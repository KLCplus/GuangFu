package com.example.pvplatform.module.openapi.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.common.state.DistributedStateService;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ApiQuotaService {
    private final DistributedStateService stateService;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public ApiQuotaService() {
        this.stateService = null;
    }

    @Autowired
    public ApiQuotaService(DistributedStateService stateService) {
        this.stateService = stateService;
    }

    public void checkAndConsume(ApiKeyDO key) {
        String minute = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        long minuteCount = increment("pv:openapi:quota:m:" + key.getApiKeyId() + ":" + minute,
            Duration.ofMinutes(2), "m:" + key.getApiKeyId() + ":" + minute);
        if (minuteCount > key.getRateLimitPerMinute()) {
            throw new BusinessException(429, "请求过于频繁");
        }

        long dailyCount = increment("pv:openapi:quota:d:" + key.getApiKeyId() + ":" + day,
            Duration.ofDays(2), "d:" + key.getApiKeyId() + ":" + day);
        if (dailyCount > key.getDailyQuota()) {
            throw new BusinessException(429, "今日调用额度已用完");
        }

        if (counters.size() > 10000) {
            counters.keySet().removeIf(k -> !k.contains(day) && !k.contains(minute));
        }
    }

    private long increment(String distributedKey, Duration ttl, String localKey) {
        if (stateService != null) {
            return stateService.increment(distributedKey, ttl);
        }
        return counters.computeIfAbsent(localKey, ignored -> new AtomicInteger()).incrementAndGet();
    }
}
