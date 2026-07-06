package com.example.pvplatform.module.model.dto;

public record ModelRequest(
    String modelName, String modelCode, String modelType, String modelVersion,
    String modelStatus, String description
) {}
