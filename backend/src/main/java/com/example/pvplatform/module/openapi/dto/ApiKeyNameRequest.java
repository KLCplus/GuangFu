package com.example.pvplatform.module.openapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyNameRequest(
    @NotBlank @Size(max = 128) String keyName
) {}
