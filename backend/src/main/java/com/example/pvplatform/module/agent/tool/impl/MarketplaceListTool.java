package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.service.OpenAccountService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MarketplaceListTool extends AbstractAgentTool {
    private final OpenAccountService accountService;
    public MarketplaceListTool(OpenAccountService accountService) { this.accountService = accountService; }
    public String name() { return "marketplace.list"; }
    public String displayName() { return "查询市场套餐"; }
    public ToolCategory category() { return ToolCategory.MARKETPLACE; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询开放平台可购买/试用套餐列表。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> ToolExecutionResult.success(accountService.plans(), "已获取市场套餐列表"));
    }
}
