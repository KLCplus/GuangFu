package com.example.pvplatform.module.auth.vo;

public record RefreshVO(String token, long expiresIn,
                        String refreshToken, long refreshExpiresIn) {
}
