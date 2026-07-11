package com.example.pvplatform.module.openapi.vo;

public record ApiUsageSummaryVO(
    long totalCalls,
    long successCalls,
    long failedCalls,
    double successRate,
    long avgCostTimeMs,
    long inputTokens,
    long outputTokens,
    long totalTokens
) {}
