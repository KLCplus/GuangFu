package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.AbstractAgentTool;
import com.example.pvplatform.module.agent.tool.ToolCategory;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.module.agent.tool.ToolPermissionLevel;
import com.example.pvplatform.module.news.service.NotificationService;
import com.example.pvplatform.module.user.dto.UpdateProfileRequest;
import com.example.pvplatform.module.user.service.UserService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class UserProfileUpdateTool extends AbstractAgentTool {
    private final UserService userService;
    UserProfileUpdateTool(UserService userService) { this.userService = userService; }
    public String name() { return "user.profile.update"; }
    public String displayName() { return "修改个人资料"; }
    public ToolCategory category() { return ToolCategory.USER; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.WRITE; }
    public String description() { return "修改当前用户昵称、邮箱、手机号、头像或性别，需要确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("nickname", Map.of("type", "string"), "email", Map.of("type", "string"), "phone", Map.of("type", "string"), "avatarUrl", Map.of("type", "string"), "gender", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long gender = longArg(arguments, "gender", false);
            var result = userService.updateProfile(new UpdateProfileRequest(
                stringArg(arguments, "nickname", null),
                stringArg(arguments, "email", null),
                stringArg(arguments, "phone", null),
                stringArg(arguments, "avatarUrl", null),
                gender == null ? null : gender.intValue()
            ));
            return ToolExecutionResult.success(displayName(), result, "个人资料已更新", java.util.List.of());
        });
    }
}

@Component
class NotificationMarkReadTool extends AbstractAgentTool {
    private final NotificationService notificationService;
    NotificationMarkReadTool(NotificationService notificationService) { this.notificationService = notificationService; }
    public String name() { return "notification.markRead"; }
    public String displayName() { return "标记通知已读"; }
    public ToolCategory category() { return ToolCategory.NEWS; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.WRITE; }
    public String description() { return "将指定 notificationId 标记为已读，需要确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "required", new String[]{"notificationId"}, "properties", Map.of("notificationId", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long notificationId = longArg(arguments, "notificationId", true);
            notificationService.markRead(notificationId);
            return ToolExecutionResult.success(displayName(), Map.of("notificationId", notificationId, "read", true), "通知已标记为已读", java.util.List.of());
        });
    }
}

@Component
class NotificationMarkAllReadTool extends AbstractAgentTool {
    private final NotificationService notificationService;
    NotificationMarkAllReadTool(NotificationService notificationService) { this.notificationService = notificationService; }
    public String name() { return "notification.markAllRead"; }
    public String displayName() { return "全部通知已读"; }
    public ToolCategory category() { return ToolCategory.NEWS; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.WRITE; }
    public String description() { return "将当前用户全部未读通知标记为已读，需要确认。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of()); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            int updated = notificationService.markAllRead();
            return ToolExecutionResult.success(displayName(), Map.of("updated", updated), "已标记 " + updated + " 条通知为已读", java.util.List.of());
        });
    }
}
