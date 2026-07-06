package com.example.pvplatform.module.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.ApiCallLogDO;
import com.example.pvplatform.persistence.mapper.ApiCallLogMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ApiCallLogService {
    private static final Logger log = LoggerFactory.getLogger(ApiCallLogService.class);
    private final ApiCallLogMapper logMapper;

    public ApiCallLogService(ApiCallLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long userId, Long apiKeyId, Long modelId, String path, String method,
                     String ip, LocalDateTime requestTime, int httpStatus, String error,
                     String requestSummary, String responseSummary) {
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
            logMapper.insert(row);
        } catch (Exception e) {
            log.error("保存 API 调用日志失败", e);
        }
    }

    public PageResult<ApiCallLogDO> ownLogs(int pageNum, int pageSize) {
        return logs(pageNum, pageSize, SecurityUtils.requireCurrentUserId());
    }

    public PageResult<ApiCallLogDO> adminLogs(int pageNum, int pageSize) {
        return logs(pageNum, pageSize, null);
    }

    private PageResult<ApiCallLogDO> logs(int pageNum, int pageSize, Long userId) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数不合法");
        }
        var query = Wrappers.<ApiCallLogDO>lambdaQuery()
            .eq(userId != null, ApiCallLogDO::getUserId, userId)
            .orderByDesc(ApiCallLogDO::getRequestTime);
        Page<ApiCallLogDO> page = logMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String safe = value.replaceAll("pv_[0-9a-f]{8}_[A-Za-z0-9_-]{43}", "[REDACTED]");
        return safe.length() > 500 ? safe.substring(0, 500) : safe;
    }
}
