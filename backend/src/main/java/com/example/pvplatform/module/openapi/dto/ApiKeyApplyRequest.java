package com.example.pvplatform.module.openapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyApplyRequest(
    @NotBlank @Size(max = 128) String keyName,
    @Min(1) @Max(3650) Integer expireDays
) {}
