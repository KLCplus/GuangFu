package com.example.pvplatform.module.agent.runtime.alibaba.skill;

import java.util.List;

public record AgentSkill(String name, String description, List<String> allowedTools, List<String> outputSections) {}
