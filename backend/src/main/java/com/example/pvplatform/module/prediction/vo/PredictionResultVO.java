package com.example.pvplatform.module.prediction.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PredictionResultVO(
    Integer timeOffsetMinutes,
    LocalDateTime predictTime,
    BigDecimal predictPowerKw,
    BigDecimal actualPowerKw,
    BigDecimal errorValue,
    BigDecimal errorRate
) {}
