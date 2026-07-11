package com.example.pvplatform.module.agent.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.AgentChatRequest;
import com.example.pvplatform.module.agent.entity.AgentApprovalDO;
import com.example.pvplatform.module.agent.entity.AgentMessageDO;
import com.example.pvplatform.module.agent.entity.AgentSessionDO;
import com.example.pvplatform.module.agent.entity.AgentToolCallDO;
import com.example.pvplatform.module.agent.llm.AgentLlmClient;
import com.example.pvplatform.module.agent.llm.AgentLlmDecision;
import com.example.pvplatform.module.agent.llm.AgentToolCallSpec;
import com.example.pvplatform.module.agent.llm.LlmPromptBuilder;
import com.example.pvplatform.module.agent.tool.AgentTool;
import com.example.pvplatform.module.agent.tool.AgentToolRegistry;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AgentOrchestratorService {
    private static final int MAX_TOOL_LOOPS = 5;

    private final AgentSessionService sessionService;
    private final AgentMessageService messageService;
    private final AgentToolService toolService;
    private final AgentApprovalService approvalService;
    private final AgentToolRegistry toolRegistry;
    private final AgentLlmClient llmClient;
    private final LlmPromptBuilder promptBuilder;
    private final AgentProgressService progress;
    private final AgentJsonService jsonService;

    public AgentOrchestratorService(AgentSessionService sessionService, AgentMessageService messageService,
                                    AgentToolService toolService, AgentApprovalService approvalService,
                                    AgentToolRegistry toolRegistry, AgentLlmClient llmClient,
                                    LlmPromptBuilder promptBuilder, AgentProgressService progress,
                                    AgentJsonService jsonService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.toolService = toolService;
        this.approvalService = approvalService;
        this.toolRegistry = toolRegistry;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.progress = progress;
        this.jsonService = jsonService;
    }

    public void chat(AgentChatRequest request, SseEmitter emitter) {
        try {
            Long userId = SecurityUtils.requireCurrentUserId();
            SecurityUser user = SecurityUtils.getCurrentUser();
            if (request.approvalId() != null) {
                continueApproval(request, emitter, userId, user);
                return;
            }
            AgentSessionDO session = sessionService.ensure(request.sessionId(), request.message());
            AgentMessageDO userMessage = messageService.save(session.getSessionId(), "user", request.message(), request.context());
            progress.send(emitter, "started", Map.of("sessionId", session.getSessionId(), "messageId", userMessage.getMessageId(), "text", "Agent 已开始处理"));
            progress.send(emitter, "thinking", Map.of("text", "正在理解任务并选择工具..."));

            List<AgentTool> tools = toolRegistry.enabledTools(request.allowedTools());
            String systemPrompt = promptBuilder.build(user, request.context(), tools);
            List<Map<String, Object>> llmMessages = new ArrayList<>();
            appendHistory(session.getSessionId(), llmMessages);
            llmMessages.add(Map.of("role", "user", "content", request.message() == null ? "" : request.message()));
            runDecisionLoop(session, userMessage, request, emitter, user, systemPrompt, llmMessages, tools);
        } catch (BusinessException exception) {
            progress.error(emitter, "AGENT_BUSINESS_ERROR", exception.getMessage(), exception.getMessage(), true);
        } catch (Exception exception) {
            progress.error(emitter, "AGENT_EXECUTION_FAILED", "Agent 执行失败", exception.getMessage(), true);
        } finally {
            emitter.complete();
        }
    }

    private void continueApproval(AgentChatRequest request, SseEmitter emitter, Long userId, SecurityUser user) {
        AgentApprovalDO approval = approvalService.requireForContinuation(request.approvalId());
        AgentSessionDO session = sessionService.requireOwned(approval.getSessionId());
        AgentToolCallDO toolCall = approvalService.requireToolCall(approval);
        progress.send(emitter, "started", Map.of("sessionId", session.getSessionId(), "text", "继续处理确认结果"));
        if ("REJECTED".equals(approval.getStatus())) {
            String answer = "已取消执行「" + toolCall.getToolName() + "」，没有进行写入操作。";
            AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, Map.of("approvalId", approval.getApprovalId(), "status", "REJECTED"));
            progress.send(emitter, "final", finalPayload(assistant, answer, List.of()));
            return;
        }
        if (!"APPROVED".equals(approval.getStatus())) {
            progress.send(emitter, "approval_required", Map.of("approvalId", approval.getApprovalId(), "toolCallId", toolCall.getClientToolCallId(), "toolName", toolCall.getToolName(), "reason", approval.getReason(), "arguments", jsonService.map(approval.getArgumentsJson())));
            return;
        }
        AgentTool tool = toolRegistry.require(toolCall.getToolName());
        Map<String, Object> args = jsonService.map(toolCall.getArgumentsJson());
        progress.send(emitter, "tool_call", Map.of("toolCallId", toolCall.getClientToolCallId(), "toolName", tool.name(), "displayName", tool.displayName(), "arguments", args));
        ToolExecutionResult result = toolService.execute(toolCall, tool, new ToolExecutionContext(userId, session.getSessionId(), toolCall.getMessageId(), user), args);
        progress.send(emitter, "tool_result", resultPayload(toolCall, tool, result));
        String answer = result.success()
            ? "已按确认执行「" + tool.displayName() + "」。\n\n" + summarizeResult(result)
            : "执行「" + tool.displayName() + "」失败：" + result.errorMessage();
        AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, Map.of("approvalId", approval.getApprovalId(), "toolResult", result));
        sessionService.touch(session.getSessionId());
        progress.send(emitter, "final", finalPayload(assistant, answer, List.of(resultPayload(toolCall, tool, result))));
    }

    private void runDecisionLoop(AgentSessionDO session, AgentMessageDO userMessage, AgentChatRequest request,
                                 SseEmitter emitter, SecurityUser user, String systemPrompt,
                                 List<Map<String, Object>> llmMessages, List<AgentTool> tools) {
        List<Object> toolEvents = new ArrayList<>();
        for (int i = 0; i < MAX_TOOL_LOOPS; i++) {
            AgentLlmDecision decision = llmClient.decide(systemPrompt, llmMessages);
            if (decision.isAskUser()) {
                String question = decision.question() == null || decision.question().isBlank() ? "请补充必要参数。" : decision.question();
                AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", question, metadata("type", "ask_user", "reason", nullToEmpty(decision.reason())));
                progress.send(emitter, "final", finalPayload(assistant, question, toolEvents));
                sessionService.touch(session.getSessionId());
                return;
            }
            if (!decision.isToolCall()) {
                String answer = decision.answer() == null || decision.answer().isBlank() ? "我没有得到可执行的结论。" : decision.answer();
                AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, metadata("type", "final", "reason", nullToEmpty(decision.reason())));
                progress.send(emitter, "final", finalPayload(assistant, answer, toolEvents));
                sessionService.touch(session.getSessionId());
                return;
            }
            progress.send(emitter, "plan", Map.of("steps", decision.toolCalls().stream().map(AgentToolCallSpec::toolName).toList(), "reason", nullToEmpty(decision.reason())));
            List<Map<String, Object>> loopResults = new ArrayList<>();
            for (AgentToolCallSpec call : decision.toolCalls()) {
                AgentTool tool = toolRegistry.require(call.toolName());
                Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
                AgentToolCallDO row = toolService.createPending(session.getSessionId(), userMessage.getMessageId(), tool.name(), args);
                if (tool.requiresApproval() && !Boolean.FALSE.equals(request.requireApproval())) {
                    toolService.markAwaitingApproval(row);
                    AgentApprovalDO approval = approvalService.create(session.getSessionId(), row,
                        "该操作会执行 " + tool.permissionLevel() + " 级别动作，需要用户确认后才会继续。");
                    Map<String, Object> event = Map.of("approvalId", approval.getApprovalId(), "toolCallId", row.getClientToolCallId(),
                        "toolName", tool.name(), "displayName", tool.displayName(), "reason", approval.getReason(), "arguments", args,
                        "permissionLevel", tool.permissionLevel().name());
                    progress.send(emitter, "approval_required", event);
                    String answer = "需要确认后才能执行「" + tool.displayName() + "」。";
                    AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, event);
                    progress.send(emitter, "final", finalPayload(assistant, answer, List.of(event)));
                    sessionService.touch(session.getSessionId());
                    return;
                }
                progress.send(emitter, "tool_call", Map.of("toolCallId", row.getClientToolCallId(), "toolName", tool.name(), "displayName", tool.displayName(), "arguments", args));
                ToolExecutionResult result = toolService.execute(row, tool, new ToolExecutionContext(user.getUserId(), session.getSessionId(), userMessage.getMessageId(), user), args);
                Map<String, Object> event = resultPayload(row, tool, result);
                toolEvents.add(event);
                progress.send(emitter, "tool_result", event);
                loopResults.add(Map.of("toolName", tool.name(), "arguments", args, "result", result));
            }
            llmMessages.add(Map.of("role", "assistant", "content", jsonService.json(decision)));
            llmMessages.add(Map.of("role", "user", "content", "工具调用结果：" + jsonService.json(loopResults) + "\n请基于这些真实结果继续判断是否需要更多工具，或给出最终回答。"));
        }
        String answer = "已达到最大工具调用轮数，已停止继续调用。请缩小问题范围后重试。";
        AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, metadata("reason", "MAX_TOOL_LOOPS"));
        progress.send(emitter, "final", finalPayload(assistant, answer, toolEvents));
    }

    private void appendHistory(Long sessionId, List<Map<String, Object>> messages) {
        List<com.example.pvplatform.module.agent.entity.AgentMessageDO> history = messageService.list(sessionId);
        int start = Math.max(0, history.size() - 8);
        for (com.example.pvplatform.module.agent.entity.AgentMessageDO item : history.subList(start, history.size())) {
            if ("user".equals(item.getRole()) || "assistant".equals(item.getRole())) {
                messages.add(Map.of("role", item.getRole(), "content", item.getContent() == null ? "" : item.getContent()));
            }
        }
    }

    private Map<String, Object> resultPayload(AgentToolCallDO row, AgentTool tool, ToolExecutionResult result) {
        return new LinkedHashMap<>(Map.of(
            "toolCallId", row.getClientToolCallId(),
            "toolName", tool.name(),
            "displayName", tool.displayName(),
            "status", result.success() ? "success" : "failed",
            "summary", nullToEmpty(result.summary()),
            "error", result.errorMessage() == null ? "" : result.errorMessage(),
            "durationMs", row.getDurationMs() == null ? 0L : row.getDurationMs(),
            "dataPreview", result.data() == null ? Map.of() : result.data()
        ));
    }

    private Map<String, Object> finalPayload(AgentMessageDO assistant, String answer, List<?> toolCalls) {
        return metadata("messageId", assistant.getMessageId(), "content", nullToEmpty(answer), "markdown", nullToEmpty(answer),
            "toolCalls", toolCalls == null ? List.of() : toolCalls, "createdAt", assistant.getCreatedAt() == null ? LocalDateTime.now() : assistant.getCreatedAt());
    }

    private Map<String, Object> metadata(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private String summarizeResult(ToolExecutionResult result) {
        return result.summary() == null || result.summary().isBlank() ? jsonService.json(result.data()) : result.summary();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
