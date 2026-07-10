package com.example.pvplatform.module.model.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.model.dto.CreateModelRequest;
import com.example.pvplatform.module.model.dto.UpdateModelRequest;
import com.example.pvplatform.module.model.dto.UpdateModelStatusRequest;
import com.example.pvplatform.module.model.service.ModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminModelController {

    private final ModelService modelService;

    public AdminModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/models")
    public Result<?> list() {
        return Result.success(modelService.adminList());
    }

    @PostMapping("/models")
    public Result<?> create(@Valid @RequestBody CreateModelRequest request) {
        Long modelId = modelService.create(request);
        return Result.success(modelService.detail(modelId));
    }

    @PutMapping("/models/{modelId}")
    public Result<?> update(@PathVariable Long modelId, @Valid @RequestBody UpdateModelRequest request) {
        modelService.update(modelId, request);
        return Result.success(modelService.detail(modelId));
    }

    @PutMapping("/models/{modelId}/status")
    public Result<?> updateStatus(@PathVariable Long modelId, @Valid @RequestBody UpdateModelStatusRequest request) {
        modelService.updateStatus(modelId, request.modelStatus());
        return Result.success(modelService.detail(modelId));
    }
}
