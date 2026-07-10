package com.example.pvplatform.module.analysis.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AnalysisReportVO(
    Long reportId,
    Long id,
    Long userId,
    Long stationId,
    Long taskId,
    String title,
    String summary,
    String riskLevel,
    List<AnalysisSectionVO> sections,
    List<String> suggestions,
    String markdown,
    String weatherAnalysis,
    String predictionAnalysis,
    String abnormalAnalysis,
    String suggestion,
    String reportContent,
    String reportJson,
    Boolean includeWeather,
    Boolean includePrediction,
    String modelName,
    String status,
    String errorMessage,
    String rawResponse,
    String promptSnapshot,
    String contextSnapshot,
    Boolean llmEnabled,
    String llmProvider,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
