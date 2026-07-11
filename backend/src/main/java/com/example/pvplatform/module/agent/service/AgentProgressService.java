package com.example.pvplatform.module.agent.service;

import com.example.pvplatform.module.agent.dto.AgentChatEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Service
public class AgentProgressService {
    public void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(new AgentChatEvent(event, data)));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE 连接已断开", exception);
        }
    }

    public void error(SseEmitter emitter, String code, String message, String detail, boolean recoverable) {
        send(emitter, "error", Map.of(
            "code", code,
            "message", message == null ? "Agent 执行失败" : message,
            "detail", detail == null ? "" : detail,
            "recoverable", recoverable
        ));
    }
}
