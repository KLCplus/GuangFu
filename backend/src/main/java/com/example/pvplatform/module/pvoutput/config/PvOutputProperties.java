package com.example.pvplatform.module.pvoutput.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pvoutput")
public class PvOutputProperties {
    private boolean enabled = true;
    private String apiKey;
    private String authSystemId;
    private String baseUrl = "https://pvoutput.org/service/r2";
    private long syncIntervalMs = 600000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getAuthSystemId() { return authSystemId; }
    public void setAuthSystemId(String authSystemId) { this.authSystemId = authSystemId; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public long getSyncIntervalMs() { return syncIntervalMs; }
    public void setSyncIntervalMs(long syncIntervalMs) { this.syncIntervalMs = syncIntervalMs; }
}
