package com.example.pvplatform.module.weather.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CurrentWeatherResult(
    String code,
    String weather,
    BigDecimal temperature,
    BigDecimal humidity,
    String windDirection,
    String windPower,
    BigDecimal windSpeed,
    LocalDateTime reportTime
) {
}
