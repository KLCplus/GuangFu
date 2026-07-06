package com.example.pvplatform.module.auth.oauth;

/**
 * Configuration for one OAuth provider.
 */
public record OAuthProviderConfig(
    String code,          // provider code: github, wechat, qq, mock
    boolean enabled,
    String clientId,
    String clientSecret,
    String authorizeUrl,
    String tokenUrl,
    String userInfoUrl,
    String scopes
) {
    public boolean isMock() {
        return "mock".equalsIgnoreCase(code);
    }
}
