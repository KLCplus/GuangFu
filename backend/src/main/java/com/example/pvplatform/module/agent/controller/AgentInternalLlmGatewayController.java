package com.example.pvplatform.module.agent.controller;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.analysis.llm.LlmProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/llm/v1")
public class AgentInternalLlmGatewayController {
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${agent.internal-token:}")
    private String internalToken;

    public AgentInternalLlmGatewayController(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat/completions")
    public Map<String, Object> chatCompletions(@RequestHeader(value = "X-Agent-Internal-Token", required = false) String token,
                                               @RequestBody Map<String, Object> request) {
        requireInternalToken(token);
        if (!properties.isEnabled()) {
            throw new BusinessException(503, "Agent LLM 未启用");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(500, "DeepSeek API Key 未配置");
        }
        Map<String, Object> body = new LinkedHashMap<>(request == null ? Map.of() : request);
        body.put("model", properties.getModel());
        body.putIfAbsent("temperature", properties.getTemperature());
        body.putIfAbsent("max_tokens", Math.min(properties.getMaxTokens(), 4096));
        body.put("stream", false);
        try {
            String response = client().post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(properties.getTimeoutMs()));
            return objectMapper.readValue(response, new TypeReference<>() {});
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(502, "DeepSeek 内部网关调用失败");
        }
    }

    private WebClient client() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.min(properties.getTimeoutMs(), 10000))
            .responseTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return WebClient.builder()
            .baseUrl(properties.getBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    private void requireInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BusinessException(500, "内部 Agent Gateway 未配置 token");
        }
        if (token == null || !internalToken.equals(token)) {
            throw new BusinessException(401, "内部 Agent Gateway token 无效");
        }
    }
}

