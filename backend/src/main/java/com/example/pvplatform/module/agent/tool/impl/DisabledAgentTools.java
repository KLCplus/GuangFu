package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import org.springframework.stereotype.Component;

import java.util.Map;

abstract class DisabledAgentTool extends AbstractAgentTool {
    public boolean enabled() { return false; }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return ToolExecutionResult.failure("TOOL_DISABLED", "工具尚未接入真实后端接口");
    }
}

@Component
class MarketplacePurchaseTool extends DisabledAgentTool {
    public String name() { return "marketplace.purchase"; }
    public String displayName() { return "购买模型/API 套餐"; }
    public ToolCategory category() { return ToolCategory.MARKETPLACE; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.COST; }
    public String description() { return "禁用：当前 OpenAccountService 只有试用和套餐展示，没有真实购买/扣费接口。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("planCode", Map.of("type", "string"), "modelId", Map.of("type", "number"))); }
}

@Component
class AdminStationManageTool extends DisabledAgentTool {
    public String name() { return "admin.station.manage"; }
    public String displayName() { return "管理端电站综合管理"; }
    public ToolCategory category() { return ToolCategory.ADMIN; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.ADMIN; }
    public String description() { return "禁用：请使用已接入的 station.create/station.update/station.disable/station.delete 精确工具。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
}

@Component
class AdminModelManageTool extends DisabledAgentTool {
    public String name() { return "admin.model.manage"; }
    public String displayName() { return "管理端模型综合管理"; }
    public ToolCategory category() { return ToolCategory.ADMIN; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.ADMIN; }
    public String description() { return "禁用：模型创建/更新/上下线需要更细粒度参数和上线健康检查，尚未注册为 Agent 写工具。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
}
