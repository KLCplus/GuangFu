package com.example.pvplatform.module.openapi.vo;

import java.time.LocalDateTime;

public record ApiCallLogVO(
    Long logId,
    Long apiKeyId,
    Long modelId,
    String modelName,
    String path,
    String method,
    String requestIp,
    LocalDateTime requestTime,
    LocalDateTime responseTime,
    Long costTime,
    Long costTimeMs,
    Integer statusCode,
    String status,
    String errorMessage,
    String requestSummary,
    String responseSummary,
    Long inputTokens,
    Long outputTokens,
    Long totalTokens,
    LocalDateTime createdAt
) {}
