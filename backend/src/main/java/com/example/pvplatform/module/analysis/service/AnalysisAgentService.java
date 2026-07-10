package com.example.pvplatform.module.analysis.service;

import com.example.pvplatform.module.analysis.llm.DeepSeekLlmClient;
import com.example.pvplatform.module.analysis.llm.LlmClient;
import com.example.pvplatform.module.analysis.llm.LlmGenerationResult;
import com.example.pvplatform.module.analysis.llm.LlmProperties;
import com.example.pvplatform.module.analysis.llm.MockLlmClient;
import org.springframework.stereotype.Service;

@Service
public class AnalysisAgentService {
    private final LlmProperties properties;
    private final LlmClient deepSeekLlmClient;
    private final LlmClient mockLlmClient;

    public AnalysisAgentService(LlmProperties properties,
                                DeepSeekLlmClient deepSeekLlmClient,
                                MockLlmClient mockLlmClient) {
        this.properties = properties;
        this.deepSeekLlmClient = deepSeekLlmClient;
        this.mockLlmClient = mockLlmClient;
    }

    public LlmGenerationResult generate(AnalysisContext context, String userInstruction) {
        if (!properties.isEnabled()) {
            return mockLlmClient.generateAnalysisReport(context, userInstruction);
        }
        // Future self-developed models or model-service LLMs can replace this through LlmClient.
        return deepSeekLlmClient.generateAnalysisReport(context, userInstruction);
    }
}
