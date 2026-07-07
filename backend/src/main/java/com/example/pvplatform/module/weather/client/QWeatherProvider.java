package com.example.pvplatform.module.weather.client;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.weather.config.WeatherProperties;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.*;

@Component
public class QWeatherProvider implements WeatherProvider {
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final WeatherProperties properties;
    private final WebClient client;
    private volatile PrivateKey privateKey;

    public QWeatherProvider(WeatherProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
            .responseTimeout(Duration.ofMillis(properties.getResponseTimeout()));
        this.client = WebClient.builder().baseUrl(properties.getBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    @Override
    public boolean supports(String provider) {
        return "QWEATHER".equalsIgnoreCase(provider);
    }

    @Override
    public CurrentWeatherResult getCurrent(double longitude, double latitude) {
        requireConfigured();
        try {
            NowResponse response = client.get().uri(builder -> builder.path("/v7/weather/now")
                    .queryParam("location", location(longitude, latitude))
                    .queryParam("lang", "zh").build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + createJwt())
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
            int days = forecastDays();
            ForecastResponse response = client.get().uri(builder -> builder.path("/v7/weather/" + days + "d")
                    .queryParam("location", location(longitude, latitude))
                    .queryParam("lang", "zh").build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + createJwt())
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
        if (!"JWT".equalsIgnoreCase(properties.getAuthType())) {
            throw new BusinessException(500, "天气服务认证方式未配置为 JWT");
        }
        if (blank(properties.getQweatherProjectId()) || blank(properties.getQweatherKeyId())
            || blank(properties.getQweatherPrivateKeyPath())) {
            throw new BusinessException(500, "天气服务 JWT 凭证未配置");
        }
    }

    private String location(double longitude, double latitude) {
        return String.format(Locale.ROOT, "%.6f,%.6f", longitude, latitude);
    }

    private int forecastDays() {
        int days = properties.getForecastDays();
        if (days != 3 && days != 7 && days != 10 && days != 15 && days != 30) {
            throw new BusinessException(500, "和风天气预报天数配置不支持");
        }
        return days;
    }

    private String createJwt() {
        try {
            long now = Instant.now().getEpochSecond();
            long ttl = properties.getJwtTtlSeconds() <= 0 ? 1800 : properties.getJwtTtlSeconds();
            String headerJson = "{\"alg\":\"EdDSA\",\"kid\":\"" + json(properties.getQweatherKeyId()) + "\"}";
            String payloadJson = "{\"sub\":\"" + json(properties.getQweatherProjectId())
                + "\",\"iat\":" + (now - 30) + ",\"exp\":" + (now + ttl) + "}";
            String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8))
                + "." + base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(loadPrivateKey());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signature.sign());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(502, "调用和风天气接口失败");
        }
    }

    private PrivateKey loadPrivateKey() {
        PrivateKey cached = privateKey;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (privateKey == null) {
                privateKey = readPrivateKey();
            }
            return privateKey;
        }
    }

    private PrivateKey readPrivateKey() {
        try {
            String pem = Files.readString(Path.of(properties.getQweatherPrivateKeyPath()), StandardCharsets.UTF_8);
            byte[] der = decodePem(pem);
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(500, "天气服务 JWT 私钥读取失败");
        }
    }

    private byte[] decodePem(String pem) {
        String normalized = pem.replace("\r", "").trim();
        if (normalized.contains("-----BEGIN PRIVATE KEY-----")) {
            String base64 = normalized
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        }
        if (normalized.contains("-----BEGIN ED25519 PRIVATE KEY-----")
            || normalized.contains("-----BEGIN OPENSSH PRIVATE KEY-----")) {
            throw new BusinessException(500, "请使用 PKCS#8 PEM 私钥，格式为 BEGIN PRIVATE KEY");
        }
        return Base64.getDecoder().decode(normalized.replaceAll("\\s", ""));
    }

    private String base64Url(byte[] bytes) {
        return BASE64_URL.encodeToString(bytes);
    }

    private String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
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
        return new BusinessException(502, "调用和风天气接口失败");
    }

    private record NowResponse(String code, Now now) {}
    private record Now(String obsTime, String temp, String icon, String text, String humidity,
                       String windDir, String windScale, String windSpeed) {}
    private record ForecastResponse(String code, List<Daily> daily) {}
    private record Daily(String fxDate, String tempMax, String tempMin, String textDay,
                         String textNight, String windDirDay, String windScaleDay,
                         String humidity) {}
}
