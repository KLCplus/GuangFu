package com.example.pvplatform.module.weather.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.weather.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
public class PublicWeatherController {
    private final WeatherService weatherService;

    public PublicWeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public Result<?> current(@RequestParam(required = false) String location,
                             @RequestParam(required = false) Double longitude,
                             @RequestParam(required = false) Double latitude) {
        if (location != null && !location.isBlank()) {
            return Result.success(weatherService.currentByLocation(location));
        }
        requireCoordinates(longitude, latitude);
        return Result.success(weatherService.currentByCoordinates(longitude, latitude));
    }

    @GetMapping("/forecast")
    public Result<?> forecast(@RequestParam(required = false) String location,
                              @RequestParam(required = false) Double longitude,
                              @RequestParam(required = false) Double latitude) {
        if (location != null && !location.isBlank()) {
            return Result.success(weatherService.forecastByLocation(location));
        }
        requireCoordinates(longitude, latitude);
        return Result.success(weatherService.forecastByCoordinates(longitude, latitude));
    }

    private void requireCoordinates(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            throw new BusinessException(400, "请提供 location 或 longitude/latitude");
        }
    }
}
