package com.example.pvplatform.module.agent.tool.impl;

import com.example.pvplatform.module.agent.tool.*;
import com.example.pvplatform.module.news.service.NewsService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NewsListTool extends AbstractAgentTool {
    private final NewsService newsService;
    public NewsListTool(NewsService newsService) { this.newsService = newsService; }
    public String name() { return "news.list"; }
    public String displayName() { return "查询新闻通知"; }
    public ToolCategory category() { return ToolCategory.NEWS; }
    public ToolPermissionLevel permissionLevel() { return ToolPermissionLevel.READ_ONLY; }
    public String description() { return "查询当前用户可见的新闻/通知，可按 type 过滤。"; }
    public Map<String, Object> inputSchema() { return schema("type", "object", "properties", Map.of("type", Map.of("type", "string"), "page", Map.of("type", "number"), "size", Map.of("type", "number"))); }
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments) {
        return guard(() -> {
            Long page = longArg(arguments, "page", false);
            Long size = longArg(arguments, "size", false);
            String type = stringArg(arguments, "type", null);
            var result = newsService.list(page == null ? 1 : page.intValue(), size == null ? 10 : size.intValue(), type, null);
            return ToolExecutionResult.success(result, "已获取新闻/通知列表，共 " + result.total() + " 条");
        });
    }
}
