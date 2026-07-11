package com.example.pvplatform.module.openapi.vo;

public record ApiUsageByModelVO(
    Long modelId,
    String modelName,
    long totalCalls,
    long successCalls,
    long failedCalls,
    long avgCostTimeMs,
    long totalTokens
) {}
