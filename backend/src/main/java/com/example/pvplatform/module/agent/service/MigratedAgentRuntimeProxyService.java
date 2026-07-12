package com.example.pvplatform.module.agent.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.AgentChatRequest;
import com.example.pvplatform.module.agent.entity.AgentMessageDO;
import com.example.pvplatform.module.agent.entity.AgentSessionDO;
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
    private final AgentProgressService progress;
    private final ObjectMapper objectMapper;

    @Value("${agent.runtime.url:http://127.0.0.1:9101}")
    private String runtimeUrl;

    @Value("${agent.internal-token:}")
    private String internalToken;

    public MigratedAgentRuntimeProxyService(AgentSessionService sessionService,
                                            AgentMessageService messageService,
                                            AgentProgressService progress,
                                            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.messageService = messageService;
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
            AgentSessionDO session = sessionService.ensure(request.sessionId(), request.message());
            AgentMessageDO userMessage = messageService.save(session.getSessionId(), "user", request.message(), request.context());
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
            payload.put("context", request.context() == null ? Map.of() : request.context());

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

