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
            case "/dashboard" -> toolName = "dashboard.overview";
            case "/pv" -> {
                Long stationId = positiveLong(arg);
                toolName = "pv.realtime";
                if (stationId != null) args.put("stationId", stationId);
            }
            case "/notify" -> toolName = "notification.list";
            default -> {
                return new AgentToolIntent(false, null, Map.of(), text, "未知命令：" + command + "。可用命令：/station /weather /predict /report /model /api /news /wallet /profile /cloud /dashboard /pv /notify", "未知 slash command", "slash", false);
            }
        }
        enrichDefaults(toolName, args, context);
        String question = missingQuestion(toolName, args);
        return new AgentToolIntent(true, toolName, args, normalized(toolName, args), question,
            question.isBlank() ? "slash command 明确指定工具" : question, "slash", true);
    }

    public AgentToolIntent natural(String text, Map<String, Object> context) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean business = lower.matches(".*(电站|station|天气|weather|预测|prediction|任务|task|报告|report|api|模型|model|云图|cloud|钱包|余额|市场|套餐|新闻|通知|公告|个人|资料|用户|profile|账号|账户|仪表盘|dashboard|实时功率|历史功率|功率曲线|已读|昵称|邮箱|手机号|电话|联系方式|修改|更新|pvoutput|公开).*");
        if (!business) return AgentToolIntent.none(false);

        Map<String, Object> args = new LinkedHashMap<>();
        String toolName = null;
        if (hasAny(lower, "修改个人", "更新个人", "改昵称", "改邮箱", "改手机号", "改电话", "改联系方式", "修改昵称", "修改邮箱", "修改手机号", "修改电话", "修改联系方式", "昵称改", "邮箱改", "手机号改", "电话改")
            || (hasAny(lower, "手机号", "电话号码", "联系电话", "联系方式", "手机", "电话") && hasAny(lower, "改为", "改成", "修改为", "设为", "设置为"))) {
            toolName = "user.profile.update";
            args.putAll(profileUpdateArgs(text));
        } else if (lower.contains("pvoutput") || lower.contains("公开电站") || lower.contains("公有电站")) {
            Long stationId = extractStationId(text);
            if (lower.contains("天气")) {
                toolName = lower.contains("预报") ? "pvoutput.weather.forecast" : "pvoutput.weather.current";
                if (stationId != null) args.put("stationId", stationId);
            } else if (lower.contains("历史") || lower.contains("状态记录")) {
                toolName = "pvoutput.status.history";
                if (stationId != null) args.put("stationId", stationId);
            } else if (lower.contains("状态") || lower.contains("功率") || lower.contains("发电")) {
                toolName = "pvoutput.status.latest";
                if (stationId != null) args.put("stationId", stationId);
            } else if (stationId != null || lower.contains("详情")) {
                toolName = "pvoutput.station.detail";
                if (stationId != null) args.put("stationId", stationId);
            } else {
                toolName = "pvoutput.station.list";
            }
        } else if (lower.contains("个人") || lower.contains("资料") || lower.contains("用户信息")
            || lower.contains("账号") || lower.contains("账户信息") || lower.contains("profile")) {
            toolName = "user.profile";
        } else if (lower.contains("仪表盘") || lower.contains("dashboard") || lower.contains("概览")) {
            toolName = "dashboard.overview";
            putNumber(args, "stationId", extractStationId(text), contextNumber(context, "stationId", "currentStationId"));
        } else if (hasAny(lower, "实时功率", "实时数据", "当前功率", "最新功率")) {
            toolName = "pv.realtime";
            putNumber(args, "stationId", extractStationId(text), contextNumber(context, "stationId", "currentStationId"));
        } else if (hasAny(lower, "历史功率", "历史数据", "功率曲线", "发电历史")) {
            toolName = "pv.history";
            putNumber(args, "stationId", extractStationId(text), contextNumber(context, "stationId", "currentStationId"));
        } else if (lower.contains("新闻") || lower.contains("通知") || lower.contains("公告")) {
            if (hasAny(lower, "全部已读", "全部通知已读", "通知全部已读")) {
                toolName = "notification.markAllRead";
            } else if (hasAny(lower, "标记通知", "通知已读", "设为已读")) {
                toolName = "notification.markRead";
                putNumber(args, "notificationId", extractTaskId(text), null);
            } else if (lower.contains("未读通知") || lower.contains("未读消息")) {
                toolName = "notification.unreadCount";
            } else if (lower.contains("通知")) {
                toolName = "notification.list";
            } else if (lower.contains("详情")) {
                toolName = "news.detail";
                putNumber(args, "newsId", extractTaskId(text), null);
            } else {
                toolName = "news.list";
            }
        } else if (lower.contains("钱包") || lower.contains("余额") || lower.contains("账单")) {
            toolName = "wallet.balance";
        } else if (lower.contains("市场") || lower.contains("套餐")) {
            toolName = "marketplace.list";
        } else if (lower.contains("云图") || lower.contains("cloud")) {
            toolName = "cloud.predict";
        } else if (lower.contains("天气") || lower.contains("weather")) {
            Long stationId = extractStationId(text);
            String location = extractLocation(text);
            if (lower.contains("预报") && (stationId != null || mentionsStation(lower))) {
                toolName = "weather.forecast";
                if (stationId != null) args.put("stationId", stationId);
            } else if (lower.contains("预报")) {
                toolName = "weather.locationForecast";
                if (location != null) args.put("location", location);
            } else if (stationId != null || mentionsStation(lower)) {
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
        if (toolName != null && toolName.startsWith("pvoutput.") && !"pvoutput.station.list".equals(toolName) && args.get("stationId") == null) {
            return "请提供公开电站 ID，例如“查看公开电站 2 的状态”。";
        }
        if (("pv.realtime".equals(toolName) || "pv.history".equals(toolName) || "weather.forecast".equals(toolName)) && args.get("stationId") == null) {
            return "请提供电站 ID，例如“查询 2 号电站实时功率”。";
        }
        if ("weather.location".equals(toolName) && (args.get("location") == null || String.valueOf(args.get("location")).isBlank())) {
            return "请提供城市或地点，例如 /weather 成都 或“查询成都天气”。";
        }
        if ("weather.locationForecast".equals(toolName) && (args.get("location") == null || String.valueOf(args.get("location")).isBlank())) {
            return "请提供城市或地点，例如“查询成都天气预报”。";
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
        if ("user.profile.update".equals(toolName) && args.isEmpty()) {
            return "请说明要修改的个人资料字段，例如昵称、邮箱或手机号。";
        }
        if ("notification.markRead".equals(toolName) && args.get("notificationId") == null) {
            return "请提供通知 ID，例如“将通知 3 标记为已读”。";
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
            case "weather.forecast" -> "查询 " + stationId + " 号电站天气预报";
            case "weather.locationForecast" -> "查询 " + args.get("location") + " 天气预报";
            case "dashboard.overview" -> "查询仪表盘概览";
            case "pv.realtime" -> "查询 " + stationId + " 号电站实时功率";
            case "pv.history" -> "查询 " + stationId + " 号电站历史功率";
            case "prediction.detail" -> "解释任务 " + taskId + " 的预测结果";
            case "prediction.list" -> "查询预测任务列表";
            case "report.generate" -> "生成 " + stationId + " 号电站综合分析报告";
            case "report.conversation" -> "生成当前会话工作报告";
            case "api.usage" -> "查询 API 使用情况";
            case "api.list" -> "查询 API Key 列表";
            case "news.list" -> "查询新闻通知";
            case "news.detail" -> "查询新闻详情";
            case "notification.list" -> "查询通知列表";
            case "notification.unreadCount" -> "查询未读通知数";
            case "notification.markRead" -> "标记通知已读";
            case "notification.markAllRead" -> "全部通知已读";
            case "wallet.balance" -> "查询钱包余额";
            case "marketplace.list" -> "查询市场套餐";
            case "user.profile" -> "查询个人信息";
            case "user.profile.update" -> "修改个人资料";
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

    private Map<String, Object> profileUpdateArgs(String text) {
        Map<String, Object> args = new LinkedHashMap<>();
        String value = afterMarker(text, "昵称");
        if (value != null) args.put("nickname", value);
        value = afterMarker(text, "邮箱");
        if (value != null) args.put("email", value);
        value = afterMarker(text, "手机号");
        if (value == null) value = afterMarker(text, "手机");
        if (value == null) value = afterMarker(text, "电话号码");
        if (value == null) value = afterMarker(text, "联系电话");
        if (value == null) value = afterMarker(text, "电话");
        if (value == null) value = afterMarker(text, "联系方式");
        if (value != null) args.put("phone", value);
        return args;
    }

    private String afterMarker(String text, String marker) {
        if (text == null || !text.contains(marker)) return null;
        String value = text.substring(text.indexOf(marker) + marker.length())
            .replaceFirst("^(改成|改为|修改为|设为|设置为|为|成|是)", "")
            .replaceAll("[，,。？?！!].*$", "")
            .trim();
        return value.isBlank() ? null : value;
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
