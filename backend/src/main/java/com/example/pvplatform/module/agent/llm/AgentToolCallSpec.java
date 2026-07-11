package com.example.pvplatform.module.agent.llm;

import java.util.Map;

public record AgentToolCallSpec(String toolName, Map<String, Object> arguments) {}
