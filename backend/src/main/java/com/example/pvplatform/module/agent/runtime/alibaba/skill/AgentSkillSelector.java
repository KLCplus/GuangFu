package com.example.pvplatform.module.agent.runtime.alibaba.skill;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentSkillSelector {
    private final AgentSkillLoader loader;

    public AgentSkillSelector(AgentSkillLoader loader) { this.loader = loader; }

    public AgentSkill select(String message) {
        String value = message == null ? "" : message.toLowerCase();
        if (value.contains("报告") || value.contains("report")) return skill("report-generation");
        if (value.contains("资料") || value.contains("profile") || value.contains("昵称") || value.contains("邮箱")) return skill("profile-management");
        return skill("station-inspection");
    }

    private AgentSkill skill(String name) {
        return loader.loadAll().stream().filter(skill -> name.equals(skill.name())).findFirst()
            .orElseThrow(() -> new IllegalStateException("Agent Skill 不存在: " + name));
    }
}
