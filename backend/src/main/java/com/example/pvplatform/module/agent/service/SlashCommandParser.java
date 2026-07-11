package com.example.pvplatform.module.agent.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SlashCommandParser {
    private static final Pattern STATION_ID = Pattern.compile("(?:电站|station)?\\s*#?\\s*(\\d+)\\s*(?:号)?\\s*(?:电站)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_ID = Pattern.compile("(?:任务|task)?\\s*#?\\s*(\\d+)\\s*(?:号)?\\s*(?:任务)?", Pattern.CASE_INSENSITIVE);

    public AgentToolIntent parse(String message, Map<String, Object> context, String preferredTool, Map<String, Object> toolArguments) {
        if (preferredTool != null && !preferredTool.isBlank()) {
            return preferred(preferredTool, toolArguments, context);
        }
        String text = message == null ? "" : message.trim();
        if (text.startsWith("/")) {
            return slash(text, context);
        }
        return natural(text, context);
    }

    private AgentToolIntent preferred(String toolName, Map<String, Object> arguments, Map<String, Object> context) {
        Map<String, Object> args = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        enrichDefaults(toolName, args, context);
        String question = missingQuestion(toolName, args);
        return new AgentToolIntent(true, toolName, args, normalized(toolName, args), question,
            question.isBlank() ? "前端提供了首选工具" : question, "preferredTool", true);
    }

    private AgentToolIntent slash(String text, Map<String, Object> context) {
        String[] parts = text.split("\\s+");
        String command = parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : "";
        Map<String, Object> args = new LinkedHashMap<>();
        String toolName;
        switch (command) {
            case "/station" -> {
                Long stationId = positiveLong(arg);
                toolName = stationId == null ? "station.list" : "station.detail";
                if (stationId != null) args.put("stationId", stationId);
            }
            case "/weather" -> {
                toolName = "weather.current";
                putNumber(args, "stationId", positiveLong(arg), contextNumber(context, "stationId", "currentStationId"));
            }
            case "/predict" -> {
                Long taskId = positiveLong(arg);
                toolName = taskId == null ? "prediction.list" : "prediction.detail";
                if (taskId != null) args.put("taskId", taskId);
                putNumber(args, "stationId", null, contextNumber(context, "stationId", "currentStationId"));
            }
            case "/report" -> {
                toolName = "report.generate";
                putNumber(args, "stationId", positiveLong(arg), contextNumber(context, "stationId", "currentStationId"));
                putNumber(args, "taskId", null, contextNumber(context, "taskId", "currentTaskId"));
                args.put("includeWeather", bool(context, "includeWeather", true));
                args.put("includePrediction", bool(context, "includePrediction", true));
                if (args.get("stationId") != null) {
                    args.put("title", args.get("stationId") + "号电站综合分析报告");
                }
            }
            case "/model" -> {
                Long modelId = positiveLong(arg);
                toolName = modelId == null ? "model.list" : "model.detail";
                if (modelId != null) args.put("modelId", modelId);
            }
            case "/api" -> toolName = "api.usage";
            default -> {
                return new AgentToolIntent(false, null, Map.of(), text, "未知命令：" + command + "。可用命令：/station /weather /predict /report /model /api", "未知 slash command", "slash", false);
            }
        }
        enrichDefaults(toolName, args, context);
        String question = missingQuestion(toolName, args);
        return new AgentToolIntent(true, toolName, args, normalized(toolName, args), question,
            question.isBlank() ? "slash command 明确指定工具" : question, "slash", true);
    }

    public AgentToolIntent natural(String text, Map<String, Object> context) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean business = lower.matches(".*(电站|station|天气|weather|预测|prediction|任务|task|报告|report|api|模型|model|云图|钱包|新闻).*");
        if (!business) return AgentToolIntent.none(false);

        Map<String, Object> args = new LinkedHashMap<>();
        String toolName = null;
        if (lower.contains("天气") || lower.contains("weather")) {
            toolName = "weather.current";
            putNumber(args, "stationId", extractStationId(text), contextNumber(context, "stationId", "currentStationId"));
        } else if (lower.contains("报告") || lower.contains("report")) {
            toolName = "report.generate";
            putNumber(args, "stationId", extractStationId(text), contextNumber(context, "stationId", "currentStationId"));
            putNumber(args, "taskId", extractTaskId(text), contextNumber(context, "taskId", "currentTaskId"));
            args.put("includeWeather", bool(context, "includeWeather", true));
            args.put("includePrediction", bool(context, "includePrediction", true));
            if (args.get("stationId") != null) args.put("title", args.get("stationId") + "号电站综合分析报告");
        } else if (lower.contains("预测") || lower.contains("prediction") || lower.contains("任务") || lower.contains("task")) {
            Long taskId = extractTaskId(text);
            toolName = taskId == null ? "prediction.list" : "prediction.detail";
            if (taskId != null) args.put("taskId", taskId);
            putNumber(args, "stationId", extractStationId(text), contextNumber(context, "stationId", "currentStationId"));
        } else if (lower.contains("电站") || lower.contains("station")) {
            Long stationId = extractStationId(text);
            toolName = stationId == null ? "station.list" : "station.detail";
            if (stationId != null) args.put("stationId", stationId);
        } else if (lower.contains("api")) {
            toolName = "api.usage";
        } else if (lower.contains("模型") || lower.contains("model")) {
            toolName = "model.list";
        }
        if (toolName == null) return AgentToolIntent.none(true);
        enrichDefaults(toolName, args, context);
        String question = missingQuestion(toolName, args);
        return new AgentToolIntent(true, toolName, args, normalized(toolName, args), question,
            question.isBlank() ? "业务问题可由规则确定工具" : question, "business-rule", true);
    }

    private void enrichDefaults(String toolName, Map<String, Object> args, Map<String, Object> context) {
        if ("report.generate".equals(toolName)) {
            args.putIfAbsent("includeWeather", bool(context, "includeWeather", true));
            args.putIfAbsent("includePrediction", bool(context, "includePrediction", true));
            if (!args.containsKey("title") && args.get("stationId") != null) {
                args.put("title", args.get("stationId") + "号电站综合分析报告");
            }
        }
    }

    private String missingQuestion(String toolName, Map<String, Object> args) {
        if (("station.detail".equals(toolName) || "weather.current".equals(toolName) || "report.generate".equals(toolName)) && args.get("stationId") == null) {
            return "请提供电站 ID，例如 /weather 2 或“查看 2 号电站信息”。";
        }
        if ("prediction.detail".equals(toolName) && args.get("taskId") == null) {
            return "请提供预测任务 ID，例如 /predict 8 或“解释任务 8 的预测结果”。";
        }
        return "";
    }

    private String normalized(String toolName, Map<String, Object> args) {
        Object stationId = args.get("stationId");
        Object taskId = args.get("taskId");
        return switch (toolName) {
            case "station.detail" -> "查看 " + stationId + " 号电站信息";
            case "station.list" -> "查询当前用户可访问电站列表";
            case "weather.current" -> "查询 " + stationId + " 号电站当前天气";
            case "prediction.detail" -> "解释任务 " + taskId + " 的预测结果";
            case "prediction.list" -> "查询预测任务列表";
            case "report.generate" -> "生成 " + stationId + " 号电站综合分析报告";
            case "api.usage" -> "查询 API 使用情况";
            case "model.detail" -> "查询模型 " + args.get("modelId") + " 详情";
            case "model.list" -> "查询模型列表";
            default -> toolName;
        };
    }

    private Long extractStationId(String text) {
        Matcher matcher = STATION_ID.matcher(text == null ? "" : text);
        while (matcher.find()) {
            Long id = positiveLong(matcher.group(1));
            if (id != null) return id;
        }
        return null;
    }

    private Long extractTaskId(String text) {
        Matcher matcher = TASK_ID.matcher(text == null ? "" : text);
        while (matcher.find()) {
            Long id = positiveLong(matcher.group(1));
            if (id != null) return id;
        }
        return null;
    }

    private void putNumber(Map<String, Object> args, String key, Long value, Long fallback) {
        Long actual = value != null ? value : fallback;
        if (actual != null) args.put(key, actual);
    }

    private Long contextNumber(Map<String, Object> context, String... keys) {
        if (context == null) return null;
        for (String key : keys) {
            Long value = positiveLong(context.get(key));
            if (value != null) return value;
        }
        return null;
    }

    private boolean bool(Map<String, Object> context, String key, boolean fallback) {
        Object value = context == null ? null : context.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private Long positiveLong(Object value) {
        if (value == null) return null;
        try {
            long parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value).replaceAll("[^0-9]", ""));
            return parsed > 0 ? parsed : null;
        } catch (Exception exception) {
            return null;
        }
    }
}
