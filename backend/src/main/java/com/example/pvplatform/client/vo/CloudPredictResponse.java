package com.example.pvplatform.client.vo;

import java.util.List;

public record CloudPredictResponse(
    int code,
    String message,
    Data data
) {
    public record Data(
        String modelName,
        List<Prediction> predictions,
        Integer costTime
    ) {}

    public record Prediction(
        Integer frameIndex,
        String image,
        Double cloudCoverage,
        Double confidence
    ) {}
}
