package com.example.pvplatform.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendEmailChangeCodeRequest(
    @NotBlank(message = "请输入新邮箱")
    @Email(message = "新邮箱格式不正确") String newEmail,
    @NotBlank(message = "请输入当前密码") String currentPassword
) {}
