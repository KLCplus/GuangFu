package com.example.pvplatform.module.agent.runtime.alibaba;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.AgentChatRequest;
import com.example.pvplatform.module.agent.entity.AgentMessageDO;
import com.example.pvplatform.module.agent.entity.AgentSessionDO;
import com.example.pvplatform.module.agent.runtime.alibaba.event.AgentRunEventService;
import com.example.pvplatform.module.agent.runtime.alibaba.skill.AgentSkill;
import com.example.pvplatform.module.agent.runtime.alibaba.skill.AgentSkillSelector;
import com.example.pvplatform.module.agent.runtime.alibaba.stream.AlibabaAgentSseEventPublisher;
import com.example.pvplatform.module.agent.service.AgentMessageService;
import com.example.pvplatform.module.agent.service.AgentOrchestratorService;
import com.example.pvplatform.module.agent.service.AgentProgressService;
import com.example.pvplatform.module.agent.service.AgentSessionService;
import com.example.pvplatform.module.agent.tool.AgentToolRegistry;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@EnableConfigurationProperties(AlibabaAgentProperties.class)
public class AlibabaAgentRuntimeService {
    private static final Pattern STATION_ID = Pattern.compile("(?:电站|station)?\\s*(\\d+)\\s*(?:号)?\\s*(?:电站)?", Pattern.CASE_INSENSITIVE);
    private final ObjectProvider<ChatModel> chatModel;
    private final AlibabaReactAgentFactory factory;
    private final AlibabaAgentProperties properties;
    private final AgentSessionService sessions;
    private final AgentMessageService messages;
    private final AgentProgressService progress;
    private final AlibabaAgentSseEventPublisher publisher;
    private final AgentOrchestratorService legacyApprovalContinuation;
    private final AgentRunEventService events;
    private final AgentSkillSelector skillSelector;
    private final AgentPromptFactory promptFactory;
    private final AgentToolRegistry toolRegistry;

    public AlibabaAgentRuntimeService(ObjectProvider<ChatModel> chatModel, AlibabaReactAgentFactory factory,
                                      AlibabaAgentProperties properties, AgentSessionService sessions,
                                      AgentMessageService messages, AgentProgressService progress, AlibabaAgentSseEventPublisher publisher,
                                      AgentOrchestratorService legacyApprovalContinuation, AgentRunEventService events,
                                      AgentSkillSelector skillSelector, AgentPromptFactory promptFactory,
                                      AgentToolRegistry toolRegistry) {
        this.chatModel = chatModel; this.factory = factory; this.properties = properties; this.sessions = sessions;
        this.messages = messages; this.progress = progress; this.publisher = publisher; this.legacyApprovalContinuation = legacyApprovalContinuation;
        this.events = events; this.skillSelector = skillSelector; this.promptFactory = promptFactory; this.toolRegistry = toolRegistry;
    }

    public String name() { return "alibaba"; }

