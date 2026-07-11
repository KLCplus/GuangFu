package com.example.pvplatform.module.agent.tool;

import com.example.pvplatform.security.SecurityUser;

import java.util.List;

public record ToolExecutionContext(
    Long currentUserId,
    String username,
    List<String> roles,
    Long sessionId,
    Long messageId,
    Long currentStationId,
    Long currentTaskId,
    Long currentReportId,
    String requestId,
    SecurityUser user
) {
    public ToolExecutionContext(Long userId, Long sessionId, Long messageId, SecurityUser user) {
        this(userId,
            user == null ? null : user.getUsername(),
            user == null ? List.of() : user.getAuthorities().stream().map(Object::toString).toList(),
            sessionId, messageId, null, null, null, null, user);
    }
}
