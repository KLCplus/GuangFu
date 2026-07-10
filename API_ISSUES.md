# API_ISSUES

更新时间：2026-07-10

用途：集中记录当前“不行、暂不可用、缺失、字段不一致、模拟实现”的接口问题。后续逐项修复时，优先更新本文件状态，再同步 `API_TEST_REPORT.md` 和 `API_INVENTORY.md`。

## 汇总

| 分类 | 数量 | 说明 |
|---|---:|---|
| P0 真实失败 | 0 | `GET /api/stations/{stationId}/history` 已复测通过 |
| P1 前后端契约不一致 | 0 | 通知、调用日志、站点、模型返回结构已对齐 |
| P1 模拟/非真实外部数据 | 4 | 可联调，但不能算真实生产数据 |
| P2 后端模块缺失 | 4 | 需求或前端页面存在，但后端没有 Controller/表/Service |
| P2 权限受限需单独验证 | 3 | 当前 USER 返回 403 正常，但仍需 ADMIN token 验证 |
| 高风险暂不接入 Agent | 10+ | 删除、注销、禁用、权限修改等写操作 |

## 已验证修复

| ID | 接口/问题 | 当前状态 | 验证结果 | 涉及文件 |
|---|---|---|---|---|
| API-P0-001 | `GET /api/stations/{stationId}/history` 500 | 已修复 | 2026-07-10 使用 `codex_auth_0709101550` 调 `GET /api/stations/2/history?interval=1min` 返回 200，30 条数据 | `PvDataMapper.xml`; `PvDataVO.java`; `PvDataService.java` |
| API-P1-001 | 通知未读数字段 `{ count }` / `{ unreadCount }` 不一致 | 已修复 | 后端返回同时包含 `count` 和 `unreadCount`，兼容前端与旧调用方 | `NotificationController.java`; `web-frontend/src/api/notification.ts` |
| API-P1-002 | 用户端 API 调用日志筛选无效 | 已修复 | `GET /api/open/call-logs` 已接收 `apiKeyId/status`，Service 按条件过滤 | `OpenApiController.java`; `ApiCallLogService.java`; `web-frontend/src/api/open.ts` |
| API-P1-003 | 管理端 API 调用日志筛选无效 | 已修复 | `GET /api/admin/api-call-logs` 已接收 `apiKeyId/status`，Service 按条件过滤 | `OpenApiController.java`; `ApiCallLogService.java`; `web-frontend/src/api/open.ts` |
| API-P1-004 | `createStation/updateStation` 返回结构偏差 | 已修复 | 后端创建/更新后返回完整 Station 详情 | `StationController.java`; `web-frontend/src/api/station.ts` |
| API-P1-005 | `createModel/updateModel/updateModelStatus` 返回结构偏差 | 已修复 | 后端创建/更新/状态变更后返回完整 Model，前端类型已对齐 | `AdminModelController.java`; `web-frontend/src/api/model.ts` |
| API-P1-006 | API 调用日志列表字段为 DO 字段，前端读取 `path/method/status/createdAt` 为空 | 已修复 | 新增 `ApiCallLogVO`，2026-07-10 创建 API Key 并调用 `/openapi/v1/predict` 后，日志返回 `path/method/status/statusCode/createdAt` | `ApiCallLogService.java`; `ApiCallLogVO.java`; `web-frontend/src/api/open.ts` |

## P1：可联调但不是“真实生产数据”

