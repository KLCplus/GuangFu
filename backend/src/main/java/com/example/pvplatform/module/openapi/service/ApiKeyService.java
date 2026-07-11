package com.example.pvplatform.module.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.openapi.dto.ApiKeyApplyRequest;
import com.example.pvplatform.module.openapi.vo.ApiKeyVO;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import com.example.pvplatform.persistence.mapper.ApiKeyMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class ApiKeyService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private final ApiKeyMapper apiKeyMapper;

    public ApiKeyService(ApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    public ApiKeyVO create(ApiKeyApplyRequest request) {
        String fullKey = generateRawKey();
        String prefix = fullKey.substring(0, fullKey.indexOf('_', 3));
        LocalDateTime now = LocalDateTime.now();
        ApiKeyDO row = new ApiKeyDO();
        row.setUserId(SecurityUtils.requireCurrentUserId());
        row.setKeyName(request.keyName().trim());
        row.setApiKeyPrefix(prefix);
        row.setApiKeyHash(hash(fullKey));
        row.setStatus("ACTIVE");
        row.setRateLimitPerMinute(60);
        row.setDailyQuota(1000);
        row.setExpireTime(request.expireDays() == null ? null : now.plusDays(request.expireDays()));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        apiKeyMapper.insert(row);
        return toVO(row, fullKey);
    }

    public ApiKeyVO resetOwn(Long id) {
        ApiKeyDO row = apiKeyMapper.selectOne(Wrappers.<ApiKeyDO>lambdaQuery()
            .eq(ApiKeyDO::getApiKeyId, id)
            .eq(ApiKeyDO::getUserId, SecurityUtils.requireCurrentUserId()).last("LIMIT 1"));
        if (row == null) {
            throw new BusinessException(404, "API Key 不存在");
        }
        String fullKey = generateRawKey();
        String prefix = fullKey.substring(0, fullKey.indexOf('_', 3));
        row.setApiKeyPrefix(prefix);
        row.setApiKeyHash(hash(fullKey));
        row.setStatus("ACTIVE");
        row.setUpdatedAt(LocalDateTime.now());
        apiKeyMapper.updateById(row);
        return toVO(row, fullKey);
    }

    private String generateRawKey() {
        byte[] prefixBytes = new byte[4];
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(prefixBytes);
        RANDOM.nextBytes(secretBytes);
        String prefix = "pv_" + HexFormat.of().formatHex(prefixBytes);
        return prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    }

    public List<ApiKeyVO> listOwn() {
        return apiKeyMapper.selectList(Wrappers.<ApiKeyDO>lambdaQuery()
                .eq(ApiKeyDO::getUserId, SecurityUtils.requireCurrentUserId())
                .orderByDesc(ApiKeyDO::getCreatedAt)).stream()
            .map(row -> toVO(row, null)).toList();
    }

    public void updateOwnStatus(Long id, String status) {
        updateStatus(id, status, SecurityUtils.requireCurrentUserId());
    }

    public ApiKeyVO updateOwnName(Long id, String keyName) {
        ApiKeyDO row = apiKeyMapper.selectOne(Wrappers.<ApiKeyDO>lambdaQuery()
            .eq(ApiKeyDO::getApiKeyId, id)
            .eq(ApiKeyDO::getUserId, SecurityUtils.requireCurrentUserId()).last("LIMIT 1"));
        if (row == null) {
            throw new BusinessException(404, "API Key 不存在");
        }
        row.setKeyName(keyName.trim());
        row.setUpdatedAt(LocalDateTime.now());
        apiKeyMapper.updateById(row);
        return toVO(row, null);
    }

    public void updateAnyStatus(Long id, String status) {
        updateStatus(id, status, null);
    }

    public void deleteOwn(Long id) {
        int changed = apiKeyMapper.delete(Wrappers.<ApiKeyDO>lambdaQuery()
            .eq(ApiKeyDO::getApiKeyId, id)
            .eq(ApiKeyDO::getUserId, SecurityUtils.requireCurrentUserId()));
        if (changed == 0) {
            throw new BusinessException(404, "API Key 不存在");
        }
    }

    public ApiKeyDO authenticate(String rawKey) {
        if (rawKey == null || !rawKey.matches("^pv_[0-9a-f]{8}_[A-Za-z0-9_-]{43}$")) {
            throw new BusinessException(401, "API Key 无效");
        }
        String prefix = rawKey.substring(0, rawKey.indexOf('_', 3));
        ApiKeyDO row = apiKeyMapper.selectOne(Wrappers.<ApiKeyDO>lambdaQuery()
            .eq(ApiKeyDO::getApiKeyPrefix, prefix)
            .eq(ApiKeyDO::getApiKeyHash, hash(rawKey)).last("LIMIT 1"));
        if (row == null) {
            throw new BusinessException(401, "API Key 无效");
        }
        if (!"ACTIVE".equals(row.getStatus())) {
            throw new BusinessException(403, "API Key 已禁用或过期");
        }
        if (row.getExpireTime() != null && !row.getExpireTime().isAfter(LocalDateTime.now())) {
            row.setStatus("EXPIRED");
            row.setUpdatedAt(LocalDateTime.now());
            apiKeyMapper.updateById(row);
            throw new BusinessException(403, "API Key 已禁用或过期");
        }
        row.setLastUsedAt(LocalDateTime.now());
        apiKeyMapper.updateById(row);
        return row;
    }

    public List<ApiKeyVO> adminList() {
        return apiKeyMapper.selectList(Wrappers.<ApiKeyDO>lambdaQuery()
                .orderByDesc(ApiKeyDO::getCreatedAt)).stream()
            .map(row -> toVO(row, null)).toList();
    }

    private void updateStatus(Long id, String status, Long ownerId) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException(400, "API Key 状态不合法");
        }
        ApiKeyDO update = new ApiKeyDO();
        update.setStatus(status);
        update.setUpdatedAt(LocalDateTime.now());
        var wrapper = Wrappers.<ApiKeyDO>lambdaUpdate().eq(ApiKeyDO::getApiKeyId, id)
            .eq(ownerId != null, ApiKeyDO::getUserId, ownerId);
        if (apiKeyMapper.update(update, wrapper) == 0) {
            throw new BusinessException(404, "API Key 不存在");
        }
    }

    private String hash(String rawKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private ApiKeyVO toVO(ApiKeyDO row, String oneTimeKey) {
        return new ApiKeyVO(row.getApiKeyId(), row.getKeyName(), oneTimeKey, row.getApiKeyPrefix(),
            row.getStatus(), row.getRateLimitPerMinute(), row.getDailyQuota(), row.getExpireTime(),
            row.getLastUsedAt(), row.getCreatedAt());
    }
}
