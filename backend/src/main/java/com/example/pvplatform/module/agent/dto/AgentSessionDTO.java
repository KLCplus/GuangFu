package com.example.pvplatform.module.agent.dto;

import java.time.LocalDateTime;

public record AgentSessionDTO(
    Long sessionId,
    String title,
    Boolean archived,
    Boolean pinned,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
