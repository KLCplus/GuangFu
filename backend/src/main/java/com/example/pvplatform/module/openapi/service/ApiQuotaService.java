package com.example.pvplatform.module.openapi.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ApiQuotaService {
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public void checkAndConsume(ApiKeyDO key) {
        String minute = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int minuteCount = counters.computeIfAbsent("m:" + key.getApiKeyId() + ":" + minute,
            ignored -> new AtomicInteger()).incrementAndGet();
        if (minuteCount > key.getRateLimitPerMinute()) {
            throw new BusinessException(429, "请求过于频繁");
        }
        int dailyCount = counters.computeIfAbsent("d:" + key.getApiKeyId() + ":" + day,
            ignored -> new AtomicInteger()).incrementAndGet();
        if (dailyCount > key.getDailyQuota()) {
            throw new BusinessException(429, "今日调用额度已用完");
        }
        if (counters.size() > 10000) {
            counters.keySet().removeIf(k -> !k.contains(day) && !k.contains(minute));
        }
    }
}
