package com.example.pvplatform.module.model.vo;

import java.time.LocalDateTime;

public record ModelDetailVO(
    Long modelId,
    String modelCode,
    String modelName,
    String modelType,
    String modelVersion,
    Integer inputWindowMinutes,
    Integer inputFrameIntervalSeconds,
    Integer outputSteps,
    Integer outputStepMinutes,
    String serviceModelName,
    String apiPath,
    String inputSchema,
    String outputSchema,
    String status,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
