# openapi 模块实现现状

## 模块职责

`openapi` 负责对外开放 API Key 管理、外部预测接口、配额限制和调用日志。

## 已实现

- 登录用户可申请 API Key，返回完整 key 仅在创建时展示。
- 数据库只保存 key 前缀和 SHA-256 哈希，不保存明文。
- 用户可查看自己的 key、启停自己的 key、删除自己的 key。
- 管理员可查看全部 key 并调整状态。
- `ApiKeyAuthenticationFilter` 对 `/openapi/**` 请求执行 API Key 鉴权。
- 支持每分钟限流和每日额度校验。
- 外部预测接口会校验模型、电站、输入帧数量，并调用模型服务。
- 调用日志写入 `api_call_log`，成功和失败路径都会尝试记录。
- 用户和管理员均可分页查看调用日志。

## 未实现或限制

- 配额计数使用 JVM 内存 `ConcurrentHashMap`，服务重启会清零，多实例部署不共享。
- 限流不是基于 Redis/数据库的分布式实现。
- API Key 删除是物理删除，未实现软删除或历史保留策略。
- 外部预测没有复用内部 `prediction_task` 持久化链路，不生成预测任务和结果表记录。
- 请求摘要和响应摘要较简单，没有完整审计脱敏策略。

## 主要接口

- `POST /api/open/apply-key`
- `GET /api/open/keys`
- `PUT /api/open/keys/{apiKeyId}/status`
- `DELETE /api/open/keys/{apiKeyId}`
- `GET /api/open/call-logs`
- `POST /openapi/v1/predict`
- `GET /api/admin/api-keys`
- `PUT /api/admin/api-keys/{apiKeyId}/status`
- `GET /api/admin/api-call-logs`

## 相关表

- `api_key`
- `api_call_log`
- 预测时依赖 `model_info`、`power_station`。

## 测试情况

- 已有 `ApiKeyServiceTest` 和 `ApiQuotaServiceTest`。
- 建议补充 OpenAPI Filter 集成测试、失败日志写入测试、多 key 配额隔离测试、外部预测模型服务异常测试。
