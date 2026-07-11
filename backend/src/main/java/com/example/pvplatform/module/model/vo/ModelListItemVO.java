package com.example.pvplatform.module.model.vo;

import java.util.List;

public record ModelListItemVO(
    Long modelId,
    String modelName,
    String modelCode,
    String modelType,
    String modelVersion,
    String status,
    String description,
    String shortDescription,
    List<String> tags,
    String modelFamily,
    String provider,
    Integer releaseYear,
    Boolean marketplaceVisible,
    Boolean isFeatured,
    Integer sortOrder
) {}
