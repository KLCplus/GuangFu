package com.example.pvplatform.module.prediction.vo;

import com.example.pvplatform.client.vo.ModelPredictResponse;

import java.util.List;

public record PredictionTaskVO(
    Long taskId, String taskStatus, String modelName,
    List<ModelPredictResponse.Prediction> predictions, Long costTime
) {}
