package com.example.pvplatform.module.agent.llm;

import java.util.List;

public record AgentLlmDecision(
    String type,
    String reason,
    List<AgentToolCallSpec> toolCalls,
    String answer,
    String question
) {
    public boolean isToolCall() { return "tool_call".equals(type) && toolCalls != null && !toolCalls.isEmpty(); }
    public boolean isFinal() { return "final".equals(type); }
    public boolean isAskUser() { return "ask_user".equals(type); }
}
