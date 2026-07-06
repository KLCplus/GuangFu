package com.example.pvplatform.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailCodeRegisterRequest(
    @NotBlank @Size(min = 4, max = 32) String username,
    @NotBlank @Email String email,
    @NotBlank String code
) {}
