package com.example.pvplatform.module.weather.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeatherForecastResult(
    LocalDate date,
    String dayWeather,
    String nightWeather,
    BigDecimal dayTemp,
    BigDecimal nightTemp,
    BigDecimal humidity,
    String windDirection,
    String windPower
) {
}
