package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.service.ApiCallLogService;
import com.example.pvplatform.module.openapi.vo.ApiCallLogVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        return guard(() -> {
            PageResult<ApiCallLogVO> logs = callLogService.ownLogs(1, 20);
            List<ApiCallLogVO> records = logs.records();
            long success = records.stream().filter(row -> "SUCCESS".equalsIgnoreCase(row.status()) || (row.statusCode() != null && row.statusCode() < 400)).count();
            long failed = records.size() - success;
            double avgCost = records.stream().map(ApiCallLogVO::costTimeMs).filter(Objects::nonNull).mapToLong(Long::longValue).average().orElse(0);
            List<String> highlights = new ArrayList<>();
            highlights.add("最近调用：" + records.size() + " 次");
            highlights.add("成功/失败：" + success + "/" + failed);
            if (avgCost > 0) highlights.add("平均延迟：" + Math.round(avgCost) + " ms");
            records.stream().filter(row -> row.errorMessage() != null && !row.errorMessage().isBlank()).findFirst()
                .ifPresent(row -> highlights.add("最近错误：" + row.errorMessage()));
            return ToolExecutionResult.success(displayName(), logs,
                records.isEmpty() ? "最近没有 API 调用记录" : "已获取最近 API 调用情况，成功 " + success + " 次，失败 " + failed + " 次",
                highlights);
        });
    }
}
