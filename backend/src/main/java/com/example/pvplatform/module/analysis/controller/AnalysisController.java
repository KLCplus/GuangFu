package com.example.pvplatform.module.analysis.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/report")
    public Result<?> report(@RequestBody AnalysisRequest request) {
        return Result.success(analysisService.report(request));
    }
}
