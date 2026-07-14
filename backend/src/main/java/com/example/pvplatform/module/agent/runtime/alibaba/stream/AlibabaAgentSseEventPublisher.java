package com.example.pvplatform.module.agent.runtime.alibaba.stream;

import com.example.pvplatform.module.agent.runtime.alibaba.AgentRunContext;
import com.example.pvplatform.module.agent.runtime.alibaba.AlibabaAgentProperties;
import com.example.pvplatform.module.agent.runtime.alibaba.event.AgentRunEventService;
import com.example.pvplatform.module.agent.service.AgentProgressService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AlibabaAgentSseEventPublisher {
    private final AgentProgressService progress;
    private final AgentRunEventService events;
    private final AlibabaAgentProperties properties;

    public AlibabaAgentSseEventPublisher(AgentProgressService progress, AgentRunEventService events,
                                         AlibabaAgentProperties properties) {
        this.progress = progress;
        this.events = events;
        this.properties = properties;
    }

    public void publish(AgentRunContext context, SseEmitter emitter, String event, Object payload) {
        progress.send(emitter, event, payload);
        if (properties.isPersistEvents() && !"token".equals(event)) {
            events.save(context, event, nodeFor(event), toolName(payload), toolCallId(payload), status(payload), payload);
        }
    }

    public void error(AgentRunContext context, SseEmitter emitter, String code, String message, String detail) {
        java.util.Map<String, Object> payload = java.util.Map.of(
            "code", code, "message", message == null ? "Agent 执行失败" : message,
            "detail", detail == null ? "" : detail, "recoverable", true);
        publish(context, emitter, "error", payload);
    }

    @SuppressWarnings("unchecked")
    private String toolName(Object payload) {
        return payload instanceof java.util.Map<?, ?> map && map.get("toolName") != null ? String.valueOf(map.get("toolName")) : null;
    }

    private Long toolCallId(Object payload) {
        if (!(payload instanceof java.util.Map<?, ?> map) || map.get("toolCallId") == null) return null;
        try { return Long.valueOf(String.valueOf(map.get("toolCallId"))); } catch (NumberFormatException ignored) { return null; }
    }

    private String status(Object payload) {
        return payload instanceof java.util.Map<?, ?> map && map.get("status") != null ? String.valueOf(map.get("status")) : null;
    }

    private String nodeFor(String event) {
        return switch (event) {
            case "tool_call", "tool_result", "step_started", "step_completed", "approval_required" -> "tools";
            case "final", "token" -> "model";
            default -> "agent";
        };
    }
}
