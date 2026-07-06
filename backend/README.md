# Backend

Java 17 + Spring Boot 3 的业务后端。当前为项目骨架版本，Controller 返回 mock 数据，预测接口会通过 HTTP 调用 FastAPI 模型服务。

## 启动

前置条件：JDK 17、Maven 3.9。MySQL 配置已预留；骨架接口暂不执行数据库查询，因此 MySQL 未启动时仍可运行。

```bash
mvn spring-boot:run
```

- 服务地址：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

创建预测任务前请先启动 `model-service`。配置项 `model-service.base-url` 可由环境变量 `MODEL_SERVICE_BASE_URL` 覆盖。

## 分层

每个业务模块按 `controller / service / dto / vo / entity` 组织。当前安全配置全部放行，`SecurityConfig` 已明确保留 JWT 和管理员路径权限收紧位置。
