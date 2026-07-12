package com.example.pvplatform.module.agent.tool;

import java.util.List;

public record ToolExecutionResult(
    boolean success,
    String displayName,
    Object data,
    String summary,
    List<String> highlights,
    String errorCode,
    String errorMessage,
    Object raw
) {
    public static ToolExecutionResult success(Object data, String summary) {
        return success(null, data, summary, List.of());
    }

    public static ToolExecutionResult success(String displayName, Object data, String summary, List<String> highlights) {
        return new ToolExecutionResult(true, displayName, data, summary, highlights == null ? List.of() : highlights, null, null, data);
    }

    public static ToolExecutionResult failure(String code, String message) {
        return new ToolExecutionResult(false, null, null, message, List.of(), code, message, null);
    }

    public ToolExecutionResult withDisplayName(String nextDisplayName) {
        return new ToolExecutionResult(success, nextDisplayName, data, summary, highlights == null ? List.of() : highlights, errorCode, errorMessage, raw);
    }

    public ToolExecutionResult withHighlights(List<String> nextHighlights) {
        return new ToolExecutionResult(success, displayName, data, summary, nextHighlights == null ? List.of() : nextHighlights, errorCode, errorMessage, raw);
    }
}
