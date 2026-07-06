package com.example.pvplatform.module.auth.entity;

public record AuthSession(Long userId, String token, String role) {}
