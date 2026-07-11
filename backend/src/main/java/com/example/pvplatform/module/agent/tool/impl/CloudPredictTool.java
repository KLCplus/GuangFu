package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.cloud.dto.CloudForecastRequest;
import com.example.pvplatform.module.cloud.service.CloudForecastService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CloudPredictTool extends AbstractAgentTool {
    private final CloudForecastService cloudForecastService;
    public CloudPredictTool(CloudForecastService cloudForecastService) { this.cloudForecastService = cloudForecastService; }
    public String name() { return "cloud.predict"; }
    public String displayName() { return "运行云图预测"; }
    public ToolCategory category() { return ToolCategory.CLOUD; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.COST; }
    public String description() { return "调用云图模型服务进行预测。需要确认，inputImages 必须为 10 张 base64 图片。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"modelName", "inputImages"}, "properties", Map.of("modelName", Map.of("type", "string"), "inputImages", Map.of("type", "array", "minItems", 10, "maxItems", 10))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            String modelName = stringArg(arguments, "modelName", null);
            if (modelName == null || modelName.isBlank()) throw new com.example.pvplatform.common.exception.BusinessException(400, "缺少参数: modelName");
            List<String> images = listArg(arguments, "inputImages", true).stream().map(String::valueOf).toList();
            if (images.size() != 10) throw new com.example.pvplatform.common.exception.BusinessException(400, "inputImages 必须包含 10 张图片");
            return ToolExecutionResult.success(cloudForecastService.predict(new CloudForecastRequest(modelName, images)), "云图预测已完成");
        });
    }
}
