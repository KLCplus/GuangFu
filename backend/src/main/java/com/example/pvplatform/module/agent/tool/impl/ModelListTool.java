package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.model.service.ModelService;
import com.example.pvplatform.module.model.vo.ModelListItemVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
            List<ModelListItemVO> models = modelService.list(category);
            List<String> highlights = new ArrayList<>();
            highlights.add("可用模型：" + models.size() + " 个");
            String categories = models.stream().map(ModelListItemVO::modelType).filter(Objects::nonNull).distinct().collect(Collectors.joining("、"));
            if (!categories.isBlank()) highlights.add("模型类别：" + categories);
            models.stream().filter(model -> Boolean.TRUE.equals(model.isFeatured())).findFirst()
                .ifPresent(model -> highlights.add("推荐模型：" + model.modelName()));
            models.stream().limit(3).forEach(model -> highlights.add(model.modelName() + status(model.status())));
            return ToolExecutionResult.success(displayName(), models,
                models.isEmpty() ? "没有查询到可用模型" : "已获取模型列表，共 " + models.size() + " 个",
                highlights);
        });
    }

    private String status(String status) {
        return status == null || status.isBlank() ? "" : "（" + status + "）";
    }
}
