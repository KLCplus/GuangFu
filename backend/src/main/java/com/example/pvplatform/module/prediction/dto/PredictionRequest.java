package com.example.pvplatform.module.prediction.dto;

import jakarta.validation.constraints.NotNull;

public record PredictionRequest(
    @NotNull Long stationId, @NotNull Long modelId, String inputMode,
    String inputStartTime, String inputEndTime
) {}
