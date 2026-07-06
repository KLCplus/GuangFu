package com.example.pvplatform.module.auth.vo;

public record LoginLogVO(
    Long logId,
    Long userId,
    String username,
    String loginType,
    String loginIp,
    String userAgent,
    String status,
    String message,
    String loginTime
) {}
