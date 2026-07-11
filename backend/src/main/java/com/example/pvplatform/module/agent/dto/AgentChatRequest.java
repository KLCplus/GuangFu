package com.example.pvplatform.module.agent.dto;

import java.util.List;
import java.util.Map;

public record AgentChatRequest(
    Long sessionId,
    String message,
    Map<String, Object> context,
    String mode,
    List<String> allowedTools,
    Boolean requireApproval,
    Long approvalId
) {}
