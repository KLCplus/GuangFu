package com.example.pvplatform.module.model.vo;

import java.util.List;

/** Public, display-only model information exposed to the local miniapp demo. */
public record MiniappModelVO(
    Long modelId,
    String modelCode,
    String modelName,
    String modelType,
    String modelVersion,
    String status,
    String shortDescription,
    List<String> tags,
    String modelFamily,
    String provider,
    Integer releaseYear,
    List<String> capabilities,
    List<String> applicableScenarios,
    List<String> advantages,
    List<String> limitations
) {}
