package com.example.pvplatform.module.weather.vo;

public record WeatherForecastVO(
    String date,
    String dayWeather,
    String nightWeather,
    Double dayTemp,
    Double nightTemp,
    Double humidity,
    String windDirection,
    String windPower,
    String source,
    boolean cached
) {
}
