package com.example.pvplatform.module.openapi.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.openapi.dto.ApiKeyApplyRequest;
import com.example.pvplatform.module.openapi.dto.ApiKeyStatusRequest;
import com.example.pvplatform.module.openapi.dto.OpenPredictRequest;
import com.example.pvplatform.module.openapi.service.ApiCallLogService;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.service.OpenApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class OpenApiController {
    private final OpenApiService openApiService;
    private final ApiKeyService apiKeyService;
    private final ApiCallLogService callLogService;

    public OpenApiController(OpenApiService openApiService, ApiKeyService apiKeyService,
                             ApiCallLogService callLogService) {
        this.openApiService = openApiService;
        this.apiKeyService = apiKeyService;
        this.callLogService = callLogService;
    }

    @PostMapping("/api/open/apply-key")
    public Result<?> applyKey(@Valid @RequestBody ApiKeyApplyRequest request) {
        return Result.success(apiKeyService.create(request));
    }

    @GetMapping("/api/open/keys")
    public Result<?> keys() {
        return Result.success(apiKeyService.listOwn());
    }

    @PutMapping("/api/open/keys/{apiKeyId}/status")
    public Result<?> updateStatus(@PathVariable Long apiKeyId,
                                  @Valid @RequestBody ApiKeyStatusRequest request) {
        apiKeyService.updateOwnStatus(apiKeyId, request.status());
        return Result.success();
    }

    @DeleteMapping("/api/open/keys/{apiKeyId}")
    public Result<?> delete(@PathVariable Long apiKeyId) {
        apiKeyService.deleteOwn(apiKeyId);
        return Result.success();
    }

    @GetMapping("/api/open/call-logs")
    public Result<?> callLogs(@RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(callLogService.ownLogs(pageNum, pageSize));
    }

    @PostMapping("/openapi/v1/predict")
    public Result<?> predict(@RequestBody OpenPredictRequest request,
                             HttpServletRequest httpRequest) {
        return Result.success(openApiService.predict(request, httpRequest));
    }

    @GetMapping("/api/admin/api-keys")
    public Result<?> adminKeys() {
        return Result.success(apiKeyService.adminList());
    }

    @PutMapping("/api/admin/api-keys/{apiKeyId}/status")
    public Result<?> adminUpdateStatus(@PathVariable Long apiKeyId,
                                       @Valid @RequestBody ApiKeyStatusRequest request) {
        apiKeyService.updateAnyStatus(apiKeyId, request.status());
        return Result.success();
    }

    @GetMapping("/api/admin/api-call-logs")
    public Result<?> adminLogs(@RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(callLogService.adminLogs(pageNum, pageSize));
    }
}
