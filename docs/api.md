# API 文档索引

当前 API 文档按用途拆分：

- 前端调用 Spring Boot：[`back_front_api.md`](back_front_api.md)
- Spring Boot 调用模型服务：[`module_back_api.md`](module_back_api.md)
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 后端 OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 模型服务 Swagger：`http://localhost:9000/docs`

前端联调以 `back_front_api.md` 为最终交付文档。旧版 mock 接口说明已经废弃，不再在本文件重复维护接口细节。

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
