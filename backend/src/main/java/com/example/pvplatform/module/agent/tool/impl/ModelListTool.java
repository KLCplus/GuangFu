package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.model.service.ModelService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ModelListTool extends AbstractAgentTool {
    private final ModelService modelService;
    public ModelListTool(ModelService modelService) { this.modelService = modelService; }
    public String name() { return "model.list"; }
    public String displayName() { return "查询模型列表"; }
    public ToolCategory category() { return ToolCategory.MODEL; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询模型广场可展示模型，可按 category/modelType 过滤。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("category", Map.of("type", "string"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            String category = stringArg(arguments, "category", null);
            return ToolExecutionResult.success(modelService.list(category), "已获取模型列表");
        });
    }
}
