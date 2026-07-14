package com.example.pvplatform.module.agent.runtime.alibaba.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSkillSelectorTest {
    private final AgentSkillSelector selector = new AgentSkillSelector(new AgentSkillLoader());

    @Test
    void selectsStationInspectionForOperationalQuestions() {
        AgentSkill skill = selector.select("分析 1 号电站当前功率和天气影响");
        assertEquals("station-inspection", skill.name());
        assertTrue(skill.allowedTools().contains("pv.realtime"));
    }

    @Test
    void selectsReportSkillForReportRequests() {
        AgentSkill skill = selector.select("给 1 号电站生成报告");
        assertEquals("report-generation", skill.name());
        assertTrue(skill.allowedTools().contains("report.generate"));
    }

    @Test
    void selectsProfileSkillForProfileRequests() {
        AgentSkill skill = selector.select("修改我的个人资料和邮箱");
        assertEquals("profile-management", skill.name());
        assertTrue(skill.allowedTools().contains("user.profile.update"));
    }
}
