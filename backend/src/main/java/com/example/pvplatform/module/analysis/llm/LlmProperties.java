package com.example.pvplatform.module.analysis.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.llm")
public class LlmProperties {
    private boolean enabled = true;
    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com";
    // DeepSeek API Key can only come from environment variables. Never hard-code or log it.
    private String apiKey = "";
    private String model = "deepseek-v4-flash";
    private double temperature = 0.2d;
    private int maxTokens = 4096;
    private int timeoutMs = 60000;
    private boolean jsonMode = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isJsonMode() {
        return jsonMode;
    }

    public void setJsonMode(boolean jsonMode) {
        this.jsonMode = jsonMode;
    }
}
