package com.example.pvplatform.module.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "news.sync")
public class NewsSyncProperties {
    private boolean enabled = false;
    private boolean memEnabled = true;
    private boolean neaEnabled = true;
    private boolean longiEnabled = true;
    private boolean weatherEnabled = true;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 8000;
    private int maxItemsPerSource = 10;
    private String userAgent = "GuangFu-NewsBot/1.0 (+local educational project; low frequency)";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isMemEnabled() { return memEnabled; }
    public void setMemEnabled(boolean value) { this.memEnabled = value; }
    public boolean isNeaEnabled() { return neaEnabled; }
    public void setNeaEnabled(boolean value) { this.neaEnabled = value; }
    public boolean isLongiEnabled() { return longiEnabled; }
    public void setLongiEnabled(boolean value) { this.longiEnabled = value; }
    public boolean isWeatherEnabled() { return weatherEnabled; }
    public void setWeatherEnabled(boolean value) { this.weatherEnabled = value; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { this.connectTimeoutMs = value; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int value) { this.readTimeoutMs = value; }
    public int getMaxItemsPerSource() { return maxItemsPerSource; }
    public void setMaxItemsPerSource(int value) { this.maxItemsPerSource = value; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String value) { this.userAgent = value; }
}
