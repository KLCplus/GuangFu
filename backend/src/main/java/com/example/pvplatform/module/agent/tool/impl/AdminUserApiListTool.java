package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.vo.ApiKeyVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdminUserApiListTool extends AbstractAgentTool {
    private final ApiKeyService apiKeyService;
    public AdminUserApiListTool(ApiKeyService apiKeyService) { this.apiKeyService = apiKeyService; }
    public String name() { return "admin.userApi.list"; }
    public String displayName() { return "管理端查询 API Key"; }
    public ToolCategory category() { return ToolCategory.ADMIN; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.ADMIN; }
    public String description() { return "管理员查询所有用户 API Key 安全摘要。需要确认且不会返回完整 Key。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            requireAdmin(context);
            List<Map<String, Object>> keys = apiKeyService.adminList().stream().map(this::safe).toList();
            return ToolExecutionResult.success(keys, "已获取管理端 API Key 列表，共 " + keys.size() + " 条");
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
