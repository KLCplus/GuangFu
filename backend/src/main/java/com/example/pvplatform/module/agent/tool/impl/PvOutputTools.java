package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.AbstractAgentTool;
import com.example.pvplatform.module.agent.tool.ToolCategory;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.module.agent.tool.ToolPermissionLevel;
import com.example.pvplatform.module.pvoutput.dto.PvOutputStationDTO;
import com.example.pvplatform.module.pvoutput.dto.PvOutputStatusDTO;
import com.example.pvplatform.module.pvoutput.service.PvOutputStationService;
import com.example.pvplatform.module.weather.service.WeatherService;
import com.example.pvplatform.persistence.entity.ExternalPvStationDO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
class PvOutputStationListTool extends AbstractAgentTool {
    private final PvOutputStationService stationService;
    PvOutputStationListTool(PvOutputStationService stationService) { this.stationService = stationService; }
    public String name() { return "pvoutput.station.list"; }
    public String displayName() { return "查询公开电站列表"; }
    public ToolCategory category() { return ToolCategory.STATION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询公开 PVOutput 电站列表，默认只返回启用电站。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("enabled", Map.of("type", "boolean"), "keyword", Map.of("type", "string"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Boolean enabled = arguments != null && arguments.containsKey("enabled") ? boolArg(arguments, "enabled", true) : true;
            String keyword = stringArg(arguments, "keyword", null);
            List<PvOutputStationDTO> result = stationService.list(enabled, keyword);
            return ToolExecutionResult.success(displayName(), result, "已获取公开电站列表，共 " + result.size() + " 个", result.stream().limit(5).map(this::highlight).toList());
        });
    }
    private String highlight(PvOutputStationDTO station) {
        return station.id() + "：" + value(station.systemName(), "未命名公开电站") + size(station.systemSizeW());
    }
    private String size(Integer watts) { return watts == null ? "" : "，容量 " + watts + " W"; }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}

@Component
class PvOutputStationDetailTool extends AbstractAgentTool {
    private final PvOutputStationService stationService;
    PvOutputStationDetailTool(PvOutputStationService stationService) { this.stationService = stationService; }
    public String name() { return "pvoutput.station.detail"; }
    public String displayName() { return "查询公开电站详情"; }
    public ToolCategory category() { return ToolCategory.STATION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按 stationId 查询公开 PVOutput 电站详情。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            PvOutputStationDTO station = stationService.detail(longArg(arguments, "stationId", true));
            return ToolExecutionResult.success(displayName(), station, "已获取公开电站 " + value(station.systemName(), String.valueOf(station.id())) + " 详情", List.of(
                "系统 ID：" + station.externalSystemId(),
                "容量：" + (station.systemSizeW() == null ? "未知" : station.systemSizeW() + " W"),
                "邮编：" + value(station.postcode(), "未知")
            ));
        });
    }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}

@Component
class PvOutputLatestStatusTool extends AbstractAgentTool {
    private final PvOutputStationService stationService;
    PvOutputLatestStatusTool(PvOutputStationService stationService) { this.stationService = stationService; }
    public String name() { return "pvoutput.status.latest"; }
    public String displayName() { return "查询公开电站最新状态"; }
    public ToolCategory category() { return ToolCategory.PREDICTION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询公开 PVOutput 电站最新发电状态。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", true);
            PvOutputStatusDTO result = stationService.latestStatus(stationId);
            if (result == null) {
                return ToolExecutionResult.success(displayName(), Map.of("stationId", stationId), "该公开电站暂无状态数据", List.of());
            }
            return ToolExecutionResult.success(displayName(), result, "已获取公开电站最新状态", List.of(
                "采样时间：" + result.sampleTime(),
                "发电功率：" + value(result.powerGenerationW()) + " W",
                "累计发电：" + value(result.energyGenerationWh()) + " Wh"
            ));
        });
    }
    private String value(Object value) { return value == null ? "未知" : String.valueOf(value); }
}

@Component
class PvOutputStatusHistoryTool extends AbstractAgentTool {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PvOutputStationService stationService;
    PvOutputStatusHistoryTool(PvOutputStationService stationService) { this.stationService = stationService; }
    public String name() { return "pvoutput.status.history"; }
    public String displayName() { return "查询公开电站历史状态"; }
    public ToolCategory category() { return ToolCategory.PREDICTION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询公开 PVOutput 电站历史状态。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"), "startTime", Map.of("type", "string"), "endTime", Map.of("type", "string"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", true);
            List<PvOutputStatusDTO> result = stationService.history(stationId, parse(stringArg(arguments, "startTime", null)), parse(stringArg(arguments, "endTime", null)));
            return ToolExecutionResult.success(displayName(), result, "已获取公开电站历史状态，共 " + result.size() + " 条", result.stream().limit(5).map(item -> item.sampleTime() + "：" + item.powerGenerationW() + " W").toList());
        });
    }
    private LocalDateTime parse(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value, FORMATTER);
    }
}

@Component
class PvOutputWeatherCurrentTool extends AbstractAgentTool {
    private final PvOutputStationService stationService;
    private final WeatherService weatherService;
    PvOutputWeatherCurrentTool(PvOutputStationService stationService, WeatherService weatherService) {
        this.stationService = stationService; this.weatherService = weatherService;
    }
    public String name() { return "pvoutput.weather.current"; }
    public String displayName() { return "查询公开电站天气"; }
    public ToolCategory category() { return ToolCategory.WEATHER; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按公开电站坐标查询当前天气。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            ExternalPvStationDO station = stationService.requireStation(longArg(arguments, "stationId", true));
            requireCoordinates(station);
            var result = weatherService.currentByCoordinates(station.getLongitude().doubleValue(), station.getLatitude().doubleValue());
            return ToolExecutionResult.success(displayName(), result, "已获取公开电站天气", List.of());
        });
    }
    private void requireCoordinates(ExternalPvStationDO station) {
        if (station.getLatitude() == null || station.getLongitude() == null) {
            throw new com.example.pvplatform.common.exception.BusinessException(400, "该公开电站未配置经纬度，无法获取天气");
        }
    }
}

@Component
class PvOutputWeatherForecastTool extends AbstractAgentTool {
    private final PvOutputStationService stationService;
    private final WeatherService weatherService;
    PvOutputWeatherForecastTool(PvOutputStationService stationService, WeatherService weatherService) {
        this.stationService = stationService; this.weatherService = weatherService;
    }
    public String name() { return "pvoutput.weather.forecast"; }
    public String displayName() { return "查询公开电站天气预报"; }
    public ToolCategory category() { return ToolCategory.WEATHER; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按公开电站坐标查询天气预报。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            ExternalPvStationDO station = stationService.requireStation(longArg(arguments, "stationId", true));
            requireCoordinates(station);
            var result = weatherService.forecastByCoordinates(station.getLongitude().doubleValue(), station.getLatitude().doubleValue());
            return ToolExecutionResult.success(displayName(), result, "已获取公开电站天气预报", List.of());
        });
    }
    private void requireCoordinates(ExternalPvStationDO station) {
        if (station.getLatitude() == null || station.getLongitude() == null) {
            throw new com.example.pvplatform.common.exception.BusinessException(400, "该公开电站未配置经纬度，无法获取天气");
        }
    }
}
