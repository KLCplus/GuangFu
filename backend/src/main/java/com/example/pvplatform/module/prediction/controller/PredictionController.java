package com.example.pvplatform.module.prediction.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.prediction.dto.PredictionRequest;
import com.example.pvplatform.module.prediction.service.PredictionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody PredictionRequest request) {
        return Result.success(predictionService.create(request));
    }

    @GetMapping("/{taskId}")
    public Result<?> detail(@PathVariable Long taskId) {
        return Result.success(predictionService.detail(taskId));
    }

    @GetMapping("/{taskId}/results")
    public Result<?> results(@PathVariable Long taskId) {
        return Result.success(predictionService.results(taskId));
    }

    @GetMapping("/history")
    public Result<?> history(@RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize,
                              @RequestParam(required = false) Long stationId,
                              @RequestParam(required = false) Long modelId,
                              @RequestParam(required = false) String status) {
        return Result.success(predictionService.history(pageNum, pageSize, stationId, modelId, status));
    }
}
