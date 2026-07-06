package com.example.pvplatform.module.prediction.entity;

import com.example.pvplatform.client.vo.ModelPredictResponse;

import java.time.LocalDateTime;
import java.util.List;

public record PredictionTask(
    Long taskId, Long stationId, Long modelId, String modelName, TaskStatus taskStatus,
    LocalDateTime createTime, List<ModelPredictResponse.Prediction> predictions, Long costTime
) {
    public enum TaskStatus { PENDING, RUNNING, SUCCESS, FAILED }
}
