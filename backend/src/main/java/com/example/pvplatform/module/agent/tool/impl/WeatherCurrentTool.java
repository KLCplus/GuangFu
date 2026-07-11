package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.weather.service.WeatherService;
import com.example.pvplatform.module.weather.vo.CurrentWeatherVO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WeatherCurrentTool extends AbstractAgentTool {
    private final WeatherService weatherService;
    public WeatherCurrentTool(WeatherService weatherService) { this.weatherService = weatherService; }
    public String name() { return "weather.current"; }
    public String displayName() { return "查询当前天气"; }
    public ToolCategory category() { return ToolCategory.WEATHER; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按 stationId 查询当前电站实时天气，会复用天气缓存和权限校验。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"stationId"}, "properties", Map.of("stationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long stationId = longArg(arguments, "stationId", true);
            CurrentWeatherVO weather = weatherService.current(stationId);
            return ToolExecutionResult.success(weather, "已获取电站当前天气：" + weather.weather());
        });
    }
}
