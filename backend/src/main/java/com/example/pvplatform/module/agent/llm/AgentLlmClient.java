package com.example.pvplatform.module.agent.llm;

import java.util.List;
import java.util.Map;

public interface AgentLlmClient {
    AgentLlmDecision decide(String systemPrompt, List<Map<String, Object>> messages);
}
