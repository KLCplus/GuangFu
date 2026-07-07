package com.example.pvplatform.module.auth.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.common.state.DistributedStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginAttemptService {
    private final int maxFailures;
    private final int lockMinutes;
    private final DistributedStateService stateService;

    public LoginAttemptService(@Value("${security.login.max-failures:5}") int maxFailures,
                               @Value("${security.login.lock-minutes:15}") int lockMinutes,
                               DistributedStateService stateService) {
        this.maxFailures = maxFailures;
        this.lockMinutes = lockMinutes;
        this.stateService = stateService;
    }

    public void checkNotLocked(String username) {
        String key = lockKey(username);
        if (!stateService.exists(key)) {
            return;
        }
        long remainingSeconds = Math.max(1L, stateService.ttlSeconds(key));
        throw new BusinessException(429,
            "账号已被锁定，请 " + (remainingSeconds / 60 + 1) + " 分钟后重试");
    }

    public void recordFailure(String username) {
        long failures = stateService.increment(failureKey(username), Duration.ofMinutes(lockMinutes));
        if (failures >= maxFailures) {
            stateService.set(lockKey(username), "1", Duration.ofMinutes(lockMinutes));
            stateService.delete(failureKey(username));
        }
    }

    public void recordSuccess(String username) {
        clear(username);
    }

    public void clear(String username) {
        stateService.delete(failureKey(username), lockKey(username));
    }

    private String failureKey(String username) {
        return "pv:auth:login:fail:" + lower(username);
    }

    private String lockKey(String username) {
        return "pv:auth:login:lock:" + lower(username);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
