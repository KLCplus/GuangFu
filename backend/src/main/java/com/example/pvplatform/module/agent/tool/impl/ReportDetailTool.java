package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.analysis.service.AnalysisService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReportDetailTool extends AbstractAgentTool {
    private final AnalysisService analysisService;
    public ReportDetailTool(AnalysisService analysisService) { this.analysisService = analysisService; }
    public String name() { return "report.detail"; }
    public String displayName() { return "查询报告详情"; }
    public ToolCategory category() { return ToolCategory.REPORT; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按 reportId 查询综合分析报告详情。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"reportId"}, "properties", Map.of("reportId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long reportId = longArg(arguments, "reportId", true);
            return ToolExecutionResult.success(analysisService.detail(reportId), "已获取报告 " + reportId + " 的详情");
        });
    }
}
