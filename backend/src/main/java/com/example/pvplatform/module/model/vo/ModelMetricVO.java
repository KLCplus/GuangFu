package com.example.pvplatform.module.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record ModelMetricVO(
    Long metricId,
    String datasetName,
    BigDecimal mae,
    BigDecimal rmse,
    BigDecimal mape,
    BigDecimal r2Score,
    Map<String, Object> metricJson,
    LocalDateTime evaluatedAt,
    LocalDateTime createdAt
) {}
