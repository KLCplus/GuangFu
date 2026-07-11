package com.example.pvplatform.module.prediction.dto;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PredictionRequest(
    @NotNull Long stationId, @NotNull Long modelId, String inputMode,
    String inputStartTime, String inputEndTime,
    @Size(min = 30, max = 30) List<NumericValue> numericValues,
    @Size(min = 30, max = 30) List<ModelPredictRequest.ImageFrame> inputImages
) {
    public record NumericValue(String time, double value) {}
}
