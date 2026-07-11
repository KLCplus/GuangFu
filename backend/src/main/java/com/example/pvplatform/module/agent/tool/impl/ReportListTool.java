package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.analysis.service.AnalysisService;
import com.example.pvplatform.module.analysis.vo.AnalysisReportListItemVO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReportListTool extends AbstractAgentTool {
    private final AnalysisService analysisService;
    public ReportListTool(AnalysisService analysisService) { this.analysisService = analysisService; }
    public String name() { return "report.list"; }
    public String displayName() { return "查询历史报告"; }
    public ToolCategory category() { return ToolCategory.REPORT; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户历史综合分析报告，可按 stationId 过滤。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", false);
            PageResult<AnalysisReportListItemVO> page = analysisService.history(1, 10, stationId);
            return ToolExecutionResult.success(page, "已获取 " + page.records().size() + " 份历史报告");
        });
    }
}
