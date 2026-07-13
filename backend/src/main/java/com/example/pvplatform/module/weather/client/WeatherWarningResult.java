package com.example.pvplatform.module.weather.client;

import java.time.LocalDateTime;

public record WeatherWarningResult(
    String id, String title, String typeName, String severity, String sender,
    String status, LocalDateTime publishedAt, LocalDateTime effectiveAt,
    LocalDateTime expiresAt, String text, String instruction
) {}
