package com.example.pvplatform.module.analysis.vo;

import java.time.LocalDateTime;

public record AnalysisReportVO(
    Long reportId,
    Long userId,
    Long stationId,
    Long taskId,
    String title,
    String summary,
    String weatherAnalysis,
    String predictionAnalysis,
    String abnormalAnalysis,
    String suggestion,
    String reportContent,
    String reportJson,
    LocalDateTime createdAt
) {}
