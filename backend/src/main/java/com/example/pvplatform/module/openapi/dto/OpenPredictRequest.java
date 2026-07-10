package com.example.pvplatform.module.openapi.dto;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OpenPredictRequest(
    Long stationId,
    @NotBlank String modelName,
    @NotNull @Size(min = 30, max = 30) List<ModelPredictRequest.InputFrame> input
) {}