| ID | 功能/接口 | 当前状态 | 问题 | 影响 | 修复建议 | 涉及文件 |
|---|---|---|---|---|---|---|
| API-MOCK-001 | `POST /api/predictions`、`POST /openapi/v1/predict` | 可访问 | model-service 当前 `PredictorProxy` 明确是 Mock predictor，按均值/斜率生成结果 | 预测链路可联调，但不能证明真实模型效果 | 接入真实 checkpoint/推理适配器；响应增加 `source` 标记 | `model-service/app/services/predictor.py`; `ModelServiceClient.java` |
| API-MOCK-002 | `GET /api/stations/{stationId}/weather/current` | 可访问 | 默认 `WEATHER_PROVIDER=LOCAL`，按经纬度 seed 生成天气 | 天气数据不是第三方实时天气 | 配置 QWEATHER 和密钥；保留 LOCAL 仅开发使用 | `application.yml`; `LocalWeatherProvider.java`; `WeatherService.java` |
| API-MOCK-003 | OAuth mock provider | 可访问 | mock provider 硬编码用户信息 | 不代表真实第三方 OAuth | 生产关闭 mock，启用 GitHub/微信等真实 provider | `OAuthService.java`; `OAuthProvidersConfig.java` |
| API-MOCK-004 | 人脸 local provider | 可访问 | 默认 local mock 特征提取/匹配 | 不代表真实人脸识别 | 生产配置 Aliyun provider 或明确标记 mock | `LocalMockFaceProvider.java`; `FaceAuthService.java`; `application.yml` |

## P2：后端模块缺失

| ID | 模块/工具 | 期望接口 | 当前状态 | 影响 | 修复建议 |
|---|---|---|---|---|---|
| API-MISSING-001 | 看板统计 | 首页统计、服务器状态、总体发电/预测统计 | 未发现 Controller | Dashboard 只能拼已有接口，缺统一统计接口 | 新增 dashboard/statistics Controller，聚合电站、PV、预测、模型服务健康 |
| API-MISSING-002 | 广场/Marketplace | 商品列表、商品详情、购买/试用、价格/额度 | 未发现 Controller/表 | 广场不能真实联调 | 新增 marketplace 模块和权益表，或前端隐藏入口 |
| API-MISSING-003 | 钱包/用户权益 | 余额、消费记录、充值记录、已购模型/API 权益 | 未发现 Controller/表 | 钱包/权益不可用 | 新增 wallet/order/entitlement 表和接口 |
| API-MISSING-004 | 云图文件管理 | 云图上传、预测结果查询、文件下载/预览 | 未发现独立 Controller | 云图工具不能完整闭环 | 新增 cloud image upload/result/file API；现有 `/openapi/v1/predict` 只接受 `cloudImages` 入参 |

## P2：权限受限，需 ADMIN token 单独验证

当前测试账号是 `USER`，以下接口返回 403 是预期行为，不算接口坏。但要保证后端整体可用，需要使用 ADMIN token 单独跑 smoke。

| ID | 接口 | 当前 USER 结果 | 待验证内容 |
|---|---|---|---|
| API-AUTH-001 | `GET /api/admin/users` | 403 | ADMIN 下应返回分页用户 |
| API-AUTH-002 | `GET /api/admin/models` | 403 | ADMIN 下应返回模型列表 |
| API-AUTH-003 | `GET /api/admin/api-keys` | 403 | ADMIN 下应返回 API Key 列表 |

建议后续 smoke test 增加 `--adminToken` 或使用 ADMIN 账号登录，分开统计普通用户和管理端接口。

## 高风险接口：暂不作为 Agent 自动调用

这些接口不一定坏，但不适合在 Agent 中无确认直接调用。

| 接口 | 风险 |
|---|---|
| `DELETE /api/admin/stations/{stationId}` | 删除电站数据 |
| `DELETE /api/admin/users/{userId}` | 删除/禁用用户 |
| `DELETE /api/admin/roles/{roleId}` | 删除角色 |
| `DELETE /api/open/keys/{apiKeyId}` | 删除 API Key |
| `DELETE /api/admin/news/{newsId}` | 删除新闻 |
| `POST /api/user/account/cancel` | 注销当前账号 |
| `PUT /api/admin/users/{userId}/roles` | 修改用户权限 |
| `PUT /api/admin/users/{userId}/status` | 修改用户状态 |
| `PUT /api/admin/models/{modelId}/status` | 模型上下架 |
| `PUT /api/open/keys/{apiKeyId}/status` | 启用/禁用 API Key |

## 当前修复顺序建议

1. 用 ADMIN 账号补跑管理端接口 smoke。
2. 决定 model-service、天气、OAuth、人脸是否要从 mock/local 切换为真实生产实现。
3. 再补 Dashboard、Marketplace、Wallet、Cloud 模块，或前端隐藏未完成入口。
