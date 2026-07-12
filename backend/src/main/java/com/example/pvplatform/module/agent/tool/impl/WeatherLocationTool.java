package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.weather.service.WeatherService;
import com.example.pvplatform.module.weather.vo.CurrentWeatherVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class WeatherLocationTool extends AbstractAgentTool {
    private final WeatherService weatherService;

    public WeatherLocationTool(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    public String name() { return "weather.location"; }
    public String displayName() { return "查询城市天气"; }
    public ToolCategory category() { return ToolCategory.WEATHER; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "按城市、地区或地点名称查询当前天气，例如 成都、上海、北京。该工具不需要电站 ID。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"location"}, "properties", Map.of("location", Map.of("type", "string"))); }

    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            String location = stringArg(arguments, "location", null);
            if (location == null || location.isBlank()) {
                return ToolExecutionResult.failure("MISSING_LOCATION", "请提供城市或地点名称，例如 成都");
            }
            CurrentWeatherVO weather = weatherService.currentByLocation(location);
            List<String> highlights = new ArrayList<>();
            highlights.add("地点：" + location);
            highlights.add("天气：" + value(weather.weather(), "未返回"));
            if (weather.temperature() != null) highlights.add("温度：" + weather.temperature() + "℃");
            if (weather.humidity() != null) highlights.add("湿度：" + weather.humidity() + "%");
            if (weather.windDirection() != null && !weather.windDirection().isBlank()) highlights.add("风向：" + weather.windDirection());
            if (weather.windSpeed() != null) highlights.add("风速：" + weather.windSpeed() + " m/s");
            return ToolExecutionResult.success(displayName(), weather,
                location + "当前天气为" + value(weather.weather(), "未知") + temperature(weather),
                highlights);
        });
    }

    private String temperature(CurrentWeatherVO weather) {
        return weather.temperature() == null ? "" : "，温度 " + weather.temperature() + "℃";
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
