package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.module.station.service.StationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class StationDetailTool extends AbstractAgentTool {
    private final StationService stationService;
    private final StationPermissionService permissionService;
    public StationDetailTool(StationService stationService, StationPermissionService permissionService) {
        this.stationService = stationService; this.permissionService = permissionService;
    }
    public String name() { return "station.detail"; }
    public String displayName() { return "查询电站详情"; }
    public ToolCategory category() { return ToolCategory.STATION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按 stationId 查询当前用户有权限访问的电站详情。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", true);
            permissionService.requireView(stationId);
            PowerStation station = stationService.detail(stationId);
            List<String> highlights = new ArrayList<>();
            highlights.add("电站：" + value(station.stationName(), station.stationId() + "号电站"));
            if (station.capacity() != null) highlights.add("装机容量：" + station.capacity() + " MW");
            String location = location(station);
            if (!location.isBlank()) highlights.add("位置：" + location);
            if (station.status() != null && !station.status().isBlank()) highlights.add("状态：" + station.status());
            return ToolExecutionResult.success(displayName(), station,
                "已获取 " + value(station.stationName(), station.stationId() + "号电站") + " 基础信息",
                highlights);
        });
    }

    private String location(PowerStation station) {
        StringBuilder builder = new StringBuilder();
        if (station.province() != null && !station.province().isBlank()) builder.append(station.province());
        if (station.city() != null && !station.city().isBlank()) builder.append(builder.length() == 0 ? "" : " ").append(station.city());
        if (station.address() != null && !station.address().isBlank()) builder.append(builder.length() == 0 ? "" : " ").append(station.address());
        return builder.toString();
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
