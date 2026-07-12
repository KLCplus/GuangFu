package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.prediction.service.PredictionService;
import com.example.pvplatform.module.prediction.vo.PredictionDetailVO;
import com.example.pvplatform.module.prediction.vo.PredictionResultVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
            PredictionDetailVO task = predictionService.detail(taskId);
            List<PredictionResultVO> results = predictionService.results(taskId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task", task);
            data.put("results", results);
            List<String> highlights = new ArrayList<>();
            highlights.add("任务状态：" + value(task.status(), "未返回"));
            if (task.stationName() != null && !task.stationName().isBlank()) highlights.add("电站：" + task.stationName());
            if (task.modelName() != null && !task.modelName().isBlank()) highlights.add("模型：" + task.modelName());
            highlights.add("预测点数：" + results.size());
            PowerStats stats = stats(results);
            if (stats.available()) {
                highlights.add("预测功率范围：" + stats.min() + " - " + stats.max() + " kW");
                highlights.add("趋势判断：" + trend(results));
            }
            return ToolExecutionResult.success(displayName(), data,
                "已读取任务 " + taskId + " 的预测详情，" + (stats.available() ? "预测功率" + stats.min() + "-" + stats.max() + " kW" : "暂无预测结果点"),
                highlights);
        });
    }

    private PowerStats stats(List<PredictionResultVO> results) {
        List<BigDecimal> values = results.stream().map(PredictionResultVO::predictPowerKw).filter(v -> v != null).toList();
        if (values.isEmpty()) return new PowerStats(null, null);
        BigDecimal min = values.stream().min(Comparator.naturalOrder()).orElse(null);
        BigDecimal max = values.stream().max(Comparator.naturalOrder()).orElse(null);
        return new PowerStats(min, max);
    }

    private String trend(List<PredictionResultVO> results) {
        List<BigDecimal> values = results.stream().map(PredictionResultVO::predictPowerKw).filter(v -> v != null).toList();
        if (values.size() < 2) return "预测点不足，无法判断趋势";
        BigDecimal first = values.get(0);
        BigDecimal last = values.get(values.size() - 1);
        int compare = last.compareTo(first);
        if (compare > 0) return "末段预测高于起点，整体偏上行";
        if (compare < 0) return "末段预测低于起点，整体偏下行";
        return "首尾功率接近，整体较平稳";
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record PowerStats(BigDecimal min, BigDecimal max) {
        boolean available() { return min != null && max != null; }
    }
}
