package com.example.pvplatform.module.analysis.llm;

public record LlmGenerationResult(
    ParsedAnalysisReport report,
    String modelName,
    String promptSnapshot,
    String rawResponse,
    boolean llmEnabled,
    String llmProvider,
    long durationMs
) {}
