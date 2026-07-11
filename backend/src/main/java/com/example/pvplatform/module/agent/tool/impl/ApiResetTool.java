package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.vo.ApiKeyVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ApiResetTool extends AbstractAgentTool {
    private final ApiKeyService apiKeyService;
    public ApiResetTool(ApiKeyService apiKeyService) { this.apiKeyService = apiKeyService; }
    public String name() { return "api.reset"; }
    public String displayName() { return "重置 API Key"; }
    public ToolCategory category() { return ToolCategory.API; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.DANGEROUS; }
    public String description() { return "重置当前用户指定 API Key。需要确认，结果不会返回完整 Key。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"apiKeyId"}, "properties", Map.of("apiKeyId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long id = longArg(arguments, "apiKeyId", true);
            ApiKeyVO key = apiKeyService.resetOwn(id);
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("apiKeyId", key.apiKeyId());
            safe.put("keyName", key.keyName());
            safe.put("prefix", key.apiKeyPrefix());
            safe.put("status", key.status());
            safe.put("updated", true);
            return ToolExecutionResult.success(safe, "API Key 已重置。完整 Key 不会在 Agent 调试信息中展示。");
        });
    }
}
