package com.example.pvplatform.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCallbackRequest(@NotBlank String code, @NotBlank String state, String redirectUri) {}
