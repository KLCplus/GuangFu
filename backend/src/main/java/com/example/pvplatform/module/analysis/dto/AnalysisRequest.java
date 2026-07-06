package com.example.pvplatform.module.analysis.dto;

public record AnalysisRequest(Long stationId, Long taskId, boolean includeWeather, boolean includePrediction) {}
