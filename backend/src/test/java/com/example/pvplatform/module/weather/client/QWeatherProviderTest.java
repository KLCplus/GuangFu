package com.example.pvplatform.module.weather.client;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.weather.config.WeatherProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QWeatherProviderTest {
    @Test
    void shouldExposeQWeatherProviderContract() {
        WeatherProperties properties = new WeatherProperties();
        properties.setAuthType("JWT");
        QWeatherProvider provider = new QWeatherProvider(properties);

        assertTrue(provider.supports("QWEATHER"));
        assertTrue(provider.supports("qweather"));
        assertFalse(provider.supports("LOCAL"));
        assertEquals("QWEATHER", provider.source());
    }

    @Test
    void shouldRequireJwtCredentials() {
        WeatherProperties properties = new WeatherProperties();
        properties.setProvider("QWEATHER");
        properties.setAuthType("JWT");
        QWeatherProvider provider = new QWeatherProvider(properties);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> provider.getCurrent(104.0668, 30.5728));
        assertEquals(500, exception.getCode());
        assertEquals("天气服务 JWT 凭证未配置", exception.getMessage());
    }
}
