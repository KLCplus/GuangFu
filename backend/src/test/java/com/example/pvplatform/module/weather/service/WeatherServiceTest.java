package com.example.pvplatform.module.weather.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.module.weather.client.WeatherProvider;
import com.example.pvplatform.module.weather.config.WeatherProperties;
import com.example.pvplatform.persistence.entity.*;
import com.example.pvplatform.persistence.mapper.WeatherDataMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WeatherServiceTest {
    @Test
    void shouldUseValidCurrentCacheWithoutCallingProvider() {
        WeatherDataMapper mapper = mock(WeatherDataMapper.class);
        StationPermissionService permissions = mock(StationPermissionService.class);
        WeatherProvider provider = mock(WeatherProvider.class);
        PowerStationDO station = new PowerStationDO();
        station.setLongitude(BigDecimal.valueOf(104));
        station.setLatitude(BigDecimal.valueOf(30));
        when(permissions.requireView(1L)).thenReturn(station);
        WeatherDataDO cached = new WeatherDataDO();
        cached.setStationId(1L);
        cached.setWeatherText("晴");
        cached.setWeatherTime(LocalDateTime.now());
        cached.setCreatedAt(LocalDateTime.now());
        cached.setSource("QWEATHER");
        when(mapper.selectOne(any())).thenReturn(cached);
        WeatherProperties properties = new WeatherProperties();
        WeatherService service = new WeatherService(mapper, permissions, provider,
            properties, new ObjectMapper().findAndRegisterModules());

        assertTrue(service.current(1L).cached());
        verifyNoInteractions(provider);
    }
}
