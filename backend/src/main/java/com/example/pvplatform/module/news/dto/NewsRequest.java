package com.example.pvplatform.module.news.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewsRequest(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 500) String summary,
    @Size(max = 2_000_000) String content,
    @Size(max = 512) String coverUrl,
    @NotBlank String newsType,
    @NotBlank String targetRole
) {}
