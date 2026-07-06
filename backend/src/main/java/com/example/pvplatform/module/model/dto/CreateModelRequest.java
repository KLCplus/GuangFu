package com.example.pvplatform.module.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateModelRequest(
    @NotBlank(message = "模型编码不能为空")
    String modelCode,

    @NotBlank(message = "模型名称不能为空")
    String modelName,

    @NotBlank(message = "模型类型不能为空")
    String modelType,

    String modelVersion,

    @Positive(message = "输入窗口必须为正数")
    Integer inputWindowMinutes,

    @Positive(message = "输入帧间隔必须为正数")
    Integer inputFrameIntervalSeconds,

    @Positive(message = "输出步数必须为正数")
    Integer outputSteps,

    @Positive(message = "输出步长必须为正数")
    Integer outputStepMinutes,

    @NotBlank(message = "服务模型名称不能为空")
    String serviceModelName,

    String apiPath,

    String inputSchema,

    String outputSchema,

    String description
) {}
