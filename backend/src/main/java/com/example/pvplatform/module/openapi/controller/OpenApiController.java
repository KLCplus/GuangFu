package com.example.pvplatform.module.openapi.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.openapi.dto.ApiKeyApplyRequest;
import com.example.pvplatform.module.openapi.dto.ApiKeyStatusRequest;
import com.example.pvplatform.module.openapi.dto.MarketplaceTrialRequest;
import com.example.pvplatform.module.openapi.dto.OpenPredictRequest;
import com.example.pvplatform.module.openapi.service.ApiCallLogService;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.service.OpenAccountService;
import com.example.pvplatform.module.openapi.service.OpenApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class OpenApiController {
    private final OpenApiService openApiService;
    private final OpenAccountService openAccountService;
    private final ApiKeyService apiKeyService;
    private final ApiCallLogService callLogService;

    public OpenApiController(OpenApiService openApiService, OpenAccountService openAccountService,
                             ApiKeyService apiKeyService, ApiCallLogService callLogService) {
        this.openApiService = openApiService;
        this.openAccountService = openAccountService;
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

    @PostMapping("/api/open/keys/{apiKeyId}/reset")
    public Result<?> reset(@PathVariable Long apiKeyId) {
        return Result.success(apiKeyService.resetOwn(apiKeyId));
    }

    @GetMapping("/api/open/call-logs")
    public Result<?> callLogs(@RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize,
                              @RequestParam(required = false) Long apiKeyId,
                              @RequestParam(required = false) String status) {
        return Result.success(callLogService.ownLogs(pageNum, pageSize, apiKeyId, status));
    }

    @PostMapping("/api/open/trials")
    public Result<?> requestTrial(@Valid @RequestBody MarketplaceTrialRequest request) {
        return Result.success(openAccountService.requestTrial(request.modelId()));
    }

    @GetMapping("/api/open/entitlements")
    public Result<?> entitlements() {
        return Result.success(openAccountService.entitlements());
    }

    @GetMapping("/api/open/wallet")
    public Result<?> wallet() {
        return Result.success(openAccountService.wallet());
    }

    @GetMapping("/api/open/plans")
    public Result<?> plans() {
        return Result.success(openAccountService.plans());
    }

    @GetMapping("/api/open/overview")
    public Result<?> overview() {
        return Result.success(openAccountService.overview());
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
                               @RequestParam(defaultValue = "10") int pageSize,
                               @RequestParam(required = false) Long apiKeyId,
                               @RequestParam(required = false) String status) {
        return Result.success(callLogService.adminLogs(pageNum, pageSize, apiKeyId, status));
    }
}
