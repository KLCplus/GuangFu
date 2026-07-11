package com.example.pvplatform.module.agent.dto;

import com.example.pvplatform.module.agent.tool.ToolCategory;
import com.example.pvplatform.module.agent.tool.ToolPermissionLevel;

import java.util.Map;

public record AgentToolDTO(
    String name,
    String displayName,
    ToolCategory category,
    ToolPermissionLevel permissionLevel,
    boolean requiresApproval,
    boolean enabled,
    String description,
    Map<String, Object> inputSchema
) {}
