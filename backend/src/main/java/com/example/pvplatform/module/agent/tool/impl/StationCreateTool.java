package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.station.dto.StationRequest;
import com.example.pvplatform.module.station.service.StationService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StationCreateTool extends AbstractAgentTool {
    private final StationService stationService;
    public StationCreateTool(StationService stationService) { this.stationService = stationService; }
    public String name() { return "station.create"; }
    public String displayName() { return "创建电站"; }
    public ToolCategory category() { return ToolCategory.STATION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.WRITE; }
    public String description() { return "管理员创建电站。需要确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationName"}, "properties", Map.of("stationName", Map.of("type", "string"), "province", Map.of("type", "string"), "city", Map.of("type", "string"), "address", Map.of("type", "string"), "longitude", Map.of("type", "number"), "latitude", Map.of("type", "number"), "capacity", Map.of("type", "number"), "status", Map.of("type", "string"), "description", Map.of("type", "string"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            requireAdmin(context);
            String name = stringArg(arguments, "stationName", null);
            if (name == null || name.isBlank()) throw new BusinessException(400, "缺少参数: stationName");
            Long id = stationService.create(new StationRequest(stringArg(arguments, "stationName", null), stringArg(arguments, "province", null), stringArg(arguments, "city", null), stringArg(arguments, "address", null), doubleArg(arguments, "longitude"), doubleArg(arguments, "latitude"), doubleArg(arguments, "capacity"), stringArg(arguments, "status", null), stringArg(arguments, "description", null)));
            return ToolExecutionResult.success(stationService.detail(id), "电站已创建，stationId=" + id);
        });
    }
    private Double doubleArg(Map<String, Object> arguments, String key) { Object value = arguments.get(key); if (value == null) return null; if (value instanceof Number n) return n.doubleValue(); try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException e) { throw new BusinessException(400, "参数不是有效数字: " + key); } }
}
