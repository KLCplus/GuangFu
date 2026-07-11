package com.example.pvplatform.module.agent.llm;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.analysis.llm.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekAgentLlmClient implements AgentLlmClient {
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final LlmToolCallParser parser;

    public DeepSeekAgentLlmClient(LlmProperties properties, ObjectMapper objectMapper, LlmToolCallParser parser) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.parser = parser;
    }

    @Override
    public AgentLlmDecision decide(String systemPrompt, List<Map<String, Object>> messages) {
        if (!properties.isEnabled()) {
            throw new BusinessException(503, "Agent LLM 未启用");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(500, "DeepSeek API Key 未配置");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        List<Map<String, Object>> allMessages = new java.util.ArrayList<>();
        allMessages.add(Map.of("role", "system", "content", systemPrompt));
        allMessages.addAll(messages);
        body.put("messages", allMessages);
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", Math.min(properties.getMaxTokens(), 4096));
        body.put("stream", false);
        if (properties.isJsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        try {
            String response = client().post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(properties.getTimeoutMs()));
            return parser.parse(extractContent(response));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(502, "DeepSeek Agent 调用失败: " + (exception.getMessage() == null ? "未知错误" : exception.getMessage()));
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

    private String extractContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new BusinessException(502, "DeepSeek 返回空 Agent 内容");
        }
        return content.asText();
    }
}
