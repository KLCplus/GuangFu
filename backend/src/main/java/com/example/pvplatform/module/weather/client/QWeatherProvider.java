package com.example.pvplatform.module.weather.client;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.weather.config.WeatherProperties;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class QWeatherProvider implements WeatherProvider {
    private final WeatherProperties properties;
    private final WebClient client;

    public QWeatherProvider(WeatherProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
            .responseTimeout(Duration.ofMillis(properties.getResponseTimeout()));
        this.client = WebClient.builder().baseUrl(properties.getBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    @Override
    public CurrentWeatherResult getCurrent(double longitude, double latitude) {
        requireConfigured();
        try {
            NowResponse response = client.get().uri(builder -> builder.path("/v7/weather/now")
                    .queryParam("location", location(longitude, latitude))
                    .queryParam("key", properties.getApiKey()).build())
                .retrieve().bodyToMono(NowResponse.class)
                .block(Duration.ofMillis(properties.getResponseTimeout() + 500L));
            if (response == null || !"200".equals(response.code()) || response.now() == null) {
                throw unavailable();
            }
            Now now = response.now();
            return new CurrentWeatherResult(now.icon(), now.text(), decimal(now.temp()),
                decimal(now.humidity()), now.windDir(), now.windScale(),
                kmhToMs(now.windSpeed()), parseTime(now.obsTime()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public List<WeatherForecastResult> getForecast(double longitude, double latitude) {
        requireConfigured();
        try {
            ForecastResponse response = client.get().uri(builder -> builder.path("/v7/weather/3d")
                    .queryParam("location", location(longitude, latitude))
                    .queryParam("key", properties.getApiKey()).build())
                .retrieve().bodyToMono(ForecastResponse.class)
                .block(Duration.ofMillis(properties.getResponseTimeout() + 500L));
            if (response == null || !"200".equals(response.code()) || response.daily() == null) {
                throw unavailable();
            }
            return response.daily().stream().map(day -> new WeatherForecastResult(
                LocalDate.parse(day.fxDate()), day.textDay(), day.textNight(),
                decimal(day.tempMax()), decimal(day.tempMin()), decimal(day.humidity()),
                day.windDirDay(), day.windScaleDay())).toList();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public String source() {
        return "QWEATHER";
    }

    private void requireConfigured() {
        if (!"QWEATHER".equalsIgnoreCase(properties.getProvider())) {
            throw new BusinessException(500, "未支持的天气 Provider");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(500, "天气服务未配置");
        }
    }

    private String location(double longitude, double latitude) {
        return String.format(Locale.ROOT, "%.6f,%.6f", longitude, latitude);
    }

    private BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private BigDecimal kmhToMs(String value) {
        BigDecimal speed = decimal(value);
        return speed == null ? null : speed.divide(BigDecimal.valueOf(3.6), 3,
            java.math.RoundingMode.HALF_UP);
    }

    private LocalDateTime parseTime(String value) {
        return value == null ? LocalDateTime.now()
            : OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
    }

    private BusinessException unavailable() {
        return new BusinessException(502, "天气服务暂不可用");
    }

    private record NowResponse(String code, Now now) {}
    private record Now(String obsTime, String temp, String icon, String text, String humidity,
                       String windDir, String windScale, String windSpeed) {}
    private record ForecastResponse(String code, List<Daily> daily) {}
    private record Daily(String fxDate, String tempMax, String tempMin, String textDay,
                         String textNight, String windDirDay, String windScaleDay,
                         String humidity) {}
}
