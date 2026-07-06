package com.example.pvplatform.module.user.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateUserRolesRequest(
    @NotEmpty List<String> roles
) {}
