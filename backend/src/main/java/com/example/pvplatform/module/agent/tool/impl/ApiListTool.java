package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.vo.ApiKeyVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ApiListTool extends AbstractAgentTool {
    private final ApiKeyService apiKeyService;
    public ApiListTool(ApiKeyService apiKeyService) { this.apiKeyService = apiKeyService; }
    public String name() { return "api.list"; }
    public String displayName() { return "查询 API Key 列表"; }
    public ToolCategory category() { return ToolCategory.API; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户 API Key 列表，只返回安全摘要和前缀。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            List<Map<String, Object>> keys = apiKeyService.listOwn().stream().map(this::safe).toList();
            return ToolExecutionResult.success(keys, "已获取 " + keys.size() + " 个 API Key");
        });
    }
    private Map<String, Object> safe(ApiKeyVO key) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("apiKeyId", key.apiKeyId());
        item.put("keyName", key.keyName());
        item.put("prefix", key.apiKeyPrefix());
        item.put("status", key.status());
        item.put("rateLimitPerMinute", key.rateLimitPerMinute());
        item.put("dailyQuota", key.dailyQuota());
        item.put("expireTime", key.expireTime());
        item.put("lastUsedAt", key.lastUsedAt());
        item.put("createdAt", key.createdAt());
        return item;
    }
}
