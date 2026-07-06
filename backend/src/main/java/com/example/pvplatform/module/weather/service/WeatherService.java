package com.example.pvplatform.module.weather.service;

import com.example.pvplatform.module.weather.entity.WeatherData;
import com.example.pvplatform.module.weather.vo.WeatherVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WeatherService {
    public WeatherData current(Long stationId) {
        return new WeatherData(stationId, "晴", 32.0, 60.0, LocalDateTime.now().toString());
    }

    public List<WeatherVO> forecast() {
        return List.of(
            new WeatherVO(LocalDate.now().toString(), "晴", "多云", 34, 26),
            new WeatherVO(LocalDate.now().plusDays(1).toString(), "多云", "小雨", 31, 24)
        );
    }
}
