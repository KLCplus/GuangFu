package com.example.pvplatform.module.auth.vo;

public record OAuthAccountVO(
    Long oauthId,
    String provider,
    String openId,
    String nickname,
    String avatarUrl,
    String email,
    String createdAt
) {}
