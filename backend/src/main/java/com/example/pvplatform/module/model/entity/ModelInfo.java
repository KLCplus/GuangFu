package com.example.pvplatform.module.model.entity;

public record ModelInfo(
    Long modelId, String modelName, String modelCode, String modelType,
    String modelVersion, String modelStatus, String description
) {}
