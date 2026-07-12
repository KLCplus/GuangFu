package com.example.pvplatform.module.agent.dto;

import java.util.List;

public record InternalMemoryListRequest(
    Long userId,
    String username,
    List<String> roles,
    String memoryType,
    Boolean enabledOnly,
    Integer limit
) {}
