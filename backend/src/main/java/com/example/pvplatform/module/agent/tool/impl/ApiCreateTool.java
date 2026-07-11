package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.dto.ApiKeyApplyRequest;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.vo.ApiKeyVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ApiCreateTool extends AbstractAgentTool {
    private final ApiKeyService apiKeyService;
    public ApiCreateTool(ApiKeyService apiKeyService) { this.apiKeyService = apiKeyService; }
    public String name() { return "api.create"; }
    public String displayName() { return "创建 API Key"; }
    public ToolCategory category() { return ToolCategory.API; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.WRITE; }
    public String description() { return "为当前用户创建 API Key。需要确认，结果不会返回完整 Key，只返回安全摘要。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"keyName"}, "properties", Map.of("keyName", Map.of("type", "string"), "expireDays", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            String keyName = stringArg(arguments, "keyName", null);
            if (keyName == null || keyName.isBlank()) throw new com.example.pvplatform.common.exception.BusinessException(400, "缺少参数: keyName");
            Long days = longArg(arguments, "expireDays", false);
            ApiKeyVO key = apiKeyService.create(new ApiKeyApplyRequest(keyName, days == null ? null : days.intValue()));
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("apiKeyId", key.apiKeyId());
            safe.put("keyName", key.keyName());
            safe.put("prefix", key.apiKeyPrefix());
            safe.put("status", key.status());
            safe.put("expireTime", key.expireTime());
            safe.put("createdAt", key.createdAt());
            return ToolExecutionResult.success(safe, "API Key 已创建。完整 Key 仅由原 API 接口一次性返回，Agent 调试面板不展示密钥。");
        });
    }
}
