package com.example.pvplatform.module.pvoutput.client;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.pvoutput.config.PvOutputProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class PvOutputClient {
    private final WebClient pvOutputWebClient;
    private final PvOutputProperties properties;

    public PvOutputClient(WebClient pvOutputWebClient, PvOutputProperties properties) {
        this.pvOutputWebClient = pvOutputWebClient;
        this.properties = properties;
    }

    public String searchStations(String keyword, String countryCode, int seenDays) {
        ensureConfigured();
        return pvOutputWebClient.get()
            .uri(uriBuilder -> uriBuilder.path("/search.jsp")
                .queryParam("q", keyword)
                .queryParam("country_code", countryCode)
                .queryParam("seen", seenDays)
                .build())
            .headers(headers -> addAuthHeaders(headers))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(new BusinessException(response.statusCode().value(), pvOutputMessage(body, response.statusCode().value())))))
            .bodyToMono(String.class)
            .block();
    }

    public String getStatus(Long externalSystemId) {
        ensureConfigured();
        return pvOutputWebClient.get()
            .uri(uriBuilder -> uriBuilder.path("/getstatus.jsp")
                .queryParam("sid1", externalSystemId)
                .queryParam("ext", 1)
                .build())
            .headers(headers -> addAuthHeaders(headers))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(new BusinessException(response.statusCode().value(), pvOutputMessage(body, response.statusCode().value())))))
            .bodyToMono(String.class)
            .block();
    }

    private void addAuthHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set("X-Pvoutput-Apikey", properties.getApiKey());
        headers.set("X-Pvoutput-SystemId", properties.getAuthSystemId());
        headers.set("X-Rate-Limit", "1");
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new BusinessException(400, "PVOutput 接入未启用");
        }
        if (blank(properties.getApiKey()) || blank(properties.getAuthSystemId())) {
            throw new BusinessException(500, "PVOutput API Key 或认证 System ID 未配置");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String pvOutputMessage(String body, int status) {
        String text = body == null ? "" : body.trim();
        if (text.isBlank()) {
            return "PVOutput 请求失败，HTTP " + status;
        }
        return switch (text) {
            case "Invalid API Key" -> "PVOutput API Key 无效";
            case "Disabled API Key" -> "PVOutput API Key 已禁用";
            case "Invalid System ID" -> "PVOutput 认证 System ID 无效";
            case "Inaccessible System ID" -> "目标公开电站不可访问";
            case "Exceeded number requests per hour" -> "PVOutput 每小时请求次数已超限";
            case "Donation Mode" -> "PVOutput 返回 Donation Mode，当前权限无法访问该数据";
            case "No status found" -> "PVOutput 未找到该电站状态数据";
            default -> "PVOutput 请求失败：" + text;
        };
    }
}
