package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.AbstractAgentTool;
import com.example.pvplatform.module.agent.tool.ToolCategory;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.module.agent.tool.ToolPermissionLevel;
import com.example.pvplatform.module.dashboard.service.DashboardService;
import com.example.pvplatform.module.news.service.NewsService;
import com.example.pvplatform.module.news.service.NotificationService;
import com.example.pvplatform.module.pvdata.dto.PvDataHistoryQuery;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import com.example.pvplatform.module.weather.service.WeatherService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
class DashboardOverviewTool extends AbstractAgentTool {
    private final DashboardService dashboardService;
    DashboardOverviewTool(DashboardService dashboardService) { this.dashboardService = dashboardService; }
    public String name() { return "dashboard.overview"; }
    public String displayName() { return "查询仪表盘概览"; }
    public ToolCategory category() { return ToolCategory.REPORT; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户可访问电站的仪表盘概览，可选 stationId。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> ToolExecutionResult.success(displayName(), dashboardService.overview(longArg(arguments, "stationId", false)), "已获取仪表盘概览", List.of()));
    }
}

@Component
class PvRealtimeTool extends AbstractAgentTool {
    private final PvDataService pvDataService;
    PvRealtimeTool(PvDataService pvDataService) { this.pvDataService = pvDataService; }
    public String name() { return "pv.realtime"; }
    public String displayName() { return "查询实时功率"; }
    public ToolCategory category() { return ToolCategory.PREDICTION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询指定电站最新光伏实时数据。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> ToolExecutionResult.success(displayName(), pvDataService.realtime(longArg(arguments, "stationId", true)), "已获取实时功率数据", List.of()));
    }
}

@Component
class PvHistoryTool extends AbstractAgentTool {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PvDataService pvDataService;
    PvHistoryTool(PvDataService pvDataService) { this.pvDataService = pvDataService; }
    public String name() { return "pv.history"; }
    public String displayName() { return "查询历史功率"; }
    public ToolCategory category() { return ToolCategory.PREDICTION; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询指定电站历史功率数据，支持 startTime、endTime、interval。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"), "startTime", Map.of("type", "string"), "endTime", Map.of("type", "string"), "interval", Map.of("type", "string"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", true);
            var query = new PvDataHistoryQuery(parse(stringArg(arguments, "startTime", null)), parse(stringArg(arguments, "endTime", null)), stringArg(arguments, "interval", "1min"));
            var result = pvDataService.history(stationId, query);
            return ToolExecutionResult.success(displayName(), result, "已获取历史功率数据，共 " + result.size() + " 条", List.of());
        });
    }
    private LocalDateTime parse(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value, FORMATTER);
    }
}

@Component
class WeatherForecastTool extends AbstractAgentTool {
    private final WeatherService weatherService;
    WeatherForecastTool(WeatherService weatherService) { this.weatherService = weatherService; }
    public String name() { return "weather.forecast"; }
    public String displayName() { return "查询天气预报"; }
    public ToolCategory category() { return ToolCategory.WEATHER; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按 stationId 查询电站天气预报。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> ToolExecutionResult.success(displayName(), weatherService.forecast(longArg(arguments, "stationId", true)), "已获取天气预报", List.of()));
    }
}

@Component
class WeatherLocationForecastTool extends AbstractAgentTool {
    private final WeatherService weatherService;
    WeatherLocationForecastTool(WeatherService weatherService) { this.weatherService = weatherService; }
    public String name() { return "weather.locationForecast"; }
    public String displayName() { return "查询地点天气预报"; }
    public ToolCategory category() { return ToolCategory.WEATHER; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按城市或地点查询天气预报。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"location"}, "properties", Map.of("location", Map.of("type", "string"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> ToolExecutionResult.success(displayName(), weatherService.forecastByLocation(stringArg(arguments, "location", null)), "已获取地点天气预报", List.of()));
    }
}

@Component
class NewsDetailTool extends AbstractAgentTool {
    private final NewsService newsService;
    NewsDetailTool(NewsService newsService) { this.newsService = newsService; }
    public String name() { return "news.detail"; }
    public String displayName() { return "查询新闻详情"; }
    public ToolCategory category() { return ToolCategory.NEWS; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按 newsId 查询新闻/公告详情。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"newsId"}, "properties", Map.of("newsId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> ToolExecutionResult.success(displayName(), newsService.detail(longArg(arguments, "newsId", true)), "已获取新闻详情", List.of()));
    }
}

@Component
class NotificationListTool extends AbstractAgentTool {
    private final NotificationService notificationService;
    NotificationListTool(NotificationService notificationService) { this.notificationService = notificationService; }
    public String name() { return "notification.list"; }
    public String displayName() { return "查询通知列表"; }
    public ToolCategory category() { return ToolCategory.NEWS; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户通知列表，可按 readStatus 过滤。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("page", Map.of("type", "number"), "size", Map.of("type", "number"), "readStatus", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            int page = Math.toIntExact(longArg(arguments, "page", false) == null ? 1L : longArg(arguments, "page", false));
            int size = Math.toIntExact(longArg(arguments, "size", false) == null ? 10L : longArg(arguments, "size", false));
            Long readStatus = longArg(arguments, "readStatus", false);
            var result = notificationService.list(page, size, readStatus == null ? null : readStatus.intValue());
            return ToolExecutionResult.success(displayName(), result, "已获取通知列表，共 " + result.total() + " 条", List.of());
        });
    }
}

@Component
class NotificationUnreadTool extends AbstractAgentTool {
    private final NotificationService notificationService;
    NotificationUnreadTool(NotificationService notificationService) { this.notificationService = notificationService; }
    public String name() { return "notification.unreadCount"; }
    public String displayName() { return "查询未读通知数"; }
    public ToolCategory category() { return ToolCategory.NEWS; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户未读通知数量。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            long count = notificationService.unreadCount();
            return ToolExecutionResult.success(displayName(), Map.of("unreadCount", count), "未读通知 " + count + " 条", List.of());
        });
    }
}
