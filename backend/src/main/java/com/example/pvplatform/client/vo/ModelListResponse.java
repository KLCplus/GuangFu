package com.example.pvplatform.client.vo;

import java.util.List;

public record ModelListResponse(int code, String message, List<ModelInfo> data) {
    public record ModelInfo(String modelName, String modelType, String description) {}
}
