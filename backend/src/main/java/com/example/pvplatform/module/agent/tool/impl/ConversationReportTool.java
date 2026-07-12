package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.dto.AgentMessageDTO;
import com.example.pvplatform.module.agent.dto.AgentToolCallDTO;
import com.example.pvplatform.module.agent.service.AgentMessageService;
import com.example.pvplatform.module.agent.tool.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConversationReportTool extends AbstractAgentTool {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final AgentMessageService messageService;

    public ConversationReportTool(AgentMessageService messageService) {
        this.messageService = messageService;
    }

    public String name() { return "report.conversation"; }
    public String displayName() { return "生成会话工作报告"; }
    public ToolCategory category() { return ToolCategory.REPORT; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "基于当前 Agent 会话的聊天内容、工具调用和执行结果生成正式 Markdown 工作报告，不要求电站 ID。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("title", Map.of("type", "string"), "signatureName", Map.of("type", "string"))); }

    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            if (context == null || context.sessionId() == null) {
                return ToolExecutionResult.failure("MISSING_SESSION", "当前会话不存在，无法生成会话报告");
            }
            String title = stringArg(arguments, "title", "光伏平台 Agent 会话工作报告");
            String signatureName = stringArg(arguments, "signatureName", "");
            List<AgentMessageDTO> messages = messageService.listWithDetails(context.sessionId());
            String markdown = buildMarkdown(title, signatureName, messages);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("title", title);
            data.put("markdown", markdown);
            data.put("messageCount", messages.size());
            data.put("toolCallCount", messages.stream().mapToInt(message -> message.toolCalls() == null ? 0 : message.toolCalls().size()).sum());
            data.put("generatedAt", LocalDateTime.now().format(DATE_TIME));
            List<String> highlights = new ArrayList<>();
            highlights.add("报告类型：当前会话工作报告");
            highlights.add("消息数量：" + data.get("messageCount"));
            highlights.add("工具调用：" + data.get("toolCallCount"));
            highlights.add("格式：正式 Markdown，包含电子签名栏");
            return ToolExecutionResult.success(displayName(), data, "已生成当前会话工作报告", highlights);
        });
    }

    private String buildMarkdown(String title, String signatureName, List<AgentMessageDTO> messages) {
        LocalDateTime now = LocalDateTime.now();
        List<AgentMessageDTO> userMessages = messages.stream().filter(message -> "user".equals(message.role())).toList();
        List<AgentMessageDTO> assistantMessages = messages.stream().filter(message -> "assistant".equals(message.role())).toList();
        List<AgentToolCallDTO> toolCalls = messages.stream()
            .flatMap(message -> message.toolCalls() == null ? java.util.stream.Stream.<AgentToolCallDTO>empty() : message.toolCalls().stream())
            .toList();
        StringBuilder md = new StringBuilder();
        line(md, "# " + title);
        blank(md);
        line(md, "| 项目 | 内容 |");
        line(md, "| --- | --- |");
        line(md, "| 报告类型 | Agent 会话工作报告 |");
        line(md, "| 生成时间 | " + now.format(DATE_TIME) + " |");
        line(md, "| 会话消息 | " + messages.size() + " 条 |");
        line(md, "| 工具调用 | " + toolCalls.size() + " 次 |");
        blank(md);

        line(md, "## 一、工作摘要");
        blank(md);
        if (userMessages.isEmpty()) {
            line(md, "本次会话暂无明确用户任务记录。");
            blank(md);
        } else {
            line(md, "本次会话围绕以下用户目标展开：");
            blank(md);
            userMessages.stream().limit(8).forEach(message -> line(md, "- " + clean(message.content())));
            blank(md);
        }

        line(md, "## 二、执行过程");
        blank(md);
        if (toolCalls.isEmpty()) {
            line(md, "- 本次会话未记录工具调用，结论主要来自对话内容。");
            blank(md);
        } else {
            for (AgentToolCallDTO call : toolCalls) {
                line(md, "- **" + clean(first(call.displayName(), call.toolName())) + "**：" + status(call.status()) + summary(call));
            }
            blank(md);
        }

        line(md, "## 三、关键结论");
        blank(md);
        List<String> conclusions = assistantMessages.stream()
            .map(AgentMessageDTO::content)
            .filter(content -> content != null && !content.isBlank())
            .map(this::clean)
            .filter(content -> !content.startsWith("需要确认后才能执行"))
            .limit(6)
            .toList();
        if (conclusions.isEmpty()) {
            line(md, "- 暂无可归档结论。");
            blank(md);
        } else {
            conclusions.forEach(content -> line(md, "- " + content));
            blank(md);
        }

        line(md, "## 四、后续建议");
        blank(md);
        line(md, "1. 对涉及业务数据的结论，建议在正式使用前复核对应工具返回结果。");
        line(md, "2. 如需形成电站级正式分析报告，请提供电站 ID，并使用电站综合分析报告流程。");
        line(md, "3. 如需归档本报告，可直接复制 Markdown 或使用浏览器打印为 PDF。");
        blank(md);

        line(md, "## 五、电子签名");
        blank(md);
        line(md, "| 角色 | 姓名 / 签字 | 日期 |");
        line(md, "| --- | --- | --- |");
        line(md, "| 编制人 | " + (signatureName.isBlank() ? "________________" : clean(signatureName)) + " | ____ 年 __ 月 __ 日 |");
        line(md, "| 复核人 | ________________ | ____ 年 __ 月 __ 日 |");
        line(md, "| 确认人 | ________________ | ____ 年 __ 月 __ 日 |");
        blank(md);
        line(md, "---");
        blank(md);
        line(md, "> 本报告由光伏平台 Agent 根据当前会话记录自动生成。");
        return md.toString();
    }

    private void line(StringBuilder builder, String value) {
        builder.append(value == null ? "" : value).append('\n');
    }

    private void blank(StringBuilder builder) {
        builder.append('\n');
    }

    private String summary(AgentToolCallDTO call) {
        if (call.errorMessage() != null && !call.errorMessage().isBlank()) return "，错误：" + clean(call.errorMessage());
        Object result = call.result();
        if (result instanceof Map<?, ?> map) {
            Object summary = map.get("summary");
            if (summary != null && !String.valueOf(summary).isBlank()) return "，" + clean(String.valueOf(summary));
        }
        return "";
    }

    private String status(String status) {
        if ("SUCCESS".equalsIgnoreCase(status)) return "完成";
        if ("FAILED".equalsIgnoreCase(status)) return "失败";
        if ("AWAITING_APPROVAL".equalsIgnoreCase(status)) return "等待确认";
        return status == null || status.isBlank() ? "已记录" : clean(status);
    }

    private String first(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace("\r", "").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
