package com.example.pvplatform.module.analysis.llm;

import com.example.pvplatform.common.exception.BusinessException;

public class AnalysisLlmException extends BusinessException {
    private final String rawResponse;

    public AnalysisLlmException(int code, String message) {
        this(code, message, null);
    }

    public AnalysisLlmException(int code, String message, String rawResponse) {
        super(code, message);
        this.rawResponse = rawResponse;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
