package com.example.pvplatform.module.openapi.vo;

import com.example.pvplatform.client.vo.ModelPredictResponse;

import java.util.List;

public record OpenPredictVO(
    Long taskId, String taskNo, String status, String modelName,
    List<ModelPredictResponse.Prediction> predictions, long costTime
) {}
