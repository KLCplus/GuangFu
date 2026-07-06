package com.example.pvplatform.client;

import com.example.pvplatform.client.vo.ModelListResponse;
import com.example.pvplatform.client.vo.ModelServiceHealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ModelServiceHealthClient {

    private static final Logger log = LoggerFactory.getLogger(ModelServiceHealthClient.class);

    private final WebClient webClient;

    public ModelServiceHealthClient(WebClient modelServiceWebClient) {
        this.webClient = modelServiceWebClient;
    }

    public boolean isHealthy() {
        try {
            ModelServiceHealthResponse response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(ModelServiceHealthResponse.class)
                    .block();
            return response != null && "ok".equals(response.status());
        } catch (Exception e) {
            log.warn("模型服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean hasModel(String serviceModelName) {
        try {
            ModelListResponse response = webClient.get()
                    .uri("/model-api/models")
                    .retrieve()
                    .bodyToMono(ModelListResponse.class)
                    .block();
            if (response == null || response.data() == null) {
                return false;
            }
            return response.data().stream()
                    .anyMatch(m -> serviceModelName.equals(m.modelName()));
        } catch (Exception e) {
            log.warn("查询模型列表失败: {}", e.getMessage());
            return false;
        }
    }
}
