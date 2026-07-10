package com.example.pvplatform.module.analysis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AnalysisRequest(
    @NotNull @Positive Long stationId,
    @Positive Long taskId,
    @Size(max = 255) String title,
    boolean includeWeather,
    boolean includePrediction
) {}
