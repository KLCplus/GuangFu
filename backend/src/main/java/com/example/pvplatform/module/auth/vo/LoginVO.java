package com.example.pvplatform.module.auth.vo;

import java.util.List;
import java.util.Map;

public record LoginVO(String token, long expiresIn,
                     String refreshToken, long refreshExpiresIn,
                     Map<String, Object> userInfo) {
    public static LoginVO of(String token, long expiresIn,
                             String refreshToken, long refreshExpiresIn,
                             Long userId, String username, String nickname,
                             List<String> roles) {
        return new LoginVO(token, expiresIn, refreshToken, refreshExpiresIn, Map.of(
            "userId", userId,
            "username", username,
            "nickname", nickname != null ? nickname : username,
            "roles", roles
        ));
    }
}
