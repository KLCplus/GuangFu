package com.example.pvplatform.module.agent.runtime.alibaba.tool;

import com.example.pvplatform.module.agent.entity.AgentApprovalDO;
import com.example.pvplatform.module.agent.entity.AgentToolCallDO;
import com.example.pvplatform.module.agent.runtime.alibaba.AgentRunContext;
import com.example.pvplatform.module.agent.runtime.alibaba.stream.AlibabaAgentSseEventPublisher;
import com.example.pvplatform.module.agent.runtime.alibaba.stream.AgentGenerativeUiFactory;
import com.example.pvplatform.module.agent.service.AgentApprovalService;
import com.example.pvplatform.module.agent.service.AgentJsonService;
import com.example.pvplatform.module.agent.service.AgentProgressService;
import com.example.pvplatform.module.agent.service.AgentToolService;
import com.example.pvplatform.module.agent.tool.AgentTool;
import com.example.pvplatform.module.agent.tool.AgentToolRegistry;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.core.ParameterizedTypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpringAiToolCallbackFactory {
    private final AgentToolRegistry registry;
    private final AgentToolService toolService;
    private final AgentApprovalService approvalService;
    private final AgentJsonService json;
    private final AlibabaAgentSseEventPublisher events;
    private final AgentGenerativeUiFactory ui;
    private final ObjectMapper objectMapper;

    public SpringAiToolCallbackFactory(AgentToolRegistry registry, AgentToolService toolService,
                                       AgentApprovalService approvalService, AgentJsonService json,
                                       AlibabaAgentSseEventPublisher events, AgentGenerativeUiFactory ui, ObjectMapper objectMapper) {
        this.registry = registry; this.toolService = toolService; this.approvalService = approvalService;
        this.json = json; this.events = events; this.ui = ui; this.objectMapper = objectMapper;
    }

    public List<ToolCallback> create(AgentRunContext context, SseEmitter emitter, int maximum) {
        List<ToolCallback> callbacks = new ArrayList<>();
        // Register every enabled tool. maximum limits agent execution policy, not callback discovery;
        // truncating this list makes later tools appear in the prompt but unavailable at runtime.
        for (AgentTool tool : registry.enabledTools(context.allowedTools())) {
            String callbackName = callbackName(tool.name());
            callbacks.add(FunctionToolCallback.<Map<String, Object>, Map<String, Object>>builder(callbackName,
                    (arguments, ignored) -> invoke(tool, arguments, context, emitter))
                .description(description(tool))
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(json.json(tool.inputSchema()))
                .build());
        }
        return callbacks;
    }

    public Map<String, Object> requestWriteApproval(String toolName, Map<String, Object> arguments,
                                                     AgentRunContext context, SseEmitter emitter) {
        AgentTool tool = registry.require(toolName);
        if (!tool.requiresApproval()) {
            throw new IllegalArgumentException("仅允许通过确认兜底调用写工具: " + toolName);
        }
        return invoke(tool, arguments, context, emitter);
    }

    private Map<String, Object> invoke(AgentTool tool, Map<String, Object> arguments,
                                       AgentRunContext context, SseEmitter emitter) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        AgentToolCallDO call = toolService.createPending(context.session().getSessionId(), context.userMessage().getMessageId(), tool, safeArguments);
        Map<String, Object> callPayload = Map.of("toolCallId", call.getToolCallId(), "clientToolCallId", call.getClientToolCallId(),
            "toolName", tool.name(), "displayName", tool.displayName(), "arguments", safeArguments, "runtime", "alibaba");
        events.publish(context, emitter, "tool_call", callPayload);
        events.publish(context, emitter, "step_started", Map.of("toolCallId", call.getToolCallId(), "toolName", tool.name(), "text", "正在执行 " + tool.displayName()));
        if (tool.requiresApproval()) {
            toolService.markAwaitingApproval(call);
            AgentApprovalDO approval = approvalService.create(context.session().getSessionId(), call,
                "将执行写操作：" + tool.displayName());
            context.approvalRequested().set(true);
            Map<String, Object> result = Map.of("status", "approval_required", "approvalId", approval.getApprovalId(),
                "toolName", tool.name(), "summary", "等待用户确认后执行", "arguments", safeArguments);
            events.publish(context, emitter, "step_completed", Map.of("toolCallId", call.getToolCallId(), "toolName", tool.name(), "status", "awaiting_approval"));
            events.publish(context, emitter, "approval_required", result);
            return result;
        }
        try {
            ToolExecutionResult result = toolService.execute(call, tool, new ToolExecutionContext(
                SecurityUtils.requireCurrentUserId(), context.session().getSessionId(), context.userMessage().getMessageId(), context.user()), safeArguments);
            Map<String, Object> payload = resultPayload(call, tool, result);
            if (!result.success() && terminal(result.errorMessage())) {
                context.terminalFailure().set(true);
                payload.put("terminal", true);
            }
            events.publish(context, emitter, "tool_result", payload);
            events.publish(context, emitter, "ui_instruction", ui.instruction(tool, result));
            events.publish(context, emitter, "step_completed", Map.of("toolCallId", call.getToolCallId(), "toolName", tool.name(), "status", result.success() ? "success" : "failed"));
            return payload;
        } catch (Exception exception) {
            Map<String, Object> result = Map.of("status", "failed", "toolName", tool.name(), "error", exception.getMessage());
            if (terminal(exception.getMessage())) {
                context.terminalFailure().set(true);
                result = new LinkedHashMap<>(result);
                result.put("terminal", true);
            }
            events.publish(context, emitter, "tool_result", result);
            events.publish(context, emitter, "step_completed", Map.of("toolCallId", call.getToolCallId(), "toolName", tool.name(), "status", "failed"));
            return result;
        }
    }

    private Map<String, Object> resultPayload(AgentToolCallDO call, AgentTool tool, ToolExecutionResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", result.success() ? "success" : "failed"); value.put("toolCallId", call.getToolCallId());
        value.put("clientToolCallId", call.getClientToolCallId());
        value.put("toolName", tool.name()); value.put("summary", result.summary() == null ? "" : result.summary());
        value.put("highlights", result.highlights() == null ? List.of() : result.highlights());
        value.put("data", result.data() == null ? Map.of() : result.data());
        value.put("error", result.errorMessage() == null ? "" : result.errorMessage());
        return value;
    }

    static String callbackName(String name) { return name.replaceAll("[^a-zA-Z0-9_-]", "_"); }
    private String description(AgentTool tool) {
        return tool.description() + " 原业务工具名：" + tool.name() + "。" +
            (tool.requiresApproval() ? "这是写操作，调用后必须等待用户确认。" : "这是只读工具，可自主调用。");
    }

    private boolean terminal(String message) {
        if (message == null) return false;
        String value = message.toLowerCase();
        return value.contains("无权") || value.contains("权限") || value.contains("不存在") || value.contains("未登录");
    }
}
