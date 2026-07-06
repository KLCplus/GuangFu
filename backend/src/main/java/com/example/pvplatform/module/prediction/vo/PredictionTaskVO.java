package com.example.pvplatform.module.prediction.vo;

import java.time.LocalDateTime;
import java.util.List;

public record PredictionTaskVO(
    Long taskId,
    String taskNo,
    String status,
    String modelName,
    String modelCode,
    Long stationId,
    String inputMode,
    LocalDateTime createdAt,
    Long costTimeMs,
    List<PredictionResultVO> predictions
) {}
