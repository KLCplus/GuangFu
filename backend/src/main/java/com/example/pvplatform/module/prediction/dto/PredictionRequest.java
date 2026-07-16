package com.example.pvplatform.module.prediction.dto;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PredictionRequest(
    @NotNull Long stationId, @NotNull Long modelId, Long apiKeyId, String inputMode,
    String inputStartTime, String inputEndTime,
    List<NumericValue> numericValues,
    List<ModelPredictRequest.ImageFrame> inputImages
) {
    public record NumericValue(String time, double value) {}
}
