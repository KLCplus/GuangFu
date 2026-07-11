package com.example.pvplatform.module.openapi.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.openapi.dto.ApiKeyApplyRequest;
import com.example.pvplatform.module.openapi.dto.ApiKeyNameRequest;
import com.example.pvplatform.module.openapi.dto.ApiKeyStatusRequest;
import com.example.pvplatform.module.openapi.dto.MarketplaceTrialRequest;
import com.example.pvplatform.module.openapi.dto.OpenPredictRequest;
import com.example.pvplatform.module.openapi.dto.WalletRechargeRequest;
import com.example.pvplatform.module.openapi.service.ApiCallLogService;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.service.OpenAccountService;
import com.example.pvplatform.module.openapi.service.OpenApiService;
import com.example.pvplatform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

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

    // ---- API Key 管理 ----

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

    @PutMapping("/api/open/keys/{apiKeyId}/name")
    public Result<?> updateName(@PathVariable Long apiKeyId,
                                @Valid @RequestBody ApiKeyNameRequest request) {
        return Result.success(apiKeyService.updateOwnName(apiKeyId, request.keyName()));
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

    // ---- 调用日志 ----

    @GetMapping("/api/open/call-logs")
    public Result<?> callLogs(@RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize,
                              @RequestParam(required = false) Long apiKeyId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                              @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                              @RequestParam(required = false) Long modelId) {
        return Result.success(callLogService.ownLogs(pageNum, pageSize, apiKeyId, status,
            startTime, endTime, modelId));
    }

    @GetMapping("/api/open/call-logs/export")
    public Result<?> exportCallLogs(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                     @RequestParam(required = false) Long apiKeyId,
                                     @RequestParam(required = false) Long modelId,
                                     @RequestParam(required = false) String status) {
        return Result.success(callLogService.exportCallLogs(SecurityUtils.requireCurrentUserId(),
            startTime, endTime, apiKeyId, modelId, status));
    }

    // ---- 使用统计 ----

    @GetMapping("/api/open/usage/summary")
    public Result<?> usageSummary(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                   @RequestParam(required = false) Long apiKeyId,
                                   @RequestParam(required = false) Long modelId) {
        return Result.success(callLogService.getUsageSummary(SecurityUtils.requireCurrentUserId(),
            startTime, endTime, apiKeyId, modelId));
    }

    @GetMapping("/api/open/usage/trend")
    public Result<?> usageTrend(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                 @RequestParam(required = false) Long apiKeyId,
                                 @RequestParam(required = false) Long modelId,
                                 @RequestParam(defaultValue = "DAY") String granularity) {
        return Result.success(callLogService.getUsageTrend(SecurityUtils.requireCurrentUserId(),
            startTime, endTime, apiKeyId, modelId, granularity));
    }

    @GetMapping("/api/open/usage/by-model")
    public Result<?> usageByModel(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                   @RequestParam(required = false) Long apiKeyId) {
        return Result.success(callLogService.getUsageByModel(SecurityUtils.requireCurrentUserId(),
            startTime, endTime, apiKeyId));
    }

    @GetMapping("/api/open/usage/by-key")
    public Result<?> usageByKey(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                 @RequestParam(required = false) Long modelId) {
        return Result.success(callLogService.getUsageByKey(SecurityUtils.requireCurrentUserId(),
            startTime, endTime, modelId));
    }

    // ---- 开放平台账户 ----

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

    @PostMapping("/api/open/wallet/recharge")
    public Result<?> recharge(@Valid @RequestBody WalletRechargeRequest request) {
        return Result.success(openAccountService.recharge(request.amount(), request.channel()));
    }

    @GetMapping("/api/open/plans")
    public Result<?> plans() {
        return Result.success(openAccountService.plans());
    }

    @GetMapping("/api/open/overview")
    public Result<?> overview() {
        return Result.success(openAccountService.overview());
    }

    // ---- 开放预测 ----

    @PostMapping("/openapi/v1/predict")
    public Result<?> predict(@RequestBody OpenPredictRequest request,
                             HttpServletRequest httpRequest) {
        return Result.success(openApiService.predict(request, httpRequest));
    }

    // ---- 管理员 ----

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
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                               @RequestParam(required = false) Long modelId) {
        return Result.success(callLogService.adminLogs(pageNum, pageSize, apiKeyId, status,
            startTime, endTime, modelId));
    }
}
