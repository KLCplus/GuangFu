package com.example.pvplatform.module.analysis.llm;

import com.example.pvplatform.module.analysis.service.AnalysisContext;

public interface LlmClient {
    LlmGenerationResult generateAnalysisReport(AnalysisContext context, String userInstruction);
}
