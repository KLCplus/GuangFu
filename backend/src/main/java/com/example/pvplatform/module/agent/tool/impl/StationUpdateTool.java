package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.station.dto.StationRequest;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StationUpdateTool extends AbstractAgentTool {
    private final StationService stationService;
    public StationUpdateTool(StationService stationService) { this.stationService = stationService; }
    public String name() { return "station.update"; }
    public String displayName() { return "更新电站"; }
    public ToolCategory category() { return ToolCategory.STATION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.WRITE; }
    public String description() { return "管理员更新电站信息。需要确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"), "stationName", Map.of("type", "string"), "province", Map.of("type", "string"), "city", Map.of("type", "string"), "address", Map.of("type", "string"), "longitude", Map.of("type", "number"), "latitude", Map.of("type", "number"), "capacity", Map.of("type", "number"), "status", Map.of("type", "string"), "description", Map.of("type", "string"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            requireAdmin(context);
            Long id = longArg(arguments, "stationId", true);
            PowerStation current = stationService.detail(id);
            StationRequest request = new StationRequest(
                stringArg(arguments, "stationName", current.stationName()),
                stringArg(arguments, "province", current.province()),
                stringArg(arguments, "city", current.city()),
                stringArg(arguments, "address", current.address()),
                doubleArg(arguments, "longitude", current.longitude()),
                doubleArg(arguments, "latitude", current.latitude()),
                doubleArg(arguments, "capacity", current.capacity()),
                stringArg(arguments, "status", current.status()),
                stringArg(arguments, "description", current.description())
            );
            stationService.update(id, request);
            return ToolExecutionResult.success(stationService.detail(id), "电站已更新，stationId=" + id);
        });
    }
    private Double doubleArg(Map<String, Object> arguments, String key, Double fallback) {
        Object value = arguments.get(key);
        if (value == null) return fallback;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException e) { throw new com.example.pvplatform.common.exception.BusinessException(400, "参数不是有效数字: " + key); }
    }
}
