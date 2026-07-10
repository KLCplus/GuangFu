package com.example.pvplatform.module.analysis.llm;

import com.example.pvplatform.module.analysis.prompt.AnalysisPromptBuilder;
import com.example.pvplatform.module.analysis.service.AnalysisContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class DeepSeekLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmClient.class);

    private final LlmProperties properties;
    private final AnalysisPromptBuilder promptBuilder;
    private final LlmResultParser parser;
    private final ObjectMapper objectMapper;

    public DeepSeekLlmClient(LlmProperties properties,
                             AnalysisPromptBuilder promptBuilder,
                             LlmResultParser parser,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmGenerationResult generateAnalysisReport(AnalysisContext context, String userInstruction) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AnalysisLlmException(500, "DeepSeek API Key 未配置");
        }
        long started = System.currentTimeMillis();
        String userPrompt = promptBuilder.buildUserPrompt(context, userInstruction);
        Map<String, Object> body = requestBody(userPrompt);
        try {
            String response = client().post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(properties.getTimeoutMs()));
            String rawContent = extractContent(response);
            ParsedAnalysisReport parsed = parser.parse(rawContent);
            long duration = System.currentTimeMillis() - started;
            log.info("DeepSeek analysis completed: model={}, durationMs={}, promptLength={}, responseLength={}",
                properties.getModel(), duration, userPrompt.length(), rawContent == null ? 0 : rawContent.length());
            return new LlmGenerationResult(parsed, properties.getModel(),
                promptBuilder.buildPromptSnapshot(context, userInstruction), rawContent, true,
                properties.getProvider(), duration);
        } catch (AnalysisLlmException e) {
            throw e;
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("DeepSeek HTTP error: model={}, statusCode={}, responseLength={}",
                properties.getModel(), status, e.getResponseBodyAsString().length());
            throw new AnalysisLlmException(mapStatus(status), statusMessage(status));
        } catch (WebClientRequestException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException) {
                log.warn("DeepSeek connection failed: model={}, message={}", properties.getModel(), e.getMessage());
                throw new AnalysisLlmException(502, "DeepSeek API 连接失败");
            }
            if (cause instanceof TimeoutException || e.getMessage().toLowerCase().contains("timeout")) {
                log.warn("DeepSeek timeout: model={}, timeoutMs={}", properties.getModel(), properties.getTimeoutMs());
                throw new AnalysisLlmException(504, "DeepSeek 调用超时");
            }
            log.warn("DeepSeek request failed: model={}, message={}", properties.getModel(), e.getMessage());
            throw new AnalysisLlmException(502, "DeepSeek API 请求失败");
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
                log.warn("DeepSeek timeout: model={}, timeoutMs={}", properties.getModel(), properties.getTimeoutMs());
                throw new AnalysisLlmException(504, "DeepSeek 调用超时");
            }
            log.warn("DeepSeek response state error: model={}, message={}", properties.getModel(), e.getMessage());
            throw new AnalysisLlmException(502, "DeepSeek API 响应异常");
        } catch (Exception e) {
            log.warn("DeepSeek analysis failed: model={}, message={}", properties.getModel(), e.getMessage());
            throw new AnalysisLlmException(502, "DeepSeek API 调用失败");
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

    private Map<String, Object> requestBody(String userPrompt) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(
            Map.of("role", "system", "content", AnalysisPromptBuilder.SYSTEM_PROMPT),
            Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("stream", false);
        if (properties.isJsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private String extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new AnalysisLlmException(502, "DeepSeek 返回空报告内容", response);
            }
            return content.asText();
        } catch (AnalysisLlmException e) {
            throw e;
        } catch (Exception e) {
            throw new AnalysisLlmException(502, "DeepSeek 响应格式异常", response);
        }
    }

    private int mapStatus(int status) {
        if (status == 401) {
            return 401;
        }
        if (status == 429) {
            return 429;
        }
        if (status >= 500) {
            return 502;
        }
        return status >= 400 && status < 500 ? 400 : 502;
    }

    private String statusMessage(int status) {
        if (status == 401) {
            return "DeepSeek API Key 错误或无效";
        }
        if (status == 429) {
            return "DeepSeek 额度不足或请求频率受限";
        }
        if (status >= 500) {
            return "DeepSeek 上游模型服务错误";
        }
        return "DeepSeek API 请求失败";
    }
}
