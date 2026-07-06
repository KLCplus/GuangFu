package com.example.pvplatform.module.openapi.vo;

import java.time.LocalDateTime;

public record ApiKeyVO(
    Long apiKeyId,
    String keyName,
    String apiKey,
    String apiKeyPrefix,
    String status,
    Integer rateLimitPerMinute,
    Integer dailyQuota,
    LocalDateTime expireTime,
    LocalDateTime lastUsedAt,
    LocalDateTime createdAt
) {}
