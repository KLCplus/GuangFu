package com.example.pvplatform.module.openapi.vo;

public record ApiUsageTrendVO(
    String timeBucket,
    long totalCalls,
    long successCalls,
    long failedCalls,
    long avgCostTimeMs,
    long totalTokens
) {}
