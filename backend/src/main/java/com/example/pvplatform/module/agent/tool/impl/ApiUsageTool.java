package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.service.ApiCallLogService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApiUsageTool extends AbstractAgentTool {
    private final ApiCallLogService callLogService;
    public ApiUsageTool(ApiCallLogService callLogService) { this.callLogService = callLogService; }
    public String name() { return "api.usage"; }
    public String displayName() { return "查询 API 使用情况"; }
    public ToolCategory category() { return ToolCategory.API; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户最近 API 调用日志，不返回完整 API Key。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> ToolExecutionResult.success(callLogService.ownLogs(1, 20), "已获取最近 API 调用记录"));
    }
}
