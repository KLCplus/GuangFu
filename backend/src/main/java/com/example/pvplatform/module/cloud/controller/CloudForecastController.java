package com.example.pvplatform.module.cloud.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.cloud.dto.CloudForecastRequest;
import com.example.pvplatform.module.cloud.service.CloudForecastService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cloud-forecast")
public class CloudForecastController {
    private final CloudForecastService cloudForecastService;

    public CloudForecastController(CloudForecastService cloudForecastService) {
        this.cloudForecastService = cloudForecastService;
    }

    @PostMapping("/predict")
    public Result<?> predict(@Valid @RequestBody CloudForecastRequest request) {
        return Result.success(cloudForecastService.predict(request));
    }
}
