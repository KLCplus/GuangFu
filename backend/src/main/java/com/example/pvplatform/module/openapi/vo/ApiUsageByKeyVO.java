package com.example.pvplatform.module.openapi.vo;

public record ApiUsageByKeyVO(
    Long apiKeyId,
    String keyName,
    String apiKeyPrefix,
    long totalCalls,
    long successCalls,
    long failedCalls,
    long avgCostTimeMs,
    long totalTokens
) {}
