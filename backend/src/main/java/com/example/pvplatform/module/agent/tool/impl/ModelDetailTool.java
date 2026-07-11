package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.model.service.ModelService;
import org.springframework.stereotype.Component;

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
            return ToolExecutionResult.success(modelService.detail(modelId), "已获取模型详情");
        });
    }
}
