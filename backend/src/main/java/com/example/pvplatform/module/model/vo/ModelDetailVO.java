package com.example.pvplatform.module.model.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ModelDetailVO(
    Long modelId,
    String modelCode,
    String modelName,
    String modelType,
    String modelVersion,
    Integer inputWindowMinutes,
    Integer inputFrameIntervalSeconds,
    Integer outputSteps,
    Integer outputStepMinutes,
    String serviceModelName,
    String apiPath,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    String status,
    String description,
    String shortDescription,
    List<String> tags,
    String modelFamily,
    String provider,
    Integer releaseYear,
    String paperTitle,
    String paperUrl,
    String sourceUrl,
    List<String> capabilities,
    List<String> applicableScenarios,
    List<String> advantages,
    List<String> limitations,
    List<String> supportedInputModes,
    Map<String, Object> referenceInfo,
    Boolean marketplaceVisible,
    Boolean isFeatured,
    Integer sortOrder,
    List<ModelMetricVO> metrics,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
