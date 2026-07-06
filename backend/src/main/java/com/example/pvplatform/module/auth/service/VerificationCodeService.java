package com.example.pvplatform.module.auth.service;

import com.example.pvplatform.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory verification code store with:
 * - 60-second resend cooldown per key
 * - Daily send limit per key
 * - Hashed code storage (plain code only returned on generate, not stored)
 */
@Service
public class VerificationCodeService {

    private final int expireMinutes;
    private final int codeLength;
    private final int maxAttempts = 5;
    private final int resendCooldownSeconds = 60;
    private final int maxDailySends = 20;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, CodeRecord> codes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SendHistory> sendHistory = new ConcurrentHashMap<>();

    public VerificationCodeService(
            @org.springframework.beans.factory.annotation.Value("${security.verification.code-expire-minutes:5}") int expireMinutes,
            @org.springframework.beans.factory.annotation.Value("${security.verification.code-length:6}") int codeLength) {
        this.expireMinutes = expireMinutes;
        this.codeLength = codeLength;
    }

    public int getExpireMinutes() { return expireMinutes; }

    /**
     * Generate a code, store its hash, and enforce rate limits.
     * Returns the plain code (to be sent via email — not logged!).
     */
    public String generate(String key) {
        String lowered = lower(key);
        SendHistory history = sendHistory.computeIfAbsent(lowered, k -> new SendHistory());

        // 60-second cooldown
        if (history.lastSentAt != null) {
            long elapsed = java.time.Duration.between(history.lastSentAt, LocalDateTime.now()).getSeconds();
            if (elapsed < resendCooldownSeconds) {
                throw new BusinessException(429,
                    "请 " + (resendCooldownSeconds - elapsed) + " 秒后再发送验证码");
            }
        }

        // Daily limit
        if (history.todayCount >= maxDailySends && sameDay(history.lastSentAt)) {
            throw new BusinessException(429, "今日发送次数已达上限");
        }

        // Reset daily count if new day
        if (!sameDay(history.lastSentAt)) {
            history.todayCount = 0;
        }

        String code = generateCode();
        String hash = hash(code);

        codes.put(lowered, new CodeRecord(hash, LocalDateTime.now(), 0));
        history.lastSentAt = LocalDateTime.now();
        history.todayCount++;

        return code;
    }

    /**
     * Verify the input code against stored hash.
     */
    public void verify(String key, String inputCode) {
        CodeRecord record = codes.get(lower(key));
        if (record == null) throw new BusinessException(400, "验证码不存在或已过期");
        if (LocalDateTime.now().isAfter(record.createdAt.plusMinutes(expireMinutes))) {
            codes.remove(lower(key));
            throw new BusinessException(400, "验证码已过期");
        }
        if (record.attempts >= maxAttempts) {
            codes.remove(lower(key));
            throw new BusinessException(400, "验证码尝试次数过多");
        }
        record.attempts++;
        if (!hash(inputCode).equals(record.codeHash)) {
            if (record.attempts >= maxAttempts) codes.remove(lower(key));
            throw new BusinessException(400, "验证码错误");
        }
        codes.remove(lower(key)); // success — single use
    }

    /** For tests only */
    public String peek(String key) {
        // Can't peek hashed codes — tests should use generate()
        return null;
    }

    public void clear(String key) {
        codes.remove(lower(key));
        sendHistory.remove(lower(key));
    }

    private String generateCode() {
        int min = (int) Math.pow(10, codeLength - 1);
        int max = (int) Math.pow(10, codeLength) - 1;
        return String.valueOf(random.nextInt(max - min + 1) + min);
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new RuntimeException("Hash failed", e);
        }
    }

    private boolean sameDay(LocalDateTime t) {
        if (t == null) return false;
        return t.toLocalDate().equals(LocalDateTime.now().toLocalDate());
    }

    private String lower(String s) { return s == null ? "" : s.toLowerCase(); }

    private static class CodeRecord {
        final String codeHash;
        final LocalDateTime createdAt;
        int attempts;
        CodeRecord(String h, LocalDateTime t, int a) { codeHash = h; createdAt = t; attempts = a; }
    }

    private static class SendHistory {
        LocalDateTime lastSentAt;
        int todayCount;
    }
}
