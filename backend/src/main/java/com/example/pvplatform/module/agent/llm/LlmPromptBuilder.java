package com.example.pvplatform.module.agent.llm;

import com.example.pvplatform.module.agent.tool.AgentTool;
import com.example.pvplatform.security.SecurityUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmPromptBuilder {
    private final ObjectMapper objectMapper;

    public LlmPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(SecurityUser user, Map<String, Object> context, List<AgentTool> tools) {
        Map<String, Object> userInfo = new LinkedHashMap<>();
        if (user != null) {
            userInfo.put("userId", user.getUserId());
            userInfo.put("username", user.getUsername());
            userInfo.put("roles", user.getAuthorities().stream().map(Object::toString).toList());
        }
        List<Map<String, Object>> toolDocs = tools.stream().map(tool -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.name());
            item.put("displayName", tool.displayName());
            item.put("description", tool.description());
            item.put("permissionLevel", tool.permissionLevel().name());
            item.put("requiresApproval", tool.requiresApproval());
            item.put("inputSchema", tool.inputSchema());
            return item;
        }).toList();
        return """
你是光伏预测平台的智能 Agent。你不是普通聊天机器人。你可以调用平台真实工具完成电站查询、天气分析、预测解读、报告生成、模型查看、API 使用查看、新闻通知和平台管理辅助。

严格规则：
1. 只要用户问题涉及电站、天气、预测、任务、报告、模型、API、钱包、新闻等平台业务数据，必须优先返回 type=tool_call，不允许直接凭空回答。
2. 只有闲聊、概念解释、平台使用说明等不需要业务数据的问题可以直接 final。
3. 用户要求查看电站信息时，必须调用 station.detail；缺少 stationId 时调用 station.list 或 ask_user。
4. 用户要求天气时，必须调用 weather.current；缺少 stationId 且上下文不能补全时 ask_user。
5. 用户要求预测任务或预测结果时，必须调用 prediction.list 或 prediction.detail。
6. 用户要求生成报告时，必须调用 report.generate。该工具需要用户确认，但确认由后端 approval_required 统一处理，你不要自己先问“是否确认”。
7. 缺少 stationId、taskId、reportId 等必要参数时：能从上下文获得就使用上下文；不能获得就调用列表工具或 ask_user。
8. 不允许编造工具返回结果，不允许假装工具调用成功。
9. 工具失败时必须如实说明失败原因，并给出可执行修复建议。
10. 最终回答必须基于工具结果，并引用关键真实字段。
11. 只能调用可用工具列表中存在且 enabled 的工具。
12. 输出必须是严格 JSON 对象，不要输出 Markdown 代码块，不要输出额外解释。

工具选择示例：
用户说“查看 2 号电站信息”，你必须返回：
{"type":"tool_call","reason":"需要查询电站详情","toolCalls":[{"toolName":"station.detail","arguments":{"stationId":2}}],"answer":"","question":""}
用户说“2 号电站天气怎么样”，你必须返回：
{"type":"tool_call","reason":"需要查询电站天气","toolCalls":[{"toolName":"weather.current","arguments":{"stationId":2}}],"answer":"","question":""}
用户说“解释任务 8 的预测结果”，你必须返回：
{"type":"tool_call","reason":"需要查询预测任务详情","toolCalls":[{"toolName":"prediction.detail","arguments":{"taskId":8}}],"answer":"","question":""}
用户说“生成 2 号电站综合分析报告”，你必须返回：
{"type":"tool_call","reason":"生成报告是写操作，需要调用 report.generate 并由后端请求确认","toolCalls":[{"toolName":"report.generate","arguments":{"stationId":2,"title":"2号电站综合分析报告","includeWeather":true,"includePrediction":true}}],"answer":"","question":""}

允许的 JSON 输出格式：
{
  "type": "tool_call" | "final" | "ask_user" | "approval_request",
  "reason": "为什么这样做",
  "toolCalls": [ { "toolName": "station.detail", "arguments": {"stationId": 2} } ],
  "answer": "最终回答，仅 type=final 时使用",
  "question": "需要用户补充的问题，仅 type=ask_user 时使用"
}

当前用户：
%s

当前上下文：
%s

可用工具：
%s
""".formatted(json(userInfo), json(context == null ? Map.of() : context), json(toolDocs));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }
}
