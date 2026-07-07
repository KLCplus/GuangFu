package com.example.pvplatform.module.auth.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.common.state.DistributedStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;

@Service
public class VerificationCodeService {
    private final int expireMinutes;
    private final int codeLength;
    private final int maxAttempts;
    private final int resendCooldownSeconds;
    private final int maxDailySends;
    private final DistributedStateService stateService;
    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(
            @Value("${security.verification.code-expire-minutes:5}") int expireMinutes,
            @Value("${security.verification.code-length:6}") int codeLength,
            @Value("${security.verification.max-attempts:5}") int maxAttempts,
            @Value("${security.verification.resend-cooldown-seconds:60}") int resendCooldownSeconds,
            @Value("${security.verification.max-daily-sends:20}") int maxDailySends,
            DistributedStateService stateService) {
        this.expireMinutes = expireMinutes;
        this.codeLength = codeLength;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxDailySends = maxDailySends;
        this.stateService = stateService;
    }

    public int getExpireMinutes() {
        return expireMinutes;
    }

    public String generate(String key) {
        String lowered = lower(key);
        String cooldownKey = stateKey("cooldown", lowered);
        if (stateService.exists(cooldownKey)) {
            long ttl = Math.max(1, stateService.ttlSeconds(cooldownKey));
            throw new BusinessException(429, "请 " + ttl + " 秒后再发送验证码");
        }

        long dailyCount = stateService.increment(dailyKey(lowered), Duration.ofDays(2));
        if (dailyCount > maxDailySends) {
            throw new BusinessException(429, "今日发送次数已达上限");
        }

        String code = generateCode();
        stateService.set(stateKey("code", lowered), hash(code), Duration.ofMinutes(expireMinutes));
        stateService.delete(stateKey("attempts", lowered));
        stateService.set(cooldownKey, "1", Duration.ofSeconds(resendCooldownSeconds));
        return code;
    }

    public void verify(String key, String inputCode) {
        String lowered = lower(key);
        String codeKey = stateKey("code", lowered);
        String attemptsKey = stateKey("attempts", lowered);
        String storedHash = stateService.get(codeKey);
        if (storedHash == null) {
            throw new BusinessException(400, "验证码不存在或已过期");
        }

        long attempts = stateService.increment(attemptsKey, Duration.ofMinutes(expireMinutes));
        if (attempts > maxAttempts) {
            stateService.delete(codeKey, attemptsKey);
            throw new BusinessException(400, "验证码尝试次数过多");
        }
        if (!hash(inputCode).equals(storedHash)) {
            if (attempts >= maxAttempts) {
                stateService.delete(codeKey, attemptsKey);
            }
            throw new BusinessException(400, "验证码错误");
        }
        stateService.delete(codeKey, attemptsKey);
    }

    public String peek(String key) {
        return null;
    }

    public void clear(String key) {
        String lowered = lower(key);
        stateService.delete(stateKey("code", lowered), stateKey("attempts", lowered),
            stateKey("cooldown", lowered), dailyKey(lowered));
    }

    private String generateCode() {
        int min = (int) Math.pow(10, codeLength - 1);
        int max = (int) Math.pow(10, codeLength) - 1;
        return String.valueOf(random.nextInt(max - min + 1) + min);
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Hash failed", e);
        }
    }

    private String stateKey(String type, String value) {
        return "pv:auth:verify:" + type + ":" + value;
    }

    private String dailyKey(String value) {
        return "pv:auth:verify:daily:" + value + ":" + LocalDate.now();
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
