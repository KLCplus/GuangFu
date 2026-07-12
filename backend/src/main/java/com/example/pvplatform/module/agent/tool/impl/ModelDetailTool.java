package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.model.service.ModelService;
import com.example.pvplatform.module.model.vo.ModelDetailVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ModelDetailTool extends AbstractAgentTool {
    private final ModelService modelService;
    public ModelDetailTool(ModelService modelService) { this.modelService = modelService; }
    public String name() { return "model.detail"; }
    public String displayName() { return "查询模型详情"; }
    public ToolCategory category() { return ToolCategory.MODEL; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按 modelId 查询模型详情、输入输出 schema 和指标。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"modelId"}, "properties", Map.of("modelId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long modelId = longArg(arguments, "modelId", true);
            ModelDetailVO model = modelService.detail(modelId);
            List<String> highlights = new ArrayList<>();
            highlights.add("模型：" + model.modelName());
            if (model.modelType() != null && !model.modelType().isBlank()) highlights.add("类型：" + model.modelType());
            if (model.status() != null && !model.status().isBlank()) highlights.add("状态：" + model.status());
            if (model.outputSteps() != null && model.outputStepMinutes() != null) highlights.add("预测范围：" + model.outputSteps() + " 步，每步 " + model.outputStepMinutes() + " 分钟");
            if (model.metrics() != null && !model.metrics().isEmpty()) highlights.add("指标数量：" + model.metrics().size());
            return ToolExecutionResult.success(displayName(), model,
                "已获取模型 " + model.modelName() + " 的详情",
                highlights);
        });
    }
}
