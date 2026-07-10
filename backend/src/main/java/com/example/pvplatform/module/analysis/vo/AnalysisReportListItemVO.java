package com.example.pvplatform.module.analysis.vo;

import java.time.LocalDateTime;

public record AnalysisReportListItemVO(
    Long reportId,
    Long id,
    Long stationId,
    Long taskId,
    String title,
    String summary,
    String riskLevel,
    String status,
    String modelName,
    LocalDateTime createdAt
) {}
