package com.example.pvplatform.module.analysis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AnalysisRequest(
    @NotNull @Positive Long stationId,
    @Positive Long taskId,
    @Size(max = 255) String title,
    @Size(max = 2000) String userInstruction,
    boolean includeWeather,
    boolean includePrediction
) {
    public AnalysisRequest(Long stationId, Long taskId, String title,
                           boolean includeWeather, boolean includePrediction) {
        this(stationId, taskId, title, null, includeWeather, includePrediction);
    }
}
