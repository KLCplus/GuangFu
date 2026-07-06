package com.example.pvplatform.module.news.vo;

import java.time.LocalDateTime;

public record NewsVO(
    Long newsId,
    String title,
    String summary,
    String content,
    String coverUrl,
    String newsType,
    String targetRole,
    String status,
    Long publisherId,
    LocalDateTime publishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
