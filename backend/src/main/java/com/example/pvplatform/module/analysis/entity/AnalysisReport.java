package com.example.pvplatform.module.analysis.entity;

public record AnalysisReport(
    String stationName, String reportTime, String summary,
    String weatherAnalysis, String predictionAnalysis, String suggestion
) {}
