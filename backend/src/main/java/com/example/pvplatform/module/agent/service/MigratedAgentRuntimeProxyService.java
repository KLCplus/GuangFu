package com.example.pvplatform.module.agent.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.AgentApprovalDecision;
import com.example.pvplatform.module.agent.dto.AgentChatRequest;
import com.example.pvplatform.module.agent.entity.AgentApprovalDO;
import com.example.pvplatform.module.agent.entity.AgentMessageDO;
import com.example.pvplatform.module.agent.entity.AgentSessionDO;
import com.example.pvplatform.module.agent.entity.AgentToolCallDO;
import com.example.pvplatform.module.agent.tool.AgentTool;
import com.example.pvplatform.module.agent.tool.AgentToolRegistry;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class MigratedAgentRuntimeProxyService {
    private final AgentSessionService sessionService;
    private final AgentMessageService messageService;
    private final AgentApprovalService approvalService;
    private final AgentToolService toolService;
    private final AgentToolRegistry toolRegistry;
    private final AgentJsonService jsonService;
    private final AgentProgressService progress;
    private final ObjectMapper objectMapper;

    @Value("${agent.runtime.url:http://127.0.0.1:9101}")
    private String runtimeUrl;

    @Value("${agent.internal-token:}")
    private String internalToken;

    public MigratedAgentRuntimeProxyService(AgentSessionService sessionService,
                                            AgentMessageService messageService,
                                            AgentApprovalService approvalService,
                                            AgentToolService toolService,
                                            AgentToolRegistry toolRegistry,
                                            AgentJsonService jsonService,
                                            AgentProgressService progress,
                                            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.approvalService = approvalService;
        this.toolService = toolService;
        this.toolRegistry = toolRegistry;
        this.jsonService = jsonService;
        this.progress = progress;
        this.objectMapper = objectMapper;
    }

    public void chat(AgentChatRequest request, SseEmitter emitter) {
        try {
            if (internalToken == null || internalToken.isBlank()) {
                throw new BusinessException(500, "AGENT_INTERNAL_TOKEN 未配置，无法使用 migrated runtime");
            }
            Long userId = SecurityUtils.requireCurrentUserId();
            SecurityUser user = SecurityUtils.getCurrentUser();
            if (request.approvalId() != null) {
                continueApproval(request, emitter, userId, user);
                return;
            }
            AgentSessionDO session = sessionService.ensure(request.sessionId(), request.message());
            AgentMessageDO userMessage = messageService.save(session.getSessionId(), "user", request.message(), request.context());
            AgentApprovalDO naturalApproval = naturalApproval(session.getSessionId(), request.message());
            if (naturalApproval != null) {
                approvalService.decide(naturalApproval.getApprovalId(), new AgentApprovalDecision(isApprovalText(request.message()), "用户通过会话文本确认"));
                continueApproval(new AgentChatRequest(session.getSessionId(), request.message(), request.context(), request.mode(),
                    request.allowedTools(), request.preferredTool(), request.toolArguments(), request.requireApproval(),
                    naturalApproval.getApprovalId()), emitter, userId, user);
                return;
            }
            progress.send(emitter, "started", Map.of(
                "sessionId", session.getSessionId(),
                "messageId", userMessage.getMessageId(),
                "runtime", "migrated"
            ));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", session.getSessionId());
            payload.put("userId", userId);
            payload.put("username", user == null ? null : user.getUsername());
            payload.put("roles", roles(user));
            payload.put("message", request.message());
            Map<String, Object> runtimeContext = new LinkedHashMap<>(request.context() == null ? Map.of() : request.context());
            runtimeContext.put("conversationHistory", recentHistory(session.getSessionId(), 12));
            payload.put("context", runtimeContext);
            if (request.preferredTool() != null && !request.preferredTool().isBlank()) {
                payload.put("preferredTool", request.preferredTool());
            }
            if (request.toolArguments() != null && !request.toolArguments().isEmpty()) {
                payload.put("toolArguments", request.toolArguments());
            }
            if (request.allowedTools() != null && !request.allowedTools().isEmpty()) {
                payload.put("allowedTools", request.allowedTools());
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(runtimeUrl.replaceAll("/+$", "") + "/run/stream"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Agent-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            HttpResponse<java.util.stream.Stream<String>> response = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                throw new BusinessException(502, "migrated runtime 调用失败: HTTP " + response.statusCode());
            }
            forwardRuntimeEvents(response.body(), emitter, session.getSessionId());
            sessionService.touch(session.getSessionId());
        } catch (BusinessException exception) {
            progress.error(emitter, "MIGRATED_RUNTIME_ERROR", exception.getMessage(), exception.getMessage(), true);
        } catch (Exception exception) {
            progress.error(emitter, "MIGRATED_RUNTIME_FAILED", "migrated runtime 执行失败", exception.getMessage(), true);
        } finally {
            emitter.complete();
        }
    }

    private void continueApproval(AgentChatRequest request, SseEmitter emitter, Long userId, SecurityUser user) {
        AgentApprovalDO approval = approvalService.requireForContinuation(request.approvalId());
        AgentSessionDO session = sessionService.requireOwned(approval.getSessionId());
        AgentToolCallDO toolCall = approvalService.requireToolCall(approval);
        progress.send(emitter, "started", Map.of("sessionId", session.getSessionId(), "runtime", "migrated", "text", "继续处理确认结果"));
        if ("REJECTED".equals(approval.getStatus())) {
            String answer = "已取消执行「" + toolCall.getToolName() + "」，没有进行写入操作。";
            AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, Map.of("approvalId", approval.getApprovalId(), "status", "REJECTED"));
            progress.send(emitter, "final", finalPayload(assistant, answer, List.of()));
            return;
        }
        if (!"APPROVED".equals(approval.getStatus())) {
            progress.send(emitter, "approval_required", Map.of(
                "approvalId", approval.getApprovalId(),
                "toolCallId", toolCall.getClientToolCallId(),
                "toolName", toolCall.getToolName(),
                "reason", approval.getReason() == null ? "" : approval.getReason(),
                "arguments", jsonService.map(approval.getArgumentsJson())
            ));
            return;
        }
        AgentTool tool = toolRegistry.require(toolCall.getToolName());
        Map<String, Object> args = jsonService.map(toolCall.getArgumentsJson());
        progress.send(emitter, "tool_call", Map.of(
            "toolCallId", toolCall.getClientToolCallId(),
            "toolName", tool.name(),
            "displayName", tool.displayName(),
            "arguments", args
        ));
        ToolExecutionResult result = toolService.execute(
            toolCall,
            tool,
            new ToolExecutionContext(userId, session.getSessionId(), toolCall.getMessageId(), user),
            args
        );
        Map<String, Object> payload = resultPayload(toolCall, tool, result);
        progress.send(emitter, "tool_result", payload);
        String answer = result.success()
            ? "已按确认执行「" + tool.displayName() + "」。\n\n" + summarizeResult(result)
            : "执行「" + tool.displayName() + "」失败：" + (result.errorMessage() == null ? "" : result.errorMessage());
        AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, Map.of("approvalId", approval.getApprovalId(), "toolResult", result));
        sessionService.touch(session.getSessionId());
        progress.send(emitter, "final", finalPayload(assistant, answer, List.of(payload)));
    }

    private AgentApprovalDO naturalApproval(Long sessionId, String message) {
        String text = message == null ? "" : message.trim();
        if (!isApprovalText(text) && !isRejectionText(text)) {
            return null;
        }
        return approvalService.findLatestPending(sessionId);
    }

    private boolean isApprovalText(String message) {
        String text = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        return Set.of("确认", "确定", "同意", "执行", "可以", "继续", "approve", "yes", "ok").contains(text);
    }

    private boolean isRejectionText(String message) {
        String text = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        return Set.of("取消", "拒绝", "不同意", "不要", "停止", "reject", "no", "cancel").contains(text);
    }

    private List<Map<String, Object>> recentHistory(Long sessionId, int limit) {
        List<AgentMessageDO> rows = messageService.list(sessionId);
        int start = Math.max(0, rows.size() - limit);
        return rows.subList(start, rows.size()).stream()
            .filter(row -> row.getContent() != null && !row.getContent().isBlank())
            .map(row -> metadata(
                "role", row.getRole(),
                "content", row.getContent(),
                "messageId", row.getMessageId(),
                "createdAt", row.getCreatedAt()
            ))
            .toList();
    }

    private void forwardRuntimeEvents(java.util.stream.Stream<String> lines, SseEmitter emitter, Long sessionId) {
        final String[] eventName = {null};
        lines.forEach(line -> {
            if (line == null || line.isBlank()) {
                return;
            }
            if (line.startsWith("event:")) {
                eventName[0] = line.substring("event:".length()).trim();
                return;
            }
            if (!line.startsWith("data:")) {
                return;
            }
            String raw = line.substring("data:".length()).trim();
            try {
                Map<String, Object> envelope = objectMapper.readValue(raw, new TypeReference<>() {});
                String event = eventName[0] == null ? String.valueOf(envelope.getOrDefault("event", "message")) : eventName[0];
                Object data = envelope.get("data");
                progress.send(emitter, event, data == null ? Map.of() : data);
                saveFinalIfCompleted(event, data, sessionId);
            } catch (Exception exception) {
                progress.error(emitter, "MIGRATED_RUNTIME_EVENT_PARSE_FAILED", "runtime 事件解析失败", raw, true);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void saveFinalIfCompleted(String event, Object data, Long sessionId) {
        if (!"run_completed".equals(event) || !(data instanceof Map<?, ?> map)) {
            return;
        }
        Object answer = map.get("answer");
        if (answer != null && !String.valueOf(answer).isBlank()) {
            messageService.save(sessionId, "assistant", String.valueOf(answer), (Map<String, Object>) data);
        }
    }

    private Map<String, Object> resultPayload(AgentToolCallDO row, AgentTool tool, ToolExecutionResult result) {
        return new LinkedHashMap<>(Map.of(
            "toolCallId", row.getClientToolCallId(),
            "toolName", tool.name(),
            "displayName", result.displayName() == null || result.displayName().isBlank() ? tool.displayName() : result.displayName(),
            "status", result.success() ? "success" : "failed",
            "summary", nullToEmpty(result.summary()),
            "highlights", result.highlights() == null ? List.of() : result.highlights(),
            "error", result.errorMessage() == null ? "" : result.errorMessage(),
            "durationMs", row.getDurationMs() == null ? 0L : row.getDurationMs(),
            "dataPreview", result.data() == null ? Map.of() : result.data()
        ));
    }

    private Map<String, Object> finalPayload(AgentMessageDO assistant, String answer, List<?> toolCalls) {
        return metadata(
            "messageId", assistant.getMessageId(),
            "content", nullToEmpty(answer),
            "markdown", nullToEmpty(answer),
            "toolCalls", toolCalls == null ? List.of() : toolCalls,
            "createdAt", assistant.getCreatedAt()
        );
    }

    private String summarizeResult(ToolExecutionResult result) {
        if (result.summary() != null && !result.summary().isBlank()) {
            return result.summary();
        }
        if (result.highlights() != null && !result.highlights().isEmpty()) {
            return String.join("\n", result.highlights());
        }
        return "工具已执行完成。";
    }

    private Map<String, Object> metadata(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<String> roles(SecurityUser user) {
        if (user == null) {
            return List.of("USER");
        }
        return user.getAuthorities().stream()
            .map(Object::toString)
            .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
            .toList();
    }
}
