package com.example.pvplatform.module.agent.dto;

import java.util.List;

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
}

