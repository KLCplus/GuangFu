package com.example.pvplatform.module.prediction.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PredictionResultVO(
    @JsonProperty("timeOffset")
    Integer timeOffsetMinutes,
    LocalDateTime predictTime,
    @JsonProperty("predictPower")
    BigDecimal predictPowerKw,
    BigDecimal actualPowerKw,
    BigDecimal errorValue,
    BigDecimal errorRate
) {}
