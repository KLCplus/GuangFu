package com.example.pvplatform.module.model.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.model.service.ModelService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/api/models")
    public Result<?> list(@RequestParam(required = false) String type) {
        return Result.success(modelService.list(type));
    }

    @GetMapping("/api/models/{modelId}")
    public Result<?> detail(@PathVariable Long modelId) {
        return Result.success(modelService.detail(modelId));
    }
}
