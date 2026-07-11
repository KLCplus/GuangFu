package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.openapi.service.OpenAccountService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WalletBalanceTool extends AbstractAgentTool {
    private final OpenAccountService accountService;
    public WalletBalanceTool(OpenAccountService accountService) { this.accountService = accountService; }
    public String name() { return "wallet.balance"; }
    public String displayName() { return "查询钱包余额"; }
    public ToolCategory category() { return ToolCategory.WALLET; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户开放平台钱包余额、月度费用和最近账单。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> ToolExecutionResult.success(accountService.wallet(), "已获取钱包余额与月度费用"));
    }
}
