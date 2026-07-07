package com.example.pvplatform.common.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DistributedStateService {
    private static final Logger log = LoggerFactory.getLogger(DistributedStateService.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final ConcurrentHashMap<String, MemoryValue> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MemoryCounter> counters = new ConcurrentHashMap<>();

    public DistributedStateService(ObjectProvider<StringRedisTemplate> redisTemplate,
                                   @Value("${redis.enabled:false}") boolean redisEnabled) {
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.redisEnabled = redisEnabled;
    }

    public boolean isRedisActive() {
        return redisEnabled && redisTemplate != null;
    }

    public void set(String key, String value, Duration ttl) {
        if (tryRedis(() -> {
            redisTemplate.opsForValue().set(key, value, ttl);
            return true;
        })) {
            return;
        }
        values.put(key, new MemoryValue(value, expiresAt(ttl)));
    }

    public String get(String key) {
        RedisResult<String> result = redis(() -> redisTemplate.opsForValue().get(key));
        if (result.success()) {
            return result.value();
        }
        MemoryValue value = values.get(key);
        if (value == null || value.expired()) {
            values.remove(key);
            return null;
        }
        return value.value();
    }

    public boolean exists(String key) {
        RedisResult<Boolean> result = redis(() -> Boolean.TRUE.equals(redisTemplate.hasKey(key)));
        if (result.success()) {
            return Boolean.TRUE.equals(result.value());
        }
        return get(key) != null;
    }

    public long increment(String key, Duration ttl) {
        RedisResult<Long> result = redis(() -> {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return count == null ? 0L : count;
        });
        if (result.success()) {
            return result.value();
        }
        cleanupCounters();
        MemoryCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expired()) {
                return new MemoryCounter(1, expiresAt(ttl));
            }
            existing.value().incrementAndGet();
            return existing;
        });
        return counter.value().get();
    }

    public long ttlSeconds(String key) {
        RedisResult<Long> result = redis(() -> {
            Long expire = redisTemplate.getExpire(key);
            return expire == null ? -1L : expire;
        });
        if (result.success()) {
            return result.value();
        }
        MemoryValue value = values.get(key);
        if (value == null || value.expired()) {
            return -1L;
        }
        return Math.max(0L, Duration.between(Instant.now(), value.expiresAt()).getSeconds());
    }

    public void delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        tryRedis(() -> {
            redisTemplate.delete(java.util.List.of(keys));
            return true;
        });
        for (String key : keys) {
            values.remove(key);
            counters.remove(key);
        }
    }

    private boolean tryRedis(RedisOperation<Boolean> operation) {
        return redis(operation).success();
    }

    private <T> RedisResult<T> redis(RedisOperation<T> operation) {
        if (!isRedisActive()) {
            return RedisResult.failed();
        }
        try {
            return RedisResult.success(operation.run());
        } catch (RuntimeException exception) {
            log.warn("Redis unavailable, falling back to local memory state", exception);
            return RedisResult.failed();
        }
    }

    private Instant expiresAt(Duration ttl) {
        return Instant.now().plus(ttl);
    }

    private void cleanupCounters() {
        if (counters.size() < 10000) {
            return;
        }
        Instant now = Instant.now();
        for (Map.Entry<String, MemoryCounter> entry : counters.entrySet()) {
            if (entry.getValue().expiresAt().isBefore(now)) {
                counters.remove(entry.getKey());
            }
        }
    }

    private interface RedisOperation<T> {
        T run();
    }

    private record RedisResult<T>(boolean success, T value) {
        static <T> RedisResult<T> success(T value) {
            return new RedisResult<>(true, value);
        }

        static <T> RedisResult<T> failed() {
            return new RedisResult<>(false, null);
        }
    }

    private record MemoryValue(String value, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private record MemoryCounter(AtomicLong value, Instant expiresAt) {
        MemoryCounter(long initialValue, Instant expiresAt) {
            this(new AtomicLong(initialValue), expiresAt);
        }

        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
