package com.example.pvplatform.module.openapi.service;

import com.example.pvplatform.client.ModelServiceClient;
import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.module.openapi.dto.OpenPredictRequest;
import com.example.pvplatform.module.openapi.entity.ApiCallLog;
import com.example.pvplatform.module.openapi.vo.ApiKeyVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OpenApiService {
    private final ModelServiceClient modelServiceClient;

    public OpenApiService(ModelServiceClient modelServiceClient) {
        this.modelServiceClient = modelServiceClient;
    }

    public ApiKeyVO applyKey() {
        return new ApiKeyVO("pv_mock_key_replace_after_jwt_enabled", "ENABLE", 1000);
    }

    public List<ApiCallLog> callLogs() {
        return List.of(new ApiCallLog(1L, "lstm_v1", LocalDateTime.now().toString(), 120L, "SUCCESS"));
    }

    public ModelPredictResponse predict(OpenPredictRequest request) {
        return modelServiceClient.predict(new ModelPredictRequest(request.modelName(), request.input()));
    }
}
