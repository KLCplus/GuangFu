package com.example.pvplatform.module.agent.dto;

import java.time.LocalDateTime;

public record AgentToolCallDTO(
    Long toolCallId,
    Long sessionId,
    Long messageId,
    String clientToolCallId,
    String toolName,
    String displayName,
    Object arguments,
    Object result,
    String status,
    String errorMessage,
    Long durationMs,
    LocalDateTime createdAt
) {}
