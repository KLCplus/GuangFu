package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.module.station.service.StationService;
import org.springframework.stereotype.Component;

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
            return ToolExecutionResult.success(station, "已获取电站 " + station.stationName() + " 的详情");
        });
    }
}
