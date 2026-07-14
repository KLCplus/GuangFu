package com.example.pvplatform.module.news.vo;

import java.time.LocalDateTime;

public record NewsVO(
    Long newsId,
    String title,
    String summary,
    String content,
    String coverUrl,
    String newsType,
    String category,
    String contentType,
    String sourceType,
    String sourceName,
    String sourceUrl,
    String attachmentName,
    String attachmentType,
    String attachmentUrl,
    String externalId,
    LocalDateTime sourcePublishedAt,
    LocalDateTime fetchedAt,
    Boolean externalContent,
    String warningLevel,
    String warningRegion,
    String warningAgency,
    LocalDateTime effectiveAt,
    LocalDateTime expiresAt,
    String targetRole,
    String status,
    Long publisherId,
    LocalDateTime publishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
