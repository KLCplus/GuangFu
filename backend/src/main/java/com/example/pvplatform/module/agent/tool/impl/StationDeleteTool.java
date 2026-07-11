package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.station.service.StationService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StationDeleteTool extends AbstractAgentTool {
    private final StationService stationService;
    public StationDeleteTool(StationService stationService) { this.stationService = stationService; }
    public String name() { return "station.delete"; }
    public String displayName() { return "删除电站"; }
    public ToolCategory category() { return ToolCategory.STATION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.DANGEROUS; }
    public String description() { return "管理员删除电站。需要确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            requireAdmin(context);
            Long id = longArg(arguments, "stationId", true);
            stationService.delete(id);
            return ToolExecutionResult.success(Map.of("stationId", id, "deleted", true), "电站已删除");
        });
    }
}
