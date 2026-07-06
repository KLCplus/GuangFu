package com.example.pvplatform.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailCodeLoginRequest(@NotBlank @Email String email, @NotBlank String code) {}
