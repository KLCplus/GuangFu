package com.example.pvplatform.module.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentMemoryDTO(
    Long memoryId,
    Long userId,
    String memoryType,
    String memoryKey,
    Object value,
    String sourceMessage,
    BigDecimal confidence,
    Boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
