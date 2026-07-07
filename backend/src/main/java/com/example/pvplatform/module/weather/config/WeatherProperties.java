package com.example.pvplatform.module.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "weather")
public class WeatherProperties {
    private String provider = "QWEATHER";
    private String authType = "API_KEY";
    private String apiKey;
    private String baseUrl = "https://devapi.qweather.com";
    private int connectTimeout = 3000;
    private int responseTimeout = 5000;
    private int cacheMinutes = 10;
    private int forecastDays = 3;
    private String qweatherProjectId;
    private String qweatherKeyId;
    private String qweatherPrivateKeyPath;
    private long jwtTtlSeconds = 1800;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
    public int getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(int responseTimeout) { this.responseTimeout = responseTimeout; }
    public int getCacheMinutes() { return cacheMinutes; }
    public void setCacheMinutes(int cacheMinutes) { this.cacheMinutes = cacheMinutes; }
    public int getForecastDays() { return forecastDays; }
    public void setForecastDays(int forecastDays) { this.forecastDays = forecastDays; }
    public String getQweatherProjectId() { return qweatherProjectId; }
    public void setQweatherProjectId(String qweatherProjectId) { this.qweatherProjectId = qweatherProjectId; }
    public String getQweatherKeyId() { return qweatherKeyId; }
    public void setQweatherKeyId(String qweatherKeyId) { this.qweatherKeyId = qweatherKeyId; }
    public String getQweatherPrivateKeyPath() { return qweatherPrivateKeyPath; }
    public void setQweatherPrivateKeyPath(String qweatherPrivateKeyPath) { this.qweatherPrivateKeyPath = qweatherPrivateKeyPath; }
    public long getJwtTtlSeconds() { return jwtTtlSeconds; }
    public void setJwtTtlSeconds(long jwtTtlSeconds) { this.jwtTtlSeconds = jwtTtlSeconds; }
}
