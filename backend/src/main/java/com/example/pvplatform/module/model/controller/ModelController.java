package com.example.pvplatform.module.model.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.model.dto.ModelRequest;
import com.example.pvplatform.module.model.service.ModelService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ModelController {
    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/api/models")
    public Result<?> list() {
        return Result.success(modelService.list());
    }

    @GetMapping("/api/models/{modelId}")
    public Result<?> detail(@PathVariable Long modelId) {
        return Result.success(modelService.detail(modelId));
    }

    @PostMapping("/api/admin/models")
    public Result<?> create(@RequestBody ModelRequest request) {
        return Result.success(Map.of("modelId", 4L, "modelCode", request.modelCode()));
    }

    @PutMapping("/api/admin/models/{modelId}/status")
    public Result<?> updateStatus(@PathVariable Long modelId, @RequestBody Map<String, String> request) {
        return Result.success(Map.of("modelId", modelId, "modelStatus", request.getOrDefault("modelStatus", "OFFLINE")));
    }
}
