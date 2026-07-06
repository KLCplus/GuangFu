package com.example.pvplatform.module.weather.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.weather.service.WeatherService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stations/{stationId}/weather")
public class WeatherController {
    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public Result<?> current(@PathVariable Long stationId) {
        return Result.success(weatherService.current(stationId));
    }

    @GetMapping("/forecast")
    public Result<?> forecast(@PathVariable Long stationId) {
        return Result.success(weatherService.forecast(stationId));
    }
}
