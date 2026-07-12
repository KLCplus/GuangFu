package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.prediction.service.PredictionService;
import com.example.pvplatform.module.prediction.vo.PredictionTaskVO;
import com.example.pvplatform.module.station.service.StationPermissionService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PredictionListTool extends AbstractAgentTool {
    private final PredictionService predictionService;
    private final StationPermissionService permissionService;
    public PredictionListTool(PredictionService predictionService, StationPermissionService permissionService) {
        this.predictionService = predictionService; this.permissionService = permissionService;
    }
    public String name() { return "prediction.list"; }
    public String displayName() { return "查询预测任务列表"; }
    public ToolCategory category() { return ToolCategory.PREDICTION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户预测任务列表，可按 stationId 过滤。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", false);
            if (stationId != null) permissionService.requireView(stationId);
            PageResult<PredictionTaskVO> page = predictionService.history(1, 10, stationId, null, null);
            List<String> highlights = new ArrayList<>();
            highlights.add("最近任务：" + page.records().size() + " 条");
            long failed = page.records().stream().filter(task -> task.status() != null && task.status().toUpperCase().contains("FAIL")).count();
            if (failed > 0) highlights.add("失败任务：" + failed + " 条");
            page.records().stream().limit(3).forEach(task -> highlights.add("任务 " + task.taskId() + "：" + value(task.status(), "未知状态") + model(task.modelName())));
            return ToolExecutionResult.success(displayName(), page,
                page.records().isEmpty() ? "没有查询到预测任务" : "已获取最近预测任务列表",
                highlights);
        });
    }

    private String model(String modelName) {
        return modelName == null || modelName.isBlank() ? "" : "，模型 " + modelName;
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
