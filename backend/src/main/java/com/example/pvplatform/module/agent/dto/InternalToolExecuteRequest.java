package com.example.pvplatform.module.agent.dto;

import java.util.List;
import java.util.Map;

public record InternalToolExecuteRequest(
    Long sessionId,
    Long userId,
    String username,
    List<String> roles,
    Map<String, Object> arguments,
    Map<String, Object> context,
    Boolean approved
) {
}

