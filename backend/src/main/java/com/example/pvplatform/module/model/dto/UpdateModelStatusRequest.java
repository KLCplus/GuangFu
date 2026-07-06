package com.example.pvplatform.module.model.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateModelStatusRequest(
    @NotBlank(message = "模型状态不能为空")
    String modelStatus
) {}
