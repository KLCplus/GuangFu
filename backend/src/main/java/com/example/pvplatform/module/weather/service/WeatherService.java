package com.example.pvplatform.module.weather.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.module.weather.client.*;
import com.example.pvplatform.module.weather.config.WeatherProperties;
import com.example.pvplatform.module.weather.vo.*;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.WeatherDataDO;
import com.example.pvplatform.persistence.mapper.WeatherDataMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class WeatherService {
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final WeatherDataMapper weatherMapper;
    private final StationPermissionService permissionService;
    private final List<WeatherProvider> providers;
    private final WeatherProperties properties;
    private final ObjectMapper objectMapper;

    public WeatherService(WeatherDataMapper weatherMapper,
                          StationPermissionService permissionService,
                          List<WeatherProvider> providers,
                          WeatherProperties properties,
                          ObjectMapper objectMapper) {
        this.weatherMapper = weatherMapper;
        this.permissionService = permissionService;
        this.providers = providers;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public CurrentWeatherVO current(Long stationId) {
        PowerStationDO station = requireCoordinates(stationId);
        WeatherProvider provider = activeProvider();
        WeatherDataDO cached = latestCurrent(stationId, provider.source());
        if (cached != null && cached.getCreatedAt() != null
            && !cached.getCreatedAt().isBefore(LocalDateTime.now()
                .minusMinutes(properties.getCacheMinutes()))) {
            return currentVO(cached, true);
        }
        try {
            CurrentWeatherResult result = provider.getCurrent(
                station.getLongitude().doubleValue(), station.getLatitude().doubleValue());
            WeatherDataDO saved = currentDO(stationId, result, provider);
            upsert(saved);
            return currentVO(saved, false);
        } catch (BusinessException exception) {
            if (cached != null && exception.getCode() == 502) {
                return currentVO(cached, true);
            }
            throw exception;
        }
    }

    public List<WeatherForecastVO> forecast(Long stationId) {
        PowerStationDO station = requireCoordinates(stationId);
        WeatherProvider provider = activeProvider();
        List<WeatherDataDO> cached = forecastRows(stationId, provider.source());
        if (!cached.isEmpty() && cached.stream().allMatch(row -> row.getCreatedAt() != null
            && !row.getCreatedAt().isBefore(LocalDateTime.now()
            .minusMinutes(properties.getCacheMinutes())))) {
            return forecastVOs(cached, true);
        }
        try {
            List<WeatherForecastResult> results = provider.getForecast(
                station.getLongitude().doubleValue(), station.getLatitude().doubleValue());
            List<WeatherDataDO> saved = results.stream()
                .limit(Math.max(1, properties.getForecastDays()))
                .map(result -> forecastDO(stationId, result, provider)).toList();
            saved.forEach(this::upsert);
            return forecastVOs(saved, false);
        } catch (BusinessException exception) {
            if (!cached.isEmpty() && exception.getCode() == 502) {
                return forecastVOs(cached, true);
            }
            throw exception;
        }
    }

    private PowerStationDO requireCoordinates(Long stationId) {
        PowerStationDO station = permissionService.requireView(stationId);
        if (station.getLongitude() == null || station.getLatitude() == null) {
            throw new BusinessException(400, "电站未配置经纬度，无法获取天气");
        }
        return station;
    }

    private WeatherProvider activeProvider() {
        return providers.stream()
            .filter(provider -> provider.supports(properties.getProvider()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(500, "未支持的天气 Provider: " + properties.getProvider()));
    }

    private WeatherDataDO latestCurrent(Long stationId, String source) {
        return weatherMapper.selectOne(Wrappers.<WeatherDataDO>lambdaQuery()
            .eq(WeatherDataDO::getStationId, stationId)
            .eq(WeatherDataDO::getSource, source)
            .likeRight(WeatherDataDO::getWeatherCode, "CURRENT:")
            .orderByDesc(WeatherDataDO::getCreatedAt).last("LIMIT 1"));
    }

    private List<WeatherDataDO> forecastRows(Long stationId, String source) {
        return weatherMapper.selectList(Wrappers.<WeatherDataDO>lambdaQuery()
            .eq(WeatherDataDO::getStationId, stationId)
            .eq(WeatherDataDO::getSource, source)
            .likeRight(WeatherDataDO::getWeatherCode, "FORECAST:")
            .ge(WeatherDataDO::getWeatherTime, LocalDate.now().atStartOfDay())
            .orderByAsc(WeatherDataDO::getWeatherTime));
    }

    private WeatherDataDO currentDO(Long stationId, CurrentWeatherResult result,
                                    WeatherProvider provider) {
        WeatherDataDO row = new WeatherDataDO();
        row.setStationId(stationId);
        row.setWeatherTime(result.reportTime());
        row.setWeatherCode("CURRENT:" + Objects.toString(result.code(), ""));
        row.setWeatherText(result.weather());
        row.setTemperatureC(result.temperature());
        row.setHumidityPercent(result.humidity());
        row.setWindDirection(result.windDirection());
        row.setWindPower(result.windPower());
        row.setWindSpeedMS(result.windSpeed());
        row.setSource(provider.source());
        row.setRawData(json(result));
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private WeatherDataDO forecastDO(Long stationId, WeatherForecastResult result,
                                     WeatherProvider provider) {
        WeatherDataDO row = new WeatherDataDO();
        row.setStationId(stationId);
        row.setWeatherTime(result.date().atTime(12, 0));
        row.setWeatherCode("FORECAST:DAY");
        row.setWeatherText(result.dayWeather());
        row.setTemperatureC(result.dayTemp());
        row.setHumidityPercent(result.humidity());
        row.setWindDirection(result.windDirection());
        row.setWindPower(result.windPower());
        row.setSource(provider.source());
        row.setRawData(json(result));
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private void upsert(WeatherDataDO row) {
        WeatherDataDO existing = weatherMapper.selectOne(Wrappers.<WeatherDataDO>lambdaQuery()
            .eq(WeatherDataDO::getStationId, row.getStationId())
            .eq(WeatherDataDO::getWeatherTime, row.getWeatherTime())
            .eq(WeatherDataDO::getSource, row.getSource()).last("LIMIT 1"));
        if (existing == null) {
            weatherMapper.insert(row);
        } else {
            row.setWeatherId(existing.getWeatherId());
            weatherMapper.updateById(row);
        }
    }

    private CurrentWeatherVO currentVO(WeatherDataDO row, boolean cached) {
        return new CurrentWeatherVO(row.getStationId(), row.getWeatherText(),
            number(row.getTemperatureC()), number(row.getHumidityPercent()),
            row.getWindDirection(), row.getWindPower(), number(row.getWindSpeedMS()),
            row.getWeatherTime().format(TIME_FORMAT), row.getSource(), cached);
    }

    private List<WeatherForecastVO> forecastVOs(List<WeatherDataDO> rows, boolean cached) {
        return rows.stream().map(row -> {
            try {
                WeatherForecastResult result = objectMapper.readValue(
                    row.getRawData(), WeatherForecastResult.class);
                return new WeatherForecastVO(result.date().toString(), result.dayWeather(),
                    result.nightWeather(), number(result.dayTemp()), number(result.nightTemp()),
                    number(result.humidity()), result.windDirection(), result.windPower(),
                    row.getSource(), cached);
            } catch (Exception exception) {
                throw new BusinessException(500, "天气缓存数据格式错误");
            }
        }).toList();
    }

    private String json(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() <= 8000 ? json : json.substring(0, 8000);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "天气数据序列化失败");
        }
    }

    private Double number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
