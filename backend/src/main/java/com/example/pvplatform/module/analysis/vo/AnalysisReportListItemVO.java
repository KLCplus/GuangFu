package com.example.pvplatform.module.analysis.vo;

import java.time.LocalDateTime;

public record AnalysisReportListItemVO(
    Long reportId, Long stationId, Long taskId, String title, String summary, LocalDateTime createdAt
) {}
