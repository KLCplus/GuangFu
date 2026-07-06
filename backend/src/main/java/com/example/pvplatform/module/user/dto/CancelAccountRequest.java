package com.example.pvplatform.module.user.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelAccountRequest(@NotBlank String password) {}
