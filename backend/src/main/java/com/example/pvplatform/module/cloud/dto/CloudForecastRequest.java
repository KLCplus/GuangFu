package com.example.pvplatform.module.cloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CloudForecastRequest(
    @NotBlank String modelName,
    @NotEmpty @Size(min = 10, max = 10) List<String> inputImages
) {}
