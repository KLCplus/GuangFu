package com.example.pvplatform.module.agent.tool;

import java.util.Map;

public interface AgentTool {
    String name();
    String displayName();
    ToolCategory category();
    ToolPermissionLevel permissionLevel();
    String description();
    Map<String, Object> inputSchema();
    boolean enabled();
    default boolean requiresApproval() {
        return permissionLevel() != ToolPermissionLevel.READ_ONLY;
    }
    ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments);
}
