package com.example.pvplatform.module.auth.service;

import com.example.pvplatform.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login attempt rate limiter. Single-instance only (like ApiQuotaService).
 * Tracks failed attempts per username; locks after threshold exceeded.
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final int lockMinutes;
    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${security.login.max-failures:5}") int maxFailures,
                               @Value("${security.login.lock-minutes:15}") int lockMinutes) {
        this.maxFailures = maxFailures;
        this.lockMinutes = lockMinutes;
    }

    /**
     * Check if the given username is locked. Throws if locked.
     */
    public void checkNotLocked(String username) {
        AttemptRecord record = attempts.get(lower(username));
        if (record == null) {
            return;
        }
        if (record.lockedUntil != null) {
            if (LocalDateTime.now().isBefore(record.lockedUntil)) {
                long remainingSec = java.time.Duration.between(
                    LocalDateTime.now(), record.lockedUntil).getSeconds();
                throw new BusinessException(429,
                    "账号已被锁定，请" + (remainingSec / 60 + 1) + "分钟后重试");
            }
            // lock expired, clear
            attempts.remove(lower(username));
        }
    }

    /**
     * Record a failed login attempt. If threshold exceeded, set lock.
     */
    public void recordFailure(String username) {
        String key = lower(username);
        attempts.compute(key, (k, v) -> {
            if (v == null || (v.lockedUntil != null && LocalDateTime.now().isAfter(v.lockedUntil))) {
                return new AttemptRecord(1, null);
            }
            v.count++;
            if (v.count >= maxFailures) {
                v.lockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
            }
            return v;
        });
    }

    /**
     * Clear attempts on successful login.
     */
    public void recordSuccess(String username) {
        attempts.remove(lower(username));
    }

    /**
     * Clear for a username (useful for testing).
     */
    public void clear(String username) {
        attempts.remove(lower(username));
    }

    private String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static class AttemptRecord {
        int count;
        LocalDateTime lockedUntil;

        AttemptRecord(int count, LocalDateTime lockedUntil) {
            this.count = count;
            this.lockedUntil = lockedUntil;
        }
    }
}
