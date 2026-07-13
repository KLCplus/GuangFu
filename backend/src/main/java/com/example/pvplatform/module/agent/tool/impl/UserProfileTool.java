package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.AbstractAgentTool;
import com.example.pvplatform.module.agent.tool.ToolCategory;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.module.agent.tool.ToolPermissionLevel;
import com.example.pvplatform.module.user.service.UserService;
import com.example.pvplatform.module.user.vo.UserProfileVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class UserProfileTool extends AbstractAgentTool {
    private final UserService userService;

    public UserProfileTool(UserService userService) {
        this.userService = userService;
    }

    public String name() { return "user.profile"; }
    public String displayName() { return "查询个人信息"; }
    public ToolCategory category() { return ToolCategory.USER; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前登录用户的个人资料、安全摘要和角色信息。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }

    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            UserProfileVO profile = userService.profile();
            List<String> highlights = new ArrayList<>();
            highlights.add("用户名：" + value(profile.username()));
            if (profile.nickname() != null && !profile.nickname().isBlank()) highlights.add("昵称：" + profile.nickname());
            if (profile.email() != null && !profile.email().isBlank()) highlights.add("邮箱：" + profile.email());
            if (profile.phone() != null && !profile.phone().isBlank()) highlights.add("手机号：" + profile.phone());
            if (profile.roles() != null && !profile.roles().isEmpty()) highlights.add("角色：" + String.join("、", profile.roles()));
            return ToolExecutionResult.success(displayName(), profile, "已获取当前用户个人信息", highlights);
        });
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "未设置" : value;
    }
}
