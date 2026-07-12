package com.example.pvplatform.module.agent.dto;

import java.util.List;

public record InternalMemoryWriteRequest(
    Long userId,
    String username,
    List<String> roles,
    String memoryType,
    String memoryKey,
    Object value,
    String sourceMessage,
    Double confidence,
    Boolean enabled
) {}
