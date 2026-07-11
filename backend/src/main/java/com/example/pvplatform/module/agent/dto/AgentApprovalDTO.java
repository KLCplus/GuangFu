package com.example.pvplatform.module.agent.dto;

import java.time.LocalDateTime;

public record AgentApprovalDTO(
    Long approvalId,
    Long sessionId,
    Long toolCallId,
    String toolName,
    String reason,
    Object arguments,
    String status,
    String comment,
    LocalDateTime decidedAt,
    LocalDateTime createdAt
) {}
