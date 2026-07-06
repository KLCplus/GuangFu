package com.example.pvplatform.module.analysis.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/report")
    public Result<?> report(@Valid @RequestBody AnalysisRequest request) {
        return Result.success(analysisService.report(request));
    }

    @GetMapping("/reports")
    public Result<?> history(@RequestParam(defaultValue = "1") int pageNum,
                             @RequestParam(defaultValue = "10") int pageSize,
                             @RequestParam(required = false) Long stationId) {
        return Result.success(analysisService.history(pageNum, pageSize, stationId));
    }

    @GetMapping("/reports/{reportId}")
    public Result<?> detail(@PathVariable Long reportId) {
        return Result.success(analysisService.detail(reportId));
    }
}
