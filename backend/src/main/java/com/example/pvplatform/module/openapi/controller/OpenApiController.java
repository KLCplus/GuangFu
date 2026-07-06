package com.example.pvplatform.module.openapi.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.openapi.dto.OpenPredictRequest;
import com.example.pvplatform.module.openapi.service.OpenApiService;
import org.springframework.web.bind.annotation.*;

@RestController
public class OpenApiController {
    private final OpenApiService openApiService;

    public OpenApiController(OpenApiService openApiService) {
        this.openApiService = openApiService;
    }

    @PostMapping("/api/open/apply-key")
    public Result<?> applyKey() {
        return Result.success(openApiService.applyKey());
    }

    @GetMapping("/api/open/call-logs")
    public Result<?> callLogs() {
        return Result.success(openApiService.callLogs());
    }

    @PostMapping("/openapi/v1/predict")
    public Result<?> predict(@RequestBody OpenPredictRequest request,
                             @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {
        return Result.success(openApiService.predict(request).data());
    }
}
