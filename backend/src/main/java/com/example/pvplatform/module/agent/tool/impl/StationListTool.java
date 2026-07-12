package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class StationListTool extends AbstractAgentTool {
    private final StationService stationService;
    public StationListTool(StationService stationService) { this.stationService = stationService; }
    public String name() { return "station.list"; }
    public String displayName() { return "查询电站列表"; }
    public ToolCategory category() { return ToolCategory.STATION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户可访问的电站列表。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            PageResult<PowerStation> page = stationService.list(1, 50, null, null);
            List<String> highlights = new ArrayList<>();
            highlights.add("可访问电站：" + page.records().size() + " 个");
            page.records().stream().limit(5).forEach(station -> highlights.add(station.stationId() + " - " + value(station.stationName(), "未命名电站") + status(station.status())));
            return ToolExecutionResult.success(displayName(), page,
                page.records().isEmpty() ? "当前没有可访问电站" : "已获取当前用户可访问电站列表",
                highlights);
        });
    }

    private String status(String status) {
        return status == null || status.isBlank() ? "" : "（" + status + "）";
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
