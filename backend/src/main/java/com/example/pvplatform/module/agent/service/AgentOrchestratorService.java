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
    private final SlashCommandParser slashCommandParser;

    public AgentOrchestratorService(AgentSessionService sessionService, AgentMessageService messageService,
                                    AgentToolService toolService, AgentApprovalService approvalService,
                                    AgentToolRegistry toolRegistry, AgentLlmClient llmClient,
                                    LlmPromptBuilder promptBuilder, AgentProgressService progress,
                                    AgentJsonService jsonService, SlashCommandParser slashCommandParser) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.toolService = toolService;
        this.approvalService = approvalService;
        this.toolRegistry = toolRegistry;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.progress = progress;
        this.jsonService = jsonService;
        this.slashCommandParser = slashCommandParser;
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
            AgentToolIntent intent = slashCommandParser.parse(request.message(), request.context(), request.preferredTool(), request.toolArguments());
            progress.send(emitter, "intent_resolved", intentPayload(intent));
            if (intent.needsQuestion()) {
                AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", intent.question(), metadata("type", "ask_user", "reason", intent.reason(), "intent", intentPayload(intent)));
                progress.send(emitter, "final", finalPayload(assistant, intent.question(), List.of()));
                sessionService.touch(session.getSessionId());
                return;
            }
            String systemPrompt = promptBuilder.build(user, request.context(), tools);
            List<Map<String, Object>> llmMessages = new ArrayList<>();
            appendHistory(session.getSessionId(), llmMessages);
            String normalizedMessage = intent.matched() && intent.normalizedMessage() != null && !intent.normalizedMessage().isBlank()
                ? intent.normalizedMessage() : (request.message() == null ? "" : request.message());
            llmMessages.add(Map.of("role", "user", "content", normalizedMessage));
            runDecisionLoop(session, userMessage, request, emitter, user, systemPrompt, llmMessages, tools, intent);
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
                                 List<Map<String, Object>> llmMessages, List<AgentTool> tools, AgentToolIntent intent) {
        List<Object> toolEvents = new ArrayList<>();
        for (int i = 0; i < MAX_TOOL_LOOPS; i++) {
            AgentLlmDecision decision;
            try {
                List<AgentToolCallSpec> initialCalls = i == 0 ? initialToolCalls(request, intent) : List.of();
                decision = !initialCalls.isEmpty()
                    ? new AgentLlmDecision("tool_call", intent.reason(), initialCalls, "", "", "", null)
                    : llmClient.decide(systemPrompt, llmMessages);
            } catch (BusinessException exception) {
                if (!toolEvents.isEmpty()) {
                    String answer = "已完成真实工具调用，但 LLM 总结不可用：" + exception.getMessage() + "\n\n" + summarizeToolEvents(toolEvents);
                    AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, metadata("type", "final", "reason", "LLM_UNAVAILABLE_AFTER_TOOLS"));
                    progress.send(emitter, "final", finalPayload(assistant, answer, toolEvents));
                    sessionService.touch(session.getSessionId());
                    return;
                }
                throw exception;
            }
            progress.send(emitter, "llm_decision", decisionPayload(decision, i == 0 && intent.matched() ? intent.source() : "llm"));
            if (decision.isAskUser()) {
                String question = decision.question() == null || decision.question().isBlank() ? "请补充必要参数。" : decision.question();
                AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", question, metadata("type", "ask_user", "reason", nullToEmpty(decision.reason())));
                progress.send(emitter, "final", finalPayload(assistant, question, toolEvents));
                sessionService.touch(session.getSessionId());
                return;
            }
            if (!decision.isToolCall() && !decision.isApprovalRequest()) {
                if (toolEvents.isEmpty() && intent.businessRelated()) {
                    AgentToolIntent forced = intent.matched() ? intent : slashCommandParser.natural(request.message(), request.context());
                    if (forced.matched() && !forced.needsQuestion()) {
                        progress.send(emitter, "final_intercepted", Map.of(
                            "reason", "业务问题 final 被拦截，强制进入工具调用",
                            "toolName", forced.toolName(),
                            "arguments", forced.arguments()
                        ));
                        decision = new AgentLlmDecision("tool_call", "业务问题必须调用工具，已由后端兜底选择工具", List.of(new AgentToolCallSpec(forced.toolName(), forced.arguments())), "", "", decision.rawOutput(), decision.parseError());
                    } else {
                        String question = forced.needsQuestion() ? forced.question() : "请补充要查询的业务对象，例如电站 ID、任务 ID 或报告 ID。";
                        AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", question, metadata("type", "ask_user", "reason", "业务问题缺少关键参数"));
                        progress.send(emitter, "final", finalPayload(assistant, question, toolEvents));
                        sessionService.touch(session.getSessionId());
                        return;
                    }
                } else {
                    String answer = decision.answer() == null || decision.answer().isBlank() ? "我没有得到可执行的结论。" : decision.answer();
                    AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", answer, metadata("type", "final", "reason", nullToEmpty(decision.reason())));
                    progress.send(emitter, "final", finalPayload(assistant, answer, toolEvents));
                    sessionService.touch(session.getSessionId());
                    return;
                }
            }
            progress.send(emitter, "plan", Map.of("steps", decision.toolCalls().stream().map(AgentToolCallSpec::toolName).toList(), "reason", nullToEmpty(decision.reason())));
            List<Map<String, Object>> loopResults = new ArrayList<>();
            for (AgentToolCallSpec call : decision.toolCalls()) {
                AgentTool tool;
                try {
                    tool = toolRegistry.require(call.toolName());
                } catch (IllegalArgumentException exception) {
                    ToolExecutionResult missing = ToolExecutionResult.failure("TOOL_NOT_AVAILABLE", exception.getMessage());
                    Map<String, Object> event = metadata("toolName", call.toolName(), "status", "failed", "summary", exception.getMessage(), "error", exception.getMessage());
                    toolEvents.add(event);
                    progress.send(emitter, "tool_result", event);
                    loopResults.add(Map.of("toolName", call.toolName(), "arguments", call.arguments() == null ? Map.of() : call.arguments(), "result", missing));
                    continue;
                }
                Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
                AgentToolCallDO row = toolService.createPending(session.getSessionId(), userMessage.getMessageId(), tool, args);
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
                if ("report.conversation".equals(tool.name()) && result.success()) {
                    String reportMarkdown = conversationReportMarkdown(result);
                    AgentMessageDO assistant = messageService.save(session.getSessionId(), "assistant", reportMarkdown, metadata("type", "conversation_report", "toolResult", result));
                    progress.send(emitter, "final", finalPayload(assistant, reportMarkdown, List.of(event)));
                    sessionService.touch(session.getSessionId());
                    return;
                }
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

    private List<AgentToolCallSpec> initialToolCalls(AgentChatRequest request, AgentToolIntent intent) {
        if (intent == null || !intent.matched()) {
            return List.of();
        }
        List<AgentToolCallSpec> comprehensive = comprehensiveAnalysisCalls(request, intent);
        if (!comprehensive.isEmpty()) {
            return comprehensive;
        }
        return List.of(new AgentToolCallSpec(intent.toolName(), intent.arguments()));
    }

    private List<AgentToolCallSpec> comprehensiveAnalysisCalls(AgentChatRequest request, AgentToolIntent intent) {
        String message = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        if (message.startsWith("/") || !(message.contains("分析") || message.contains("波动") || message.contains("原因") || message.contains("综合"))) {
            return List.of();
        }
        boolean wantsWeather = message.contains("天气") || message.contains("weather");
        boolean wantsPrediction = message.contains("预测") || message.contains("prediction") || message.contains("功率") || message.contains("发电");
        if (!wantsWeather && !wantsPrediction) {
            return List.of();
        }
        Object stationId = intent.arguments() == null ? null : intent.arguments().get("stationId");
        if (stationId == null) {
            return List.of();
        }
        Map<String, Object> stationArgs = Map.of("stationId", stationId);
        List<AgentToolCallSpec> calls = new ArrayList<>();
        calls.add(new AgentToolCallSpec("station.detail", stationArgs));
        if (wantsWeather) {
            calls.add(new AgentToolCallSpec("weather.current", stationArgs));
        }
        Object taskId = intent.arguments().get("taskId");
        if (taskId != null) {
            calls.add(new AgentToolCallSpec("prediction.detail", Map.of("taskId", taskId)));
        } else if (wantsPrediction) {
            calls.add(new AgentToolCallSpec("prediction.list", stationArgs));
        }
        return calls;
    }

    private Map<String, Object> intentPayload(AgentToolIntent intent) {
        if (intent == null) {
            return Map.of("matched", false);
        }
        return metadata(
            "matched", intent.matched(),
            "source", nullToEmpty(intent.source()),
            "toolName", nullToEmpty(intent.toolName()),
            "arguments", intent.arguments() == null ? Map.of() : intent.arguments(),
            "normalizedMessage", nullToEmpty(intent.normalizedMessage()),
            "question", nullToEmpty(intent.question()),
            "reason", nullToEmpty(intent.reason()),
            "businessRelated", intent.businessRelated()
        );
    }

    private Map<String, Object> decisionPayload(AgentLlmDecision decision, String source) {
        return metadata(
            "source", nullToEmpty(source),
            "type", decision == null ? "" : nullToEmpty(decision.type()),
            "reason", decision == null ? "" : nullToEmpty(decision.reason()),
            "toolCalls", decision == null || decision.toolCalls() == null ? List.of() : decision.toolCalls(),
            "answerPreview", decision == null ? "" : truncate(decision.answer(), 500),
            "question", decision == null ? "" : nullToEmpty(decision.question()),
            "rawOutput", decision == null ? "" : truncate(decision.rawOutput(), 2000),
            "parseError", decision == null ? "" : nullToEmpty(decision.parseError())
        );
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

    private String conversationReportMarkdown(ToolExecutionResult result) {
        if (result.data() instanceof Map<?, ?> map) {
            Object markdown = map.get("markdown");
            if (markdown != null && !String.valueOf(markdown).isBlank()) {
                return String.valueOf(markdown);
            }
        }
        return summarizeResult(result);
    }

    private String summarizeResult(ToolExecutionResult result) {
        return result.summary() == null || result.summary().isBlank() ? jsonService.json(result.data()) : result.summary();
    }

    private String summarizeToolEvents(List<Object> toolEvents) {
        return toolEvents.stream()
            .map(item -> {
                if (item instanceof Map<?, ?> map) {
                    Object nameValue = map.containsKey("displayName") ? map.get("displayName") : map.get("toolName");
                    Object summaryValue = map.containsKey("summary") ? map.get("summary") : map.get("error");
                    String name = String.valueOf(nameValue == null ? "工具" : nameValue);
                    String status = String.valueOf(map.get("status") == null ? "" : map.get("status"));
                    String summary = String.valueOf(summaryValue == null ? "" : summaryValue);
                    return "- " + name + " [" + status + "] " + summary;
                }
                return "- " + String.valueOf(item);
            })
            .toList()
            .stream()
            .reduce((a, b) -> a + "\n" + b)
            .orElse("工具已返回结果。");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }
}
