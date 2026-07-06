package com.example.pvplatform.module.model.dto;

import jakarta.validation.constraints.Positive;

public record UpdateModelRequest(
    String modelName,

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

    String serviceModelName,

    String apiPath,

    String inputSchema,

    String outputSchema,

    String description
) {}
