package com.example.pvplatform.module.weather.client;

import java.util.List;

public interface WeatherProvider {
    boolean supports(String provider);
    CurrentWeatherResult getCurrent(double longitude, double latitude);
    List<WeatherForecastResult> getForecast(double longitude, double latitude);
    String source();
}
