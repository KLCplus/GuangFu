package com.example.pvplatform.module.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
    @NotBlank @Size(min = 2, max = 32) String roleCode,
    @NotBlank @Size(min = 1, max = 64) String roleName,
    @Size(max = 255) String description
) {}