    public void chat(AgentChatRequest request, SseEmitter emitter) {
        AgentRunContext runContext = null;
        try {
            if (!properties.isEnabled()) throw new BusinessException(503, "Alibaba Agent runtime 未启用");
            // Approval records are intentionally reused. The existing continuation owns their durable state transition.
            if (request.approvalId() != null) { legacyApprovalContinuation.chat(request, emitter); return; }
            ChatModel model = chatModel.getIfAvailable();
            if (model == null) throw new BusinessException(503, "DeepSeek ChatModel 未配置，请设置 DEEPSEEK_API_KEY");
            SecurityUser user = SecurityUtils.getCurrentUser();
            AgentSessionDO session = sessions.ensure(request.sessionId(), request.message());
            AgentMessageDO userMessage = messages.save(session.getSessionId(), "user", request.message(), request.context());
            String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
            AgentSkill skill = skillSelector.select(request.message());
            List<String> toolNames = selectTools(request, skill);
            AgentRunContext context = new AgentRunContext(runId, session, userMessage, user, toolNames,
                new AtomicBoolean(), new AtomicBoolean(), new AtomicInteger());
            runContext = context;
            Map<String, Object> started = Map.of("sessionId", session.getSessionId(), "messageId", userMessage.getMessageId(), "runId", runId, "runtime", name(), "text", "Agent 已开始处理");
            publisher.publish(context, emitter, "started", started);
            publisher.publish(context, emitter, "intent_resolved", Map.of("skill", skill.name(), "allowedTools", toolNames));
            publisher.publish(context, emitter, "plan", Map.of("summary", skill.description(), "outputSections", skill.outputSections()));
            publisher.publish(context, emitter, "thinking", Map.of("text", "正在基于真实工具分析请求...", "runtime", name()));
            ReactAgent agent = factory.create(model, context, emitter, promptFactory.build(context, skill)
                + "工具若返回 terminal=true，必须立即停止调用工具，只说明错误和已完成步骤。");
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            ExecutorService modelExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "alibaba-agent-model");
                thread.setDaemon(true);
                return thread;
            });
            AssistantMessage response;
            try {
                Future<AssistantMessage> future = modelExecutor.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    try { return agent.call(request.message() == null ? "" : request.message()); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                response = future.get(properties.getModelTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                throw new BusinessException(504, "DeepSeek 模型调用超时");
            } finally {
                modelExecutor.shutdownNow();
            }
            ensureExplicitReportApproval(request.message(), context, emitter);
            String answer = response.getText();
            if (context.approvalRequested().get()) {
                answer = "已创建待确认操作。确认后将继续执行。";
            }
            if (answer == null || answer.isBlank()) answer = "任务已完成，但模型未返回可展示的总结。";
            AgentMessageDO assistant = messages.save(session.getSessionId(), "assistant", answer,
                Map.of("runtime", name(), "runId", runId,
                    "status", context.approvalRequested().get() ? "AWAITING_APPROVAL" : "COMPLETED",
                    "completedAt", LocalDateTime.now()));
            sessions.touch(session.getSessionId());
            Map<String, Object> completed = Map.of("messageId", assistant.getMessageId(), "content", answer, "markdown", answer, "toolCalls", List.of(), "createdAt", assistant.getCreatedAt(), "runtime", name());
            if (!context.approvalRequested().get()) {
                publisher.publish(context, emitter, "final", completed);
                publisher.publish(context, emitter, "run_completed", Map.of("runId", runId, "messageId", assistant.getMessageId()));
            }
        } catch (BusinessException exception) {
            progress.error(emitter, "ALIBABA_AGENT_ERROR", exception.getMessage(), exception.getMessage(), true);
            if (runContext != null) publisher.publish(runContext, emitter, "run_completed", Map.of("status", "error", "code", "ALIBABA_AGENT_ERROR"));
        } catch (Exception exception) {
            progress.error(emitter, "ALIBABA_AGENT_FAILED", "Alibaba Agent 执行失败", exception.getMessage(), true);
            if (runContext != null) publisher.publish(runContext, emitter, "error", Map.of("status", "error", "code", "ALIBABA_AGENT_FAILED"));
        } finally { emitter.complete(); }
    }

    private List<String> selectTools(AgentChatRequest request, AgentSkill skill) {
        List<String> available = toolRegistry.enabledNames();
        if (request.allowedTools() == null || request.allowedTools().isEmpty()) return available;
        return request.allowedTools().stream().filter(available::contains).toList();
    }

    private void ensureExplicitReportApproval(String message, AgentRunContext context, SseEmitter emitter) {
        if (context.approvalRequested().get() || message == null || !message.toLowerCase().contains("报告")) return;
        Matcher matcher = STATION_ID.matcher(message);
        if (!matcher.find()) return;
        Long stationId = Long.valueOf(matcher.group(1));
        factory.requestWriteApproval("report.generate", Map.of(
            "stationId", stationId,
            "title", stationId + "号电站综合分析报告",
            "includeWeather", true,
            "includePrediction", true), context, emitter);
    }

}
