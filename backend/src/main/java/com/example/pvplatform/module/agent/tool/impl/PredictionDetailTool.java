package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.prediction.service.PredictionService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PredictionDetailTool extends AbstractAgentTool {
    private final PredictionService predictionService;
    public PredictionDetailTool(PredictionService predictionService) { this.predictionService = predictionService; }
    public String name() { return "prediction.detail"; }
    public String displayName() { return "查询预测详情"; }
    public ToolCategory category() { return ToolCategory.PREDICTION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按 taskId 查询预测任务详情和预测结果。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"taskId"}, "properties", Map.of("taskId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long taskId = longArg(arguments, "taskId", true);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task", predictionService.detail(taskId));
            data.put("results", predictionService.results(taskId));
            return ToolExecutionResult.success(data, "已获取预测任务 " + taskId + " 的详情与结果");
        });
    }
}
