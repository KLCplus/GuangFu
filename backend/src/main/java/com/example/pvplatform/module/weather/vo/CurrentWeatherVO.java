package com.example.pvplatform.module.weather.vo;

public record CurrentWeatherVO(
    Long stationId,
    String weather,
    Double temperature,
    Double humidity,
    String windDirection,
    String windPower,
    Double windSpeed,
    String reportTime,
    String source,
    boolean cached
) {
}
