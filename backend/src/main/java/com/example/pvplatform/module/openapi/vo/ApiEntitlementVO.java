package com.example.pvplatform.module.openapi.vo;

import java.time.LocalDateTime;

public record ApiEntitlementVO(
    Long entitlementId,
    Long modelId,
    String modelName,
    String apiKeyName,
    Integer quotaTotal,
    Long quotaUsed,
    LocalDateTime expireTime,
    String status
) {}
