package com.example.pvplatform.module.model.vo;

public record ModelListItemVO(
    Long modelId,
    String modelName,
    String modelCode,
    String modelType,
    String modelVersion,
    String status,
    String description
) {}
