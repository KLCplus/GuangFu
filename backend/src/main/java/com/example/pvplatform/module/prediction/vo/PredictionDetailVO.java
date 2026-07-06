package com.example.pvplatform.module.prediction.vo;

import java.time.LocalDateTime;

public record PredictionDetailVO(
    Long taskId,
    String taskNo,
    Long stationId,
    String stationName,
    Long modelId,
    String modelName,
    String modelCode,
    String inputMode,
    String status,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Long costTimeMs,
    String errorMessage
) {}
