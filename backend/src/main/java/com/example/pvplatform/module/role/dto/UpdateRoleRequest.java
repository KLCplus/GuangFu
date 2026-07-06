package com.example.pvplatform.module.role.dto;

import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
    @Size(min = 1, max = 64) String roleName,
    String description
) {}
