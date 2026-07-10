package com.example.pvplatform.module.openapi.dto;

import jakarta.validation.constraints.NotNull;

public record MarketplaceTrialRequest(
    @NotNull Long modelId
) {}
