package com.example.pvplatform.module.agent.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AgentMessageDTO(
    Long messageId,
    Long sessionId,
    String role,
    String content,
    Object metadata,
    List<AgentToolCallDTO> toolCalls,
    List<AgentApprovalDTO> approvals,
    LocalDateTime createdAt
) {}
