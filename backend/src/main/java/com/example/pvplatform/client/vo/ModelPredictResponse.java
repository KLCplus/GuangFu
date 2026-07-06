package com.example.pvplatform.client.vo;

import java.util.List;

public record ModelPredictResponse(int code, String message, Data data) {
    public record Data(String modelName, List<Prediction> predictions, long costTime) {}
    public record Prediction(int timeOffset, double predictPower) {}
}
