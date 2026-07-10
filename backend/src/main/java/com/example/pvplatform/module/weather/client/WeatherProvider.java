package com.example.pvplatform.module.weather.client;

import com.example.pvplatform.common.exception.BusinessException;
import java.util.List;

public interface WeatherProvider {
    boolean supports(String provider);
    CurrentWeatherResult getCurrent(double longitude, double latitude);
    List<WeatherForecastResult> getForecast(double longitude, double latitude);
    String source();

    default WeatherLocation resolveLocation(String location) {
        throw new BusinessException(400, "当前天气 Provider 不支持地点名称查询");
    }
}
