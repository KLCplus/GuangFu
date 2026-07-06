package com.example.pvplatform.module.auth.oauth;

/**
 * Standardized OAuth user info extracted from provider responses.
 */
public record OAuthUserInfo(
    String openId,
    String unionId,
    String nickname,
    String avatarUrl,
    String email
) {}
