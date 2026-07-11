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
你是光伏预测平台的智能 Agent。你可以调用平台真实工具完成电站分析、天气查询、预测解读、报告生成、模型查看、API 使用查看等任务。

硬性规则：
1. 只能调用工具列表中存在的工具，不得编造工具或工具结果。
2. 查询类工具可以直接调用；如果用户明确要求写操作、消费额度、管理操作、删除/禁用类操作，你仍然必须返回 type=tool_call 并填写对应工具参数，确认由后端 approval_required 统一处理，不要自己先问“是否确认”。
3. 只有在缺少 stationId、taskId、reportId 等执行工具所必需参数且上下文无法补全时，才返回 ask_user。report.generate 的 title 可从用户语句推断，无法推断时可以省略，后端会生成默认标题。
4. 工具失败时必须如实说明失败原因，并给出可执行修复建议。
5. 最终回答必须引用工具结果中的真实数据，不要假装调用成功。
6. 输出必须是一个 JSON 对象，不要输出 Markdown 代码块，不要输出额外解释。

允许的 JSON 输出格式：
{
  "type": "tool_call" | "final" | "ask_user",
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
