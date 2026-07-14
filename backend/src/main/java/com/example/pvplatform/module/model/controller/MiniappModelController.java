package com.example.pvplatform.module.model.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.model.service.ModelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/models")
public class MiniappModelController {
    private final ModelService modelService;

    public MiniappModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public Result<?> list(@RequestParam(required = false) String type) {
        return Result.success(modelService.miniappPublicList(type));
    }

    @GetMapping("/{modelId}")
    public Result<?> detail(@PathVariable Long modelId) {
        return Result.success(modelService.miniappPublicDetail(modelId));
    }
}
