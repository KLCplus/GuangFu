package com.example.pvplatform.module.news.vo;

import java.time.LocalDateTime;

public record NotificationVO(
    Long notificationId,
    String title,
    String content,
    String notificationType,
    String relatedType,
    Long relatedId,
    Integer readStatus,
    LocalDateTime readTime,
    LocalDateTime createdAt
) {}
