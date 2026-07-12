package com.example.pvplatform.module.agent.dto;

import java.util.List;
import java.util.Map;

public record InternalToolExecuteResponse(
    boolean success,
    String summary,
    List<String> highlights,
    Object data,
    String error
) {
    public static InternalToolExecuteResponse failure(String code, String message) {
        return new InternalToolExecuteResponse(false, message, List.of(code), null, message);
    }

    public static InternalToolExecuteResponse approvalRequired(Long approvalId, Long toolCallId, String clientToolCallId,
                                                               String toolName, String reason, Object arguments) {
        return new InternalToolExecuteResponse(false, reason, List.of("APPROVAL_REQUIRED"), Map.of(
            "approvalRequired", true,
            "approvalId", approvalId,
            "toolCallId", toolCallId,
            "clientToolCallId", clientToolCallId,
            "toolName", toolName,
            "reason", reason,
            "arguments", arguments == null ? Map.of() : arguments
        ), "APPROVAL_REQUIRED");
    }
}
