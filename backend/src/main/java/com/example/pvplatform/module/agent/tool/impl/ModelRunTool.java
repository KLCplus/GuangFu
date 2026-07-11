package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.prediction.dto.PredictionRequest;
import com.example.pvplatform.module.prediction.service.PredictionService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ModelRunTool extends AbstractAgentTool {
    private final PredictionService predictionService;
    public ModelRunTool(PredictionService predictionService) { this.predictionService = predictionService; }
    public String name() { return "model.run"; }
    public String displayName() { return "运行预测模型"; }
    public ToolCategory category() { return ToolCategory.MODEL; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.COST; }
    public String description() { return "创建并执行预测任务。需要确认，numericValues 和 inputImages 都必须包含 30 帧。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId", "modelId", "numericValues", "inputImages"}, "properties", Map.of("stationId", Map.of("type", "number"), "modelId", Map.of("type", "number"), "numericValues", Map.of("type", "array", "minItems", 30, "maxItems", 30), "inputImages", Map.of("type", "array", "minItems", 30, "maxItems", 30))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", true);
            Long modelId = longArg(arguments, "modelId", true);
            List<PredictionRequest.NumericValue> numericValues = new ArrayList<>();
            for (Object item : listArg(arguments, "numericValues", true)) {
                Map<String, Object> row = asMap(item, "numericValues");
                Object value = row.get("value");
                if (!(value instanceof Number number)) throw new BusinessException(400, "numericValues.value 必须是数字");
                numericValues.add(new PredictionRequest.NumericValue(String.valueOf(row.get("time")), number.doubleValue()));
            }
            List<ModelPredictRequest.ImageFrame> images = new ArrayList<>();
            for (Object item : listArg(arguments, "inputImages", true)) {
                Map<String, Object> row = asMap(item, "inputImages");
                images.add(new ModelPredictRequest.ImageFrame(String.valueOf(row.get("time")), String.valueOf(row.get("image"))));
            }
            if (numericValues.size() != 30 || images.size() != 30) throw new BusinessException(400, "numericValues 和 inputImages 都必须包含 30 帧");
            var result = predictionService.create(new PredictionRequest(stationId, modelId, "MANUAL_MULTIMODAL", null, null, numericValues, images));
            return ToolExecutionResult.success(result, "预测任务已创建并执行，taskId=" + result.taskId());
        });
    }
    private Map<String, Object> asMap(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        throw new BusinessException(400, key + " 元素必须是对象");
    }
}
