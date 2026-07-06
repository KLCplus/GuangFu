package com.example.pvplatform.module.openapi.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiQuotaServiceTest {
    @Test
    void enforcesMinuteLimit() {
        ApiKeyDO key = key(1L, 1, 10);
        ApiQuotaService service = new ApiQuotaService();
        service.checkAndConsume(key);

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.checkAndConsume(key));
        assertEquals(429, error.getCode());
        assertEquals("请求过于频繁", error.getMessage());
    }

    @Test
    void enforcesDailyQuota() {
        ApiKeyDO key = key(2L, 10, 1);
        ApiQuotaService service = new ApiQuotaService();
        service.checkAndConsume(key);

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.checkAndConsume(key));
        assertEquals("今日调用额度已用完", error.getMessage());
    }

    private ApiKeyDO key(Long id, int minuteLimit, int dailyQuota) {
        ApiKeyDO key = new ApiKeyDO();
        key.setApiKeyId(id);
        key.setRateLimitPerMinute(minuteLimit);
        key.setDailyQuota(dailyQuota);
        return key;
    }
}
