package com.example.pvplatform.module.auth.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.auth.dto.LoginRequest;
import com.example.pvplatform.module.auth.dto.RegisterRequest;
import com.example.pvplatform.module.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}
