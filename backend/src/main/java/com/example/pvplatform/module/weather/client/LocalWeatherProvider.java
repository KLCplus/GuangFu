package com.example.pvplatform.module.weather.client;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Component
public class LocalWeatherProvider implements WeatherProvider {
    @Override
    public boolean supports(String provider) {
        return "LOCAL".equalsIgnoreCase(provider);
    }

    @Override
    public CurrentWeatherResult getCurrent(double longitude, double latitude) {
        double seed = Math.abs(longitude * 0.17d + latitude * 0.31d);
        return new CurrentWeatherResult("LOCAL", weatherText(seed),
            decimal(22d + seed % 9d), decimal(45d + seed % 25d),
            "SE", "2", decimal(1.5d + seed % 3d), LocalDateTime.now());
    }

    @Override
    public List<WeatherForecastResult> getForecast(double longitude, double latitude) {
        double seed = Math.abs(longitude * 0.17d + latitude * 0.31d);
        return IntStream.range(0, 3).mapToObj(offset -> {
            BigDecimal dayTemp = decimal(24d + (seed + offset) % 8d);
            return new WeatherForecastResult(LocalDate.now().plusDays(offset),
                weatherText(seed + offset), weatherText(seed + offset + 1),
                dayTemp, dayTemp.subtract(BigDecimal.valueOf(6)),
                decimal(45d + (seed + offset * 3d) % 25d), "SE", "2");
        }).toList();
    }

    @Override
    public String source() {
        return "LOCAL";
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private String weatherText(double seed) {
        int index = ((int) Math.floor(seed)) % 4;
        return switch (index) {
            case 0 -> "Sunny";
            case 1 -> "Cloudy";
            case 2 -> "Overcast";
            default -> "Light rain";
        };
    }
}
