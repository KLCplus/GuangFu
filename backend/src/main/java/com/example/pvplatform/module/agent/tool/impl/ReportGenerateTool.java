package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.service.AnalysisService;
import com.example.pvplatform.module.analysis.vo.AnalysisReportVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ReportGenerateTool extends AbstractAgentTool {
    private final AnalysisService analysisService;
    public ReportGenerateTool(AnalysisService analysisService) { this.analysisService = analysisService; }
    public String name() { return "report.generate"; }
    public String displayName() { return "生成综合分析报告"; }
    public ToolCategory category() { return ToolCategory.REPORT; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.WRITE; }
    public String description() { return "生成综合分析报告并写入数据库。该工具必须经过用户确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of(
        "stationId", Map.of("type", "number"), "taskId", Map.of("type", "number"), "title", Map.of("type", "string"),
        "includeWeather", Map.of("type", "boolean"), "includePrediction", Map.of("type", "boolean")));
    }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", true);
            Long taskId = longArg(arguments, "taskId", false);
            String title = stringArg(arguments, "title", stationId + "号电站综合分析报告");
            boolean includeWeather = boolArg(arguments, "includeWeather", true);
            boolean includePrediction = boolArg(arguments, "includePrediction", true);
            AnalysisRequest request = new AnalysisRequest(stationId, taskId, title, "由 Agent 根据用户确认生成", includeWeather, includePrediction);
            AnalysisReportVO report = analysisService.report(request);
            Map<String, Object> safeReport = new LinkedHashMap<>();
            safeReport.put("reportId", report.reportId());
            safeReport.put("id", report.id());
            safeReport.put("stationId", report.stationId());
            safeReport.put("taskId", report.taskId());
            safeReport.put("title", report.title());
            safeReport.put("summary", report.summary());
            safeReport.put("riskLevel", report.riskLevel());
            safeReport.put("status", report.status());
            safeReport.put("includeWeather", report.includeWeather());
            safeReport.put("includePrediction", report.includePrediction());
            safeReport.put("modelName", report.modelName());
            safeReport.put("createdAt", report.createdAt());
            return ToolExecutionResult.success(safeReport, "综合分析报告已生成并保存，reportId=" + report.reportId());
        });
    }
}
