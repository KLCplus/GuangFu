package com.example.pvplatform.module.auth.service;

import com.example.pvplatform.module.auth.dto.LoginRequest;
import com.example.pvplatform.module.auth.dto.RegisterRequest;
import com.example.pvplatform.module.auth.vo.LoginVO;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {
    public Map<String, Object> register(RegisterRequest request) {
        return Map.of("userId", 1L, "username", request.username());
    }

    public LoginVO login(LoginRequest request) {
        return new LoginVO(
            "mock-jwt-token",
            Map.of("userId", 1L, "username", request.username(), "role", "USER")
        );
    }
}
