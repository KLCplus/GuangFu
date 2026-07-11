package com.example.pvplatform.module.agent.tool;

public record ToolExecutionResult(
    boolean success,
    Object data,
    String summary,
    String errorCode,
    String errorMessage,
    Object raw
) {
    public static ToolExecutionResult success(Object data, String summary) {
        return new ToolExecutionResult(true, data, summary, null, null, data);
    }

    public static ToolExecutionResult failure(String code, String message) {
        return new ToolExecutionResult(false, null, message, code, message, null);
    }
}
