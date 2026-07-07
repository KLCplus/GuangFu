package com.example.pvplatform.module.prediction.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record PredictionTaskVO(
    Long taskId,
    String taskNo,
    @JsonProperty("taskStatus")
    String status,
    String modelName,
    String modelCode,
    Long stationId,
    String inputMode,
    LocalDateTime createdAt,
    @JsonProperty("costTime")
    Long costTimeMs,
    List<PredictionResultVO> predictions
) {}
