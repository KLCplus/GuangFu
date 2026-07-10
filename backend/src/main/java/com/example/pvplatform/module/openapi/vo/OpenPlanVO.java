package com.example.pvplatform.module.openapi.vo;

import java.math.BigDecimal;

public record OpenPlanVO(
    String planCode,
    String planName,
    Integer quota,
    BigDecimal price,
    Integer validDays,
    String description
) {}
