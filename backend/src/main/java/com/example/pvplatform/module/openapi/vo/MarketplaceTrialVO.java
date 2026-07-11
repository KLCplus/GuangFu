package com.example.pvplatform.module.openapi.vo;

import java.time.LocalDateTime;

public record MarketplaceTrialVO(
    Long trialId,
    Long modelId,
    String modelName,
    LocalDateTime expireTime,
    Integer quota
) {}
