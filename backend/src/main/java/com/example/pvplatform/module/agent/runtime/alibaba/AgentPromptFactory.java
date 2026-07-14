package com.example.pvplatform.module.agent.runtime.alibaba;

import com.example.pvplatform.module.agent.dto.AgentMemoryDTO;
import com.example.pvplatform.module.agent.entity.AgentMessageDO;
import com.example.pvplatform.module.agent.runtime.alibaba.skill.AgentSkill;
import com.example.pvplatform.module.agent.service.AgentMemoryService;
import com.example.pvplatform.module.agent.service.AgentMessageService;
import com.example.pvplatform.module.agent.tool.AgentToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentPromptFactory {
    private final AgentMessageService messages;
    private final AgentMemoryService memories;
    private final AgentToolRegistry tools;

    public AgentPromptFactory(AgentMessageService messages, AgentMemoryService memories, AgentToolRegistry tools) {
        this.messages = messages;
        this.memories = memories;
        this.tools = tools;
    }

    public String build(AgentRunContext context, AgentSkill skill) {
        String memory = memories.list(null, true, 8).stream()
            .map(item -> item.memoryType() + "=" + String.valueOf(item.value()))
            .reduce((left, right) -> left + "; " + right).orElse("无");
        List<AgentMessageDO> rows = messages.list(context.session().getSessionId());
        int from = Math.max(0, rows.size() - 12);
        String history = rows.subList(from, rows.size()).stream()
            .filter(row -> row.getContent() != null && !row.getContent().isBlank())
            .map(row -> row.getRole() + ": " + row.getContent())
            .reduce((left, right) -> left + "\n" + right).orElse("无");
        return "你是光伏平台业务 Agent。所有业务事实必须来自工具结果，不得猜测。"
            + "当前 Skill：" + skill.name() + "，目标：" + skill.description() + "。"
            + "允许工具：" + String.join(", ", tools.enabledNames()) + "。"
            + "输出应包含：" + String.join("、", skill.outputSections()) + "。"
            + "当用户明确要求生成电站报告且已知 stationId 时，必须调用 report.generate；使用默认标题和 includeWeather=true、includePrediction=true，"
            + "不得在调用前为标题或选项再次追问。写操作必须等待用户确认；不得泄露密钥、Token、密码或内部鉴权信息；不输出隐藏推理过程。"
            + "长期记忆摘要：" + memory + "。最近会话：\n" + history;
    }
}
