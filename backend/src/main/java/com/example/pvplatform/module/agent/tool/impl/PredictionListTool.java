package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.prediction.service.PredictionService;
import com.example.pvplatform.module.prediction.vo.PredictionTaskVO;
import com.example.pvplatform.module.station.service.StationPermissionService;
import org.springframework.stereotype.Component;

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
            return ToolExecutionResult.success(page, "已获取 " + page.records().size() + " 条预测任务");
        });
    }
}
