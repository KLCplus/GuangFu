package com.example.pvplatform.module.agent.tool;

import com.example.pvplatform.security.SecurityUser;

public record ToolExecutionContext(
    Long userId,
    Long sessionId,
    Long messageId,
    SecurityUser user
) {}
