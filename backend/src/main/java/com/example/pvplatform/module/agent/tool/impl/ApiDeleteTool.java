package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApiDeleteTool extends AbstractAgentTool {
    private final ApiKeyService apiKeyService;
    public ApiDeleteTool(ApiKeyService apiKeyService) { this.apiKeyService = apiKeyService; }
    public String name() { return "api.delete"; }
    public String displayName() { return "删除 API Key"; }
    public ToolCategory category() { return ToolCategory.API; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.DANGEROUS; }
    public String description() { return "删除当前用户指定 API Key。需要确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"apiKeyId"}, "properties", Map.of("apiKeyId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long id = longArg(arguments, "apiKeyId", true);
            apiKeyService.deleteOwn(id);
            return ToolExecutionResult.success(Map.of("apiKeyId", id, "deleted", true), "API Key 已删除");
        });
    }
}
