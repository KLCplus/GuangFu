# 项目收束说明

## 配置入口

本地开发配置统一使用仓库根目录 `.env`，后端通过 `spring.config.import` 加载。`backend/.env.local` 已移除；`.env.example` 仅作为无敏感信息的变量清单，不参与运行。

## 数据库入口

数据库初始化和历史增量结构已统一收束到：

`backend/src/main/resources/sql/init.sql`

该文件包含基础表、Agent 会话/工具/审批/记忆/运行事件表，以及 API 统计、新闻、OSS、模型元数据和电站坐标补丁。其他独立 SQL 文件已删除，避免部署时漏执行或顺序不一致。

## Agent

Agent 入口固定使用 Spring AI Alibaba ReactAgent。所有已启用工具会注册为模型 ToolCallback，写操作仍由原有权限和审批机制保护。

## 前端主题

标准布局与可视化布局通过 `ThemeToggle` 互相切换，业务路由、会话、SSE 和权限逻辑保持不变。

## 验证

- `mvn -q test`
- `npm run build`
