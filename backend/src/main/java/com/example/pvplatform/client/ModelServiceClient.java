package com.example.pvplatform.client;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ModelServiceClient {
    private final WebClient webClient;

    public ModelServiceClient(WebClient modelServiceWebClient) {
        this.webClient = modelServiceWebClient;
    }

    public ModelPredictResponse predict(ModelPredictRequest request) {
        try {
            return webClient.post()
                .uri("/model-api/predict")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ModelPredictResponse.class)
                .block();
        } catch (Exception exception) {
            throw new BusinessException(502, "模型服务调用失败，请确认 FastAPI 服务已启动");
        }
    }
}
