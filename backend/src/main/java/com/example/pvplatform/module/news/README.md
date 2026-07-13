# news 模块实现现状

## 内容边界

- `news` 保存公开新闻、公开公告和外部采集内容。匿名用户只能读取已发布、面向全部用户的内容。
- `user_notification` 保存当前用户的站内通知及已读状态，所有通知接口必须登录。
- 公开分类为 `WEATHER_ALERT`、`DISASTER`、`POLICY`、`INDUSTRY`、`ENTERPRISE`、`PLATFORM`。
- `MODEL_UPDATE`、`ALERT`、`SYSTEM_NOTICE` 属于站内通知类型，不进入公开新闻列表。
- 旧 `NEWS`、`NOTICE`、`INDUSTRY_NEWS` 数据通过查询兼容和增量 SQL 映射保留。

## 已实现

- 公开新闻分页、关键词/分类筛选和详情；草稿、下线内容及非公开目标不可匿名读取。
- 管理端创建、编辑、发布、下线、删除、封面/正文图片上传及发布时生成用户通知。
- 站内通知分页、未读筛选、类型筛选、未读数、单条已读和全部已读。
- 外部内容字段、来源归因、原文链接、发布时间、抓取时间、外部唯一标识及预警扩展字段。
- MEM、NEA、LONGI 采集器按来源隔离；单源失败不影响其他来源，重复同步使用外部标识和唯一索引去重。
- QWeather 预警复用既有 JWT 客户端，按启用电站坐标查询并按官方预警 ID 更新。
- 可配置定时同步和管理员手动同步入口。

## 同步配置

定时同步默认关闭：

```text
NEWS_SYNC_ENABLED=false
NEWS_SYNC_WEB_CRON=0 20 */6 * * *
NEWS_SYNC_WEATHER_CRON=0 */20 * * * *
NEWS_SYNC_MEM_ENABLED=true
NEWS_SYNC_NEA_ENABLED=true
NEWS_SYNC_LONGI_ENABLED=true
NEWS_SYNC_WEATHER_ENABLED=true
```

生产启用来源前应先在目标 JVM 环境验证网络、证书链和页面结构。管理员可调用 `POST /api/admin/news/sync?source=LONGI` 等接口手动验证；普通用户和匿名用户不能触发同步。

## 主要接口

- `GET /api/news`、`GET /api/news/{newsId}`：公开读取。
- `/api/admin/news/**`：管理员内容管理和手动同步。
- `/api/notifications/**`：当前登录用户的站内通知。

## 数据库

- 增量脚本：`src/main/resources/sql/news_source_migration.sql`。
- 相关表：`news`、`user_notification`、`sys_user`、`sys_user_role`、`sys_role`。
- 启动迁移器会为旧库补齐来源和预警字段；不会重写已执行的旧 SQL。

## 当前限制

- 外部网页解析依赖对方页面结构和目标 JVM 的 TLS 证书链，启用前必须真实验证。
- 外部正文仅保存清洗后的摘要/片段，不复制完整文章和图片。
- QWeather 只有在配置有效且电站附近存在生效预警时才会写入数据。
- 没有审核流、定时发布、撤回原因和版本历史。
- 通知创建依赖新闻发布流程或服务内部调用，没有独立后台群发页面。

## 测试

- `NewsSecurityTest` 覆盖标准安全配置下匿名公开新闻、匿名通知拒绝和匿名管理接口拒绝。
- `PhaseFourServiceTest` 覆盖公开新闻与模型更新通知的边界及通知生成。
