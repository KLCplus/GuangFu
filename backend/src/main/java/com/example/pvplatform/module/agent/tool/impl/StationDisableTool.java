package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.station.dto.StationRequest;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StationDisableTool extends AbstractAgentTool {
    private final StationService stationService;
    public StationDisableTool(StationService stationService) { this.stationService = stationService; }
    public String name() { return "station.disable"; }
    public String displayName() { return "停用电站"; }
    public ToolCategory category() { return ToolCategory.STATION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.WRITE; }
    public String description() { return "管理员将电站状态更新为 OFFLINE。需要确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            requireAdmin(context);
            Long id = longArg(arguments, "stationId", true);
            PowerStation current = stationService.detail(id);
            stationService.update(id, new StationRequest(current.stationName(), current.province(), current.city(), current.address(), current.longitude(), current.latitude(), current.capacity(), "OFFLINE", current.description()));
            return ToolExecutionResult.success(stationService.detail(id), "电站已停用，stationId=" + id);
        });
    }
}
