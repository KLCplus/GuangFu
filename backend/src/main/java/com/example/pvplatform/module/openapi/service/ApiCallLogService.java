package com.example.pvplatform.module.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.openapi.vo.ApiCallLogVO;
import com.example.pvplatform.module.openapi.vo.ApiUsageByKeyVO;
import com.example.pvplatform.module.openapi.vo.ApiUsageByModelVO;
import com.example.pvplatform.module.openapi.vo.ApiUsageSummaryVO;
import com.example.pvplatform.module.openapi.vo.ApiUsageTrendVO;
import com.example.pvplatform.persistence.entity.ApiCallLogDO;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.mapper.ApiCallLogMapper;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ApiCallLogService {
    private static final Logger log = LoggerFactory.getLogger(ApiCallLogService.class);
    private static final int MAX_EXPORT_ROWS = 10000;
    private final ApiCallLogMapper logMapper;
    private final ModelInfoMapper modelMapper;

    public ApiCallLogService(ApiCallLogMapper logMapper, ModelInfoMapper modelMapper) {
        this.logMapper = logMapper;
        this.modelMapper = modelMapper;
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long userId, Long apiKeyId, Long modelId, String path, String method,
                     String ip, LocalDateTime requestTime, int httpStatus, String error,
                     String requestSummary, String responseSummary) {
        save(userId, apiKeyId, modelId, path, method, ip, requestTime, httpStatus, error,
            requestSummary, responseSummary, null, null, null);
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long userId, Long apiKeyId, Long modelId, String path, String method,
                     String ip, LocalDateTime requestTime, int httpStatus, String error,
                     String requestSummary, String responseSummary,
                     Long inputTokens, Long outputTokens, Long totalTokens) {
        try {
            LocalDateTime responseTime = LocalDateTime.now();
            ApiCallLogDO row = new ApiCallLogDO();
            row.setUserId(userId);
            row.setApiKeyId(apiKeyId);
            row.setModelId(modelId);
            row.setRequestPath(path);
            row.setRequestMethod(method);
            row.setRequestIp(ip);
            row.setRequestTime(requestTime);
            row.setResponseTime(responseTime);
            row.setCostTimeMs(java.time.Duration.between(requestTime, responseTime).toMillis());
            row.setHttpStatus(httpStatus);
            row.setBizStatus(httpStatus < 400 ? "SUCCESS" : "FAILED");
            row.setErrorMessage(sanitize(error));
            row.setRequestSummary(requestSummary);
            row.setResponseSummary(responseSummary);
            row.setInputTokens(inputTokens);
            row.setOutputTokens(outputTokens);
            row.setTotalTokens(totalTokens);
            logMapper.insert(row);
        } catch (Exception e) {
            log.error("保存 API 调用日志失败", e);
        }
    }

    // ---- 调用日志查询 ----

    public PageResult<ApiCallLogVO> ownLogs(int pageNum, int pageSize) {
        return ownLogs(pageNum, pageSize, null, null, null, null, null);
    }

    public PageResult<ApiCallLogVO> ownLogs(int pageNum, int pageSize, Long apiKeyId, String status) {
        return ownLogs(pageNum, pageSize, apiKeyId, status, null, null, null);
    }

    public PageResult<ApiCallLogVO> ownLogs(int pageNum, int pageSize, Long apiKeyId, String status,
                                             LocalDateTime startTime, LocalDateTime endTime, Long modelId) {
        return logs(pageNum, pageSize, SecurityUtils.requireCurrentUserId(), apiKeyId, status,
            startTime, endTime, modelId);
    }

    public PageResult<ApiCallLogVO> adminLogs(int pageNum, int pageSize) {
        return adminLogs(pageNum, pageSize, null, null, null, null, null);
    }

    public PageResult<ApiCallLogVO> adminLogs(int pageNum, int pageSize, Long apiKeyId, String status) {
        return adminLogs(pageNum, pageSize, apiKeyId, status, null, null, null);
    }

    public PageResult<ApiCallLogVO> adminLogs(int pageNum, int pageSize, Long apiKeyId, String status,
                                               LocalDateTime startTime, LocalDateTime endTime, Long modelId) {
        return logs(pageNum, pageSize, null, apiKeyId, status, startTime, endTime, modelId);
    }

    private PageResult<ApiCallLogVO> logs(int pageNum, int pageSize, Long userId, Long apiKeyId,
                                           String status, LocalDateTime startTime, LocalDateTime endTime,
                                           Long modelId) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数不合法");
        }
        LambdaQueryWrapper<ApiCallLogDO> query = Wrappers.<ApiCallLogDO>lambdaQuery()
            .eq(userId != null, ApiCallLogDO::getUserId, userId)
            .eq(apiKeyId != null, ApiCallLogDO::getApiKeyId, apiKeyId)
            .eq(modelId != null, ApiCallLogDO::getModelId, modelId)
            .eq(status != null && !status.isBlank(), ApiCallLogDO::getBizStatus, status)
            .ge(startTime != null, ApiCallLogDO::getRequestTime, startTime)
            .le(endTime != null, ApiCallLogDO::getRequestTime, endTime)
            .orderByDesc(ApiCallLogDO::getRequestTime);
        Page<ApiCallLogDO> page = logMapper.selectPage(new Page<>(pageNum, pageSize), query);
        Map<Long, String> modelNames = modelNames(page.getRecords());
        return new PageResult<>(page.getTotal(), pageNum, pageSize,
            page.getRecords().stream().map(row -> toVO(row, modelNames.get(row.getModelId()))).toList());
    }

    // ---- 使用统计 ----

    public ApiUsageSummaryVO getUsageSummary(Long userId, LocalDateTime startTime, LocalDateTime endTime,
                                              Long apiKeyId, Long modelId) {
        return logMapper.selectUsageSummary(userId, startTime, endTime, apiKeyId, modelId);
    }

    public List<ApiUsageTrendVO> getUsageTrend(Long userId, LocalDateTime startTime, LocalDateTime endTime,
                                                Long apiKeyId, Long modelId, String granularity) {
        String dateFormat = "HOUR".equalsIgnoreCase(granularity) ? "%Y-%m-%d %H:00" : "%Y-%m-%d";
        return logMapper.selectUsageTrend(userId, startTime, endTime, apiKeyId, modelId, dateFormat);
    }

    public List<ApiUsageByModelVO> getUsageByModel(Long userId, LocalDateTime startTime, LocalDateTime endTime,
                                                    Long apiKeyId) {
        return logMapper.selectUsageByModel(userId, startTime, endTime, apiKeyId);
    }

    public List<ApiUsageByKeyVO> getUsageByKey(Long userId, LocalDateTime startTime, LocalDateTime endTime,
                                                Long modelId) {
        return logMapper.selectUsageByKey(userId, startTime, endTime, modelId);
    }

    // ---- 导出 ----

    public List<ApiCallLogVO> exportCallLogs(Long userId, LocalDateTime startTime, LocalDateTime endTime,
                                              Long apiKeyId, Long modelId, String status) {
        LambdaQueryWrapper<ApiCallLogDO> query = Wrappers.<ApiCallLogDO>lambdaQuery()
            .eq(userId != null, ApiCallLogDO::getUserId, userId)
            .eq(apiKeyId != null, ApiCallLogDO::getApiKeyId, apiKeyId)
            .eq(modelId != null, ApiCallLogDO::getModelId, modelId)
            .eq(status != null && !status.isBlank(), ApiCallLogDO::getBizStatus, status)
            .ge(startTime != null, ApiCallLogDO::getRequestTime, startTime)
            .le(endTime != null, ApiCallLogDO::getRequestTime, endTime)
            .orderByDesc(ApiCallLogDO::getRequestTime)
            .last("LIMIT " + MAX_EXPORT_ROWS);
        List<ApiCallLogDO> rows = logMapper.selectList(query);
        Map<Long, String> modelNames = modelNames(rows);
        return rows.stream().map(row -> toVO(row, modelNames.get(row.getModelId()))).toList();
    }

    // ----

    private Map<Long, String> modelNames(List<ApiCallLogDO> rows) {
        List<Long> modelIds = rows.stream()
            .map(ApiCallLogDO::getModelId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (modelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return modelMapper.selectBatchIds(modelIds).stream()
            .collect(Collectors.toMap(ModelInfoDO::getModelId, ModelInfoDO::getModelName, (left, right) -> left));
    }

    private ApiCallLogVO toVO(ApiCallLogDO row, String modelName) {
        return new ApiCallLogVO(
            row.getLogId(),
            row.getApiKeyId(),
            row.getModelId(),
            modelName,
            row.getRequestPath(),
            row.getRequestMethod(),
            row.getRequestIp(),
            row.getRequestTime(),
            row.getResponseTime(),
            row.getCostTimeMs(),
            row.getCostTimeMs(),
            row.getHttpStatus(),
            row.getBizStatus(),
            row.getErrorMessage(),
            row.getRequestSummary(),
            row.getResponseSummary(),
            row.getInputTokens(),
            row.getOutputTokens(),
            row.getTotalTokens(),
            row.getRequestTime()
        );
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String safe = value.replaceAll("pv_[0-9a-f]{8}_[A-Za-z0-9_-]{43}", "[REDACTED]");
        return safe.length() > 500 ? safe.substring(0, 500) : safe;
    }
}
