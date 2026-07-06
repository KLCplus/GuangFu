package com.example.pvplatform.module.user.vo;

import java.util.List;

public record UserProfileVO(
    Long userId,
    String username,
    String nickname,
    String email,
    String phone,
    String avatarUrl,
    Integer gender,
    Integer status,
    List<String> roles,
    String createdAt
) {}
