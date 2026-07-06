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
    public Result<?> task(@PathVariable Long taskId) {
        return Result.success(predictionService.task(taskId));
    }

    @GetMapping("/{taskId}/results")
    public Result<?> results(@PathVariable Long taskId) {
        return Result.success(predictionService.results(taskId));
    }

    @GetMapping("/history")
    public Result<?> history() {
        return Result.success(predictionService.history());
    }
}
