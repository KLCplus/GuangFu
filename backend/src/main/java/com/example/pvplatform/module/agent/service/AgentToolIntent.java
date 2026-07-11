package com.example.pvplatform.module.agent.service;

import java.util.Map;

public record AgentToolIntent(
    boolean matched,
    String toolName,
    Map<String, Object> arguments,
    String normalizedMessage,
    String question,
    String reason,
    String source,
    boolean businessRelated
) {
    public static AgentToolIntent none(boolean businessRelated) {
        return new AgentToolIntent(false, null, Map.of(), "", "", "", "none", businessRelated);
    }

    public boolean needsQuestion() {
        return question != null && !question.isBlank();
    }
}
