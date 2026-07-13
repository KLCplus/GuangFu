package com.example.pvplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(
    String callbackBaseUrl,
    String backendCallbackBaseUrl,
    Providers providers
) {
    public record Providers(Github github) {}
    public record Github(
        boolean enabled,
        String clientId,
        String clientSecret,
        String authorizeUrl,
        String tokenUrl,
        String userInfoUrl,
        String scopes
    ) {}
}
