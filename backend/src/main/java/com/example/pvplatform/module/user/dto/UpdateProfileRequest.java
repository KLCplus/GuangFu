package com.example.pvplatform.module.user.dto;

public record UpdateProfileRequest(
    String nickname,
    String phone,
    String avatarUrl,
    Integer gender
) {}
