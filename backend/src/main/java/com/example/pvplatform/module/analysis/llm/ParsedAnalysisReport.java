package com.example.pvplatform.module.analysis.llm;

import com.example.pvplatform.module.analysis.vo.AnalysisSectionVO;

import java.util.List;

public record ParsedAnalysisReport(
    String summary,
    String riskLevel,
    List<AnalysisSectionVO> sections,
    List<String> suggestions,
    String markdown,
    String rawText
) {}
