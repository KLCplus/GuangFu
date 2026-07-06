package com.example.pvplatform.module.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateProfileRequest(
    String nickname,
    @Email String email,
    String phone,
    String avatarUrl,
    Integer gender
) {}
