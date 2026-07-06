package com.example.pvplatform.module.openapi.dto;

import jakarta.validation.constraints.NotBlank;

public record ApiKeyStatusRequest(@NotBlank String status) {}
