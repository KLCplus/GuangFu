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
                Long stationId = positiveLong(arg);
                if (stationId != null) {
                    toolName = "weather.current";
                    args.put("stationId", stationId);
                } else if (!arg.isBlank()) {
                    toolName = "weather.location";
                    args.put("location", cleanLocation(arg));
                } else {
                    toolName = "weather.location";
                }
            }
            case "/predict" -> {
                Long taskId = positiveLong(arg);
                toolName = taskId == null ? "prediction.list" : "prediction.detail";
                if (taskId != null) args.put("taskId", taskId);
                putNumber(args, "stationId", null, contextNumber(context, "stationId", "currentStationId"));
            }
            case "/report" -> {
                Long stationId = positiveLong(arg);
                if (stationId == null && (arg.isBlank() || wantsConversationReport(arg))) {
                    toolName = "report.conversation";
                    args.put("title", "光伏平台 Agent 会话工作报告");
                } else {
                    toolName = "report.generate";
                    putNumber(args, "stationId", stationId, contextNumber(context, "stationId", "currentStationId"));
                    putNumber(args, "taskId", null, contextNumber(context, "taskId", "currentTaskId"));
                    args.put("includeWeather", bool(context, "includeWeather", true));
                    args.put("includePrediction", bool(context, "includePrediction", true));
                    if (args.get("stationId") != null) {
                        args.put("title", args.get("stationId") + "号电站综合分析报告");
                    }
                }
            }
            case "/model" -> {
                Long modelId = positiveLong(arg);
                toolName = modelId == null ? "model.list" : "model.detail";
                if (modelId != null) args.put("modelId", modelId);
            }
            case "/api" -> toolName = "api.usage";
            case "/news" -> toolName = "news.list";
            case "/wallet" -> toolName = "wallet.balance";
            case "/profile" -> toolName = "user.profile";
            case "/cloud" -> toolName = "cloud.predict";
            default -> {
                return new AgentToolIntent(false, null, Map.of(), text, "未知命令：" + command + "。可用命令：/station /weather /predict /report /model /api /news /wallet /profile /cloud", "未知 slash command", "slash", false);
            }
        }
        enrichDefaults(toolName, args, context);
        String question = missingQuestion(toolName, args);
        return new AgentToolIntent(true, toolName, args, normalized(toolName, args), question,
            question.isBlank() ? "slash command 明确指定工具" : question, "slash", true);
    }

    public AgentToolIntent natural(String text, Map<String, Object> context) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean business = lower.matches(".*(电站|station|天气|weather|预测|prediction|任务|task|报告|report|api|模型|model|云图|cloud|钱包|余额|市场|套餐|新闻|通知|公告|个人|资料|用户|profile|账号|账户).*");
        if (!business) return AgentToolIntent.none(false);

        Map<String, Object> args = new LinkedHashMap<>();
        String toolName = null;
        if (lower.contains("个人") || lower.contains("资料") || lower.contains("用户信息")
            || lower.contains("账号") || lower.contains("账户信息") || lower.contains("profile")) {
            toolName = "user.profile";
        } else if (lower.contains("新闻") || lower.contains("通知") || lower.contains("公告")) {
            toolName = "news.list";
        } else if (lower.contains("钱包") || lower.contains("余额") || lower.contains("账单")) {
            toolName = "wallet.balance";
        } else if (lower.contains("市场") || lower.contains("套餐")) {
            toolName = "marketplace.list";
        } else if (lower.contains("云图") || lower.contains("cloud")) {
            toolName = "cloud.predict";
        } else if (lower.contains("天气") || lower.contains("weather")) {
            Long stationId = extractStationId(text);
            String location = extractLocation(text);
            if (stationId != null || mentionsStation(lower)) {
                toolName = "weather.current";
                if (stationId != null) args.put("stationId", stationId);
            } else if (location != null) {
                toolName = "weather.location";
                args.put("location", location);
            } else {
                toolName = "weather.location";
            }
        } else if ((lower.contains("模型") || lower.contains("model")) && hasAny(lower, "运行", "调用", "执行")) {
            toolName = "model.run";
        } else if (lower.contains("报告") || lower.contains("report")) {
            Long stationId = extractStationId(text);
            if (stationId == null && (!mentionsStation(lower) || wantsConversationReport(lower))) {
                toolName = "report.conversation";
                args.put("title", "光伏平台 Agent 会话工作报告");
            } else {
                toolName = "report.generate";
                putNumber(args, "stationId", stationId, contextNumber(context, "stationId", "currentStationId"));
                putNumber(args, "taskId", extractTaskId(text), contextNumber(context, "taskId", "currentTaskId"));
                args.put("includeWeather", bool(context, "includeWeather", true));
                args.put("includePrediction", bool(context, "includePrediction", true));
                if (args.get("stationId") != null) args.put("title", args.get("stationId") + "号电站综合分析报告");
            }
        } else if ((lower.contains("预测") || lower.contains("prediction") || lower.contains("任务") || lower.contains("task"))
            && !lower.contains("模型") && !lower.contains("model")) {
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
        if ("weather.location".equals(toolName) && (args.get("location") == null || String.valueOf(args.get("location")).isBlank())) {
            return "请提供城市或地点，例如 /weather 成都 或“查询成都天气”。";
        }
        if ("prediction.detail".equals(toolName) && args.get("taskId") == null) {
            return "请提供预测任务 ID，例如 /predict 8 或“解释任务 8 的预测结果”。";
        }
        if ("cloud.predict".equals(toolName)) {
            return "云图预测需要 modelName 和 10 张 inputImages，请在云图预测页面选择图片后发起，或提供完整参数。";
        }
        if ("model.run".equals(toolName)) {
            return "运行模型需要 stationId、modelId、30 帧 numericValues 和 30 张 inputImages，请在预测页面准备输入后发起。";
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
            case "weather.location" -> "查询 " + args.get("location") + " 当前天气";
            case "prediction.detail" -> "解释任务 " + taskId + " 的预测结果";
            case "prediction.list" -> "查询预测任务列表";
            case "report.generate" -> "生成 " + stationId + " 号电站综合分析报告";
            case "report.conversation" -> "生成当前会话工作报告";
            case "api.usage" -> "查询 API 使用情况";
            case "api.list" -> "查询 API Key 列表";
            case "news.list" -> "查询新闻通知";
            case "wallet.balance" -> "查询钱包余额";
            case "marketplace.list" -> "查询市场套餐";
            case "user.profile" -> "查询个人信息";
            case "cloud.predict" -> "运行云图预测";
            case "model.run" -> "运行预测模型";
            case "model.detail" -> "查询模型 " + args.get("modelId") + " 详情";
            case "model.list" -> "查询模型列表";
            default -> toolName;
        };
    }

    private boolean wantsConversationReport(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("会话") || lower.contains("聊天") || lower.contains("当前")
            || lower.contains("工作") || lower.contains("总结") || lower.contains("markdown")
            || lower.contains("签字") || lower.contains("签名") || lower.contains("pdf");
    }

    private boolean mentionsStation(String lower) {
        return lower != null && (lower.contains("电站") || lower.contains("station"));
    }

    private boolean hasAny(String lower, String... words) {
        if (lower == null) return false;
        for (String word : words) {
            if (lower.contains(word.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String extractLocation(String text) {
        String value = text == null ? "" : text.trim();
        value = value.replaceAll("(?i)weather", "");
        value = value.replace("天气", "");
        value = value.replace("查询", "").replace("查看", "").replace("帮我", "").replace("一下", "").replace("当前", "").replace("今天", "").replace("现在", "");
        value = value.replaceAll("[，,。？?！!].*$", "").trim();
        if (value.isBlank() || mentionsStation(value.toLowerCase(Locale.ROOT))) return null;
        if (value.matches(".*\\d+.*")) return null;
        return cleanLocation(value);
    }

    private String cleanLocation(String value) {
        return value == null ? "" : value.replaceAll("[，,。？?！!]", "").trim();
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
