package com.example.pvplatform.module.agent.runtime.alibaba.stream;

import com.example.pvplatform.module.agent.tool.AgentTool;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AgentGenerativeUiFactory {
    private static final Map<String, String> COMPONENTS = Map.ofEntries(
        Map.entry("station.detail", "StationSummaryCard"), Map.entry("weather.current", "WeatherImpactCard"),
        Map.entry("prediction.detail", "PredictionTrendCard"), Map.entry("pv.realtime", "PowerMetricCard"),
        Map.entry("pv.history", "PowerMetricCard"), Map.entry("report.generate", "ReportPreviewCard"));

    public Map<String, Object> instruction(AgentTool tool, ToolExecutionResult result) {
        String component = COMPONENTS.getOrDefault(tool.name(), "ToolProgressCard");
        if (!result.success()) component = "ErrorRecoveryCard";
        return Map.of("component", component, "toolName", tool.name(), "props", Map.of(
            "summary", result.summary() == null ? "" : result.summary(),
            "highlights", result.highlights() == null ? List.of() : result.highlights(),
            "data", result.data() == null ? Map.of() : result.data()));
    }
}
