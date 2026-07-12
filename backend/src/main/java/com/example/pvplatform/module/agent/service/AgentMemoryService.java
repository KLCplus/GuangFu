package com.example.pvplatform.module.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.AgentMemoryDTO;
import com.example.pvplatform.module.agent.entity.AgentMemoryDO;
import com.example.pvplatform.module.agent.mapper.AgentMemoryMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AgentMemoryService {
    private static final Set<String> ALLOWED_MEMORY_TYPES = Set.of(
        "user_preference",
        "default_station",
        "report_format",
        "frequent_metric",
        "recent_context",
        "station_focus",
        "ops_preference"
    );
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
        "api_key",
        "apikey",
        "token",
        "password",
        "secret",
        "private_key",
        "credential",
        "密钥",
        "密码",
        "令牌"
    );

    private final AgentMemoryMapper memoryMapper;
    private final AgentJsonService jsonService;

    public AgentMemoryService(AgentMemoryMapper memoryMapper, AgentJsonService jsonService) {
        this.memoryMapper = memoryMapper;
        this.jsonService = jsonService;
    }

    public AgentMemoryDTO write(String memoryType, String memoryKey, Object value,
                                String sourceMessage, Double confidence, Boolean enabled) {
        Long userId = SecurityUtils.requireCurrentUserId();
        String normalizedType = normalizeType(memoryType);
        String normalizedKey = normalizeKey(normalizedType, memoryKey);
        String valueJson = jsonService.json(value == null ? java.util.Map.of() : value);
        String source = sourceMessage == null ? "" : sourceMessage.trim();
        ensureSafe(valueJson + " " + source);

        AgentMemoryDO existing = memoryMapper.selectOne(Wrappers.<AgentMemoryDO>lambdaQuery()
            .eq(AgentMemoryDO::getUserId, userId)
            .eq(AgentMemoryDO::getMemoryType, normalizedType)
            .eq(AgentMemoryDO::getMemoryKey, normalizedKey)
            .last("LIMIT 1"));

        LocalDateTime now = LocalDateTime.now();
        AgentMemoryDO row = existing == null ? new AgentMemoryDO() : existing;
        row.setUserId(userId);
        row.setMemoryType(normalizedType);
        row.setMemoryKey(normalizedKey);
        row.setValueJson(valueJson);
        row.setSourceMessage(source.length() > 1000 ? source.substring(0, 1000) : source);
        row.setConfidence(normalizeConfidence(confidence));
        row.setEnabled(enabled == null || enabled);
        row.setUpdatedAt(now);
        if (existing == null) {
            row.setCreatedAt(now);
            memoryMapper.insert(row);
        } else {
            memoryMapper.updateById(row);
        }
        return toDTO(row);
    }

    public List<AgentMemoryDTO> list(String memoryType, Boolean enabledOnly, Integer limit) {
        Long userId = SecurityUtils.requireCurrentUserId();
        int max = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        return memoryMapper.selectList(Wrappers.<AgentMemoryDO>lambdaQuery()
                .eq(AgentMemoryDO::getUserId, userId)
                .eq(memoryType != null && !memoryType.isBlank(), AgentMemoryDO::getMemoryType, memoryType)
                .eq(Boolean.TRUE.equals(enabledOnly), AgentMemoryDO::getEnabled, true)
                .orderByDesc(AgentMemoryDO::getUpdatedAt)
                .last("LIMIT " + max))
            .stream()
            .map(this::toDTO)
            .toList();
    }

    private String normalizeType(String memoryType) {
        String value = memoryType == null ? "" : memoryType.trim();
        if (!ALLOWED_MEMORY_TYPES.contains(value)) {
            throw new BusinessException(400, "不允许的 Agent memory type");
        }
        return value;
    }

    private String normalizeKey(String memoryType, String memoryKey) {
        if (memoryKey != null && !memoryKey.isBlank()) {
            return memoryKey.trim();
        }
        if ("default_station".equals(memoryType)) return "default";
        if ("report_format".equals(memoryType)) return "default";
        return "general";
    }

    private BigDecimal normalizeConfidence(Double confidence) {
        double value = confidence == null ? 0.5 : confidence;
        if (value < 0 || value > 1) {
            throw new BusinessException(400, "Agent memory confidence 必须在 0 到 1 之间");
        }
        return BigDecimal.valueOf(value);
    }

    private void ensureSafe(String blob) {
        String lower = blob == null ? "" : blob.toLowerCase();
        if (SENSITIVE_MARKERS.stream().anyMatch(lower::contains)) {
            throw new BusinessException(400, "敏感信息不允许写入长期记忆");
        }
    }

    private AgentMemoryDTO toDTO(AgentMemoryDO row) {
        return new AgentMemoryDTO(
            row.getMemoryId(),
            row.getUserId(),
            row.getMemoryType(),
            row.getMemoryKey(),
            jsonService.value(row.getValueJson()),
            row.getSourceMessage(),
            row.getConfidence(),
            row.getEnabled(),
            row.getCreatedAt(),
            row.getUpdatedAt()
        );
    }
}
