# API 文档索引

当前 API 文档按用途拆分：

- 前端调用 Spring Boot：[`back_front_api.md`](back_front_api.md)
- Spring Boot 调用模型服务：[`module_back_api.md`](module_back_api.md)
- PC 用户端进度：[`hanxxi-pc-user-work-summary.md`](hanxxi-pc-user-work-summary.md)
- 架构说明：[`architecture.md`](architecture.md)
- 数据库说明：[`database.md`](database.md)
- DeepSeek 分析报告配置：[`deepseek_integration.md`](deepseek_integration.md)
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 后端 OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 模型服务 Swagger：`http://localhost:9000/docs`

前后端联调以 `back_front_api.md` 为最终交付文档。旧版 mock 接口说明和临时审计文档已经清理，不再重复维护接口细节。

统一响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

业务接口认证：

```http
Authorization: Bearer <jwt-token>
```

开放 API 认证：

```http
X-API-KEY: <api-key>
```

时间格式：

```text
yyyy-MM-dd HH:mm:ss
```

## 新闻与站内通知

公开新闻无需 Token，只返回已发布、面向全部用户的公开内容：

```http
GET /api/news?pageNum=1&pageSize=10&type=WEATHER_ALERT&keyword=光伏
GET /api/news/{newsId}
```

`type` 可用值：`WEATHER_ALERT`、`DISASTER`、`POLICY`、`INDUSTRY`、`ENTERPRISE`、`PLATFORM`。旧新闻和公告由后端兼容映射，不会作为站内通知混入公开列表。

站内通知必须携带登录 Token：

```http
GET /api/notifications?pageNum=1&pageSize=10&unread=true&type=MODEL_UPDATE
GET /api/notifications/unread-count
PUT /api/notifications/{notificationId}/read
PUT /api/notifications/read-all
```

通知类型包括 `NOTICE`、`MODEL_UPDATE`、`ALERT`、`SYSTEM`。所有查询和已读操作只作用于当前用户。

管理员可以管理内容并手动验证采集来源：

```http
POST /api/admin/news/sync?source=MEM
POST /api/admin/news/sync?source=NEA
POST /api/admin/news/sync?source=LONGI
POST /api/admin/news/sync?source=QWEATHER
```

手动同步接口仅限管理员；定时同步默认关闭，具体开关和频率见 `application.yml` 的 `news.sync` 配置。
