# API_TEST_REPORT

更新时间：2026-07-10

## 测试环境

- 目标后端：`http://localhost:8080`
- 前端：`http://localhost:5173`
- model-service：`http://localhost:9000`
- MySQL：`localhost:3306`

2026-07-10 已完成一轮前后端联调验证：

- `http://localhost:8080` 后端可访问。
- `http://localhost:5173` 前端 Vite 可访问，并能代理 `/api` 到后端。
- `http://localhost:9000` model-service 已启动。
- 使用交接文档测试账号 `codex_auth_0709101550 / Demo123456` 登录成功，后续用户态接口带 JWT 调用。
- 当前账号角色为 `USER`，访问 `/api/admin/**` 返回 403，符合权限预期。

## 实际测试结果

| 类别 | 结果 |
|---|---|
| Controller 扫描 | 通过，发现约 60 个后端 API |
| 前端 API 对照 | P0/P1 已修复并复测；仍保留 mock/缺模块/ADMIN 待验证项 |
| 统一响应检查 | 基本统一为 `Result{code,message,data}` |
| 数据真实性审计 | 大部分接口来自数据库；天气 LOCAL、OAuth mock、人脸 local、model-service predictor 为模拟/开发实现 |
| Live HTTP 测试 | 已运行；登录、报告列表、报告生成、PV 历史、Vite 代理、调用日志均通过 |

集中问题清单见 [API_ISSUES.md](./API_ISSUES.md)。后续修复时以该文件作为逐项跟踪入口。

## 2026-07-10 复测结果

启动方式：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -SkipDocker
```

验证结果：

| 验证项 | 结果 |
|---|---|
| 后端单元测试 | 通过，`mvn test` 结果：63 tests，0 failures，0 errors，1 skipped |
| 前端构建 | 通过，`npm run build` 成功；仅有第三方 pure annotation 和 chunk size 警告 |
| 登录 | `POST /api/auth/login` 返回 200，拿到 JWT |
| 报告列表 | `GET /api/analysis/reports?pageNum=1&pageSize=5` 返回 200 |
| 报告生成 | `POST /api/analysis/report` 返回 200，生成 `reportId=8` |
| 报告详情 | `GET /api/analysis/reports/8` 返回 200，返回 summary |
| PV 历史 | `GET /api/stations/2/history?interval=1min` 返回 200，30 条数据 |
| Vite 代理 | `http://localhost:5173/api/auth/login` 和 `/api/analysis/reports` 返回 200 |
| API 调用日志 | 创建 API Key 后调用 `/openapi/v1/predict`，`GET /api/open/call-logs` 返回 `path/method/status/statusCode/createdAt` 等字段 |

说明：

- 新注册用户未自动拥有 `stationId=2` 权限，访问 PV 历史返回 403 是权限预期；文档测试账号已授权，返回 200。
- 后端启动日志仍会出现 Spring Security generated password 警告，但实际 JWT 登录和受保护接口访问已通过。
- OpenAPI 预测使用不存在模型名制造 400，用于验证调用日志写入；该 400 是预期业务错误，不代表日志接口失败。

## 运行冒烟测试

```powershell
cd C:\Users\99140\Desktop\chengdu\code\pv-power-platform
node .\scripts\api-smoke-test.mjs --baseUrl=http://localhost:8080 --token="<JWT>"
```

也可以让脚本登录：

```powershell
node .\scripts\api-smoke-test.mjs --username=codex_auth_0709101550 --password=Demo123456 --stationId=2 --taskId=8 --modelId=1
```

OpenAPI 预测需要 API Key：

```powershell
node .\scripts\api-smoke-test.mjs --token="<JWT>" --apiKey="<PV_API_KEY>"
```

脚本不会打印 Authorization token 或完整 API Key，响应摘要最多 500 字符。

## 2026-07-09 live smoke 历史结果

执行命令：

```powershell
node .\scripts\api-smoke-test.mjs --baseUrl=http://localhost:8080 --username=codex_auth_0709101550 --password=Demo123456 --stationId=2 --taskId=8 --modelId=1
```

汇总：

| 指标 | 数值 |
|---|---:|
| 总接口数 | 24 |
| 成功数 | 19 |
| 失败数 | 4 |
| 需认证接口数 | 21 |
| 疑似 mock 响应数 | 5 |
| 不适合直接 Agent 调用/未就绪数 | 5 |

关键通过项：

| 接口 | 状态 | 说明 |
|---|---|---|
| `POST /api/auth/login` | 200 | 测试账号登录成功，响应含 JWT，脚本已脱敏 |
| `GET /api/stations` | 200 | 返回真实数据库电站：`成都联调光伏电站` |
| `GET /api/stations/2` | 200 | 返回电站详情 |
| `GET /api/stations/2/realtime` | 200 | 返回 `pv_data` 最新采集数据 |
| `GET /api/stations/2/weather/current` | 200 | 返回 LOCAL provider 天气并写/读缓存 |
| `GET /api/stations/2/weather/forecast` | 200 | 返回 LOCAL provider 预报 |
| `GET /api/models` | 200 | 返回数据库模型列表 |
| `GET /api/models/1` | 200 | 返回 DLinear 模型详情 |
| `GET /api/predictions/history` | 200 | 返回预测任务，包含 taskId=8 |
| `GET /api/predictions/8` | 200 | 返回预测任务详情 |
| `GET /api/predictions/8/results` | 200 | 返回 6 个预测点 |
| `GET /api/analysis/reports` | 200 | 返回真实 `analysis_report` 历史 |
| `POST /api/analysis/report` | 200 | 成功写入新报告 reportId=6 |
| `GET /api/open/keys` | 200 | 当前用户 API Key 为空数组 |
| `GET /api/open/call-logs` | 200 | 当前用户调用日志为空分页 |
| `GET /api/news` | 200 | 空分页，接口逻辑可用但缺新闻数据 |
| `GET /api/notifications` | 200 | 空分页，接口逻辑可用但缺通知数据 |
| `POST /openapi/v1/predict` 无 API Key | 401 | 返回“缺少 API Key”，符合预期 |

失败/受限项：

| 接口 | 状态 | 判断 |
|---|---|---|
| `GET /api/stations/2/history?interval=1min` | 500 | 真实失败，需要后端排查 |
| `GET /api/admin/users` | 403 | 当前 USER 无 ADMIN 权限，符合预期 |
| `GET /api/admin/models` | 403 | 当前 USER 无 ADMIN 权限，符合预期 |
| `GET /api/admin/api-keys` | 403 | 当前 USER 无 ADMIN 权限，符合预期 |

## 典型接口验证点

| 接口 | 期望无 token | 期望有 token | 正常参数 | 错误参数 |
|---|---|---|---|---|
| `GET /api/user/profile` | 401/403 | 返回用户资料 | 无 | 无 |
| `GET /api/stations` | 401/403 | 返回分页电站 | `pageNum=1&pageSize=10` | `pageSize>100` 应 400 |
| `GET /api/stations/{id}/realtime` | 401/403 | 返回最新 PV 数据 | 有权限 stationId | 无数据应 404 |
| `POST /api/predictions` | 401/403 | 创建任务并调用 model-service | 30 分钟连续 PV 数据 | 数据不足/模型离线应 400 |
| `POST /api/analysis/report` | 401/403 | 写入 `analysis_report` | stationId + taskId | includePrediction=true 且无 taskId 应 400 |
| `GET /api/open/keys` | 401/403 | 返回当前用户 API Key 列表 | 无 | 无 |
| `POST /openapi/v1/predict` | 401/403 API Key 错误 | 写预测任务和调用日志 | X-API-KEY + 30 帧输入 | 输入帧数不足应 400 |

## 历史失败原因与修复记录

问题：`GET /api/stations/2/history?interval=1min` 曾返回 500。
当前状态：已修复。2026-07-10 复测返回 200，30 条数据。
修复点：`PvDataVO` 使用 `Double`，聚合 SQL 使用 `COALESCE(AVG(...),0)`，避免空值/类型映射导致 500。
涉及文件：`backend/src/main/resources/mapper/PvDataMapper.xml`, `backend/src/main/java/com/example/pvplatform/module/pvdata/vo/PvDataVO.java`, `backend/src/main/java/com/example/pvplatform/module/pvdata/service/PvDataService.java`。

问题：通知、API 调用日志、站点/模型创建更新返回结构曾存在前后端契约不一致。
当前状态：已修复。2026-07-10 后端测试和前端构建通过；调用日志接口经真实 OpenAPI 调用后返回前端所需字段。
涉及文件：`NotificationController.java`, `OpenApiController.java`, `ApiCallLogService.java`, `ApiCallLogVO.java`, `StationController.java`, `AdminModelController.java`, `web-frontend/src/api/open.ts`, `web-frontend/src/api/model.ts`。

问题：model-service 当前为模拟预测。
原因：`model-service/app/services/predictor.py` 明确声明 `Mock predictor for local front/back integration`，并按均值/斜率生成结果。
影响：预测链路可联调，但不能证明真实模型效果。
修复建议：接入真实 checkpoint/推理适配器，或在响应中标记 `source=mock`。
涉及文件：`model-service/app/services/predictor.py`, `backend/src/main/java/com/example/pvplatform/client/ModelServiceClient.java`。

问题：天气默认 LOCAL provider 非第三方真实天气。
原因：`application.yml` 默认 `WEATHER_PROVIDER=LOCAL`，`LocalWeatherProvider` 按经纬度 seed 生成天气。
影响：天气接口可用但默认不是实时第三方天气。
修复建议：生产/真实性验证时配置 `WEATHER_PROVIDER=QWEATHER` 和密钥，保留缓存降级。
涉及文件：`WeatherService.java`, `LocalWeatherProvider.java`, `application.yml`。

问题：通知未读数前后端字段不一致。
原因：前端类型为 `{ count }`，后端返回 `{ unreadCount }`。
影响：未读数展示/Agent 工具输出可能为空。
修复建议：后端改 `Map.of("count", ...)` 或前端 normalize。
涉及文件：`NotificationController.java`, `web-frontend/src/api/notification.ts`。

问题：OpenAPI 日志筛选参数前端有、后端未接。
原因：前端 `ApiCallLogQuery` 支持 `apiKeyId/status`，Controller 只接 `pageNum/pageSize`。
影响：日志筛选无效。
修复建议：后端 `ApiCallLogService` 增加筛选条件。
涉及文件：`OpenApiController.java`, `ApiCallLogService.java`, `web-frontend/src/api/open.ts`。

问题：广场、钱包、云图文件管理接口缺失。
原因：未发现 Marketplace/Wallet/Cloud controller。
影响：这些模块暂不能作为真实 Agent 工具。
修复建议：先隐藏工具入口或新增后端模块、表和权限模型。
涉及文件：后端模块目录缺失。

## 统计口径

静态扫描统计：

- 后端 Controller：15 个。
- 后端 API：约 60 个。
- 需要 JWT 的 `/api/**`：除安全白名单外全部需要。
- 需要 ADMIN 的接口：`/api/admin/**`。
- 可直接读数据库的接口：电站、PV、模型、预测历史、报告、API Key、日志、新闻通知、用户管理。
- 疑似 mock/开发实现：model-service predictor、LOCAL 天气、mock OAuth、local face provider。
- 不适合 Agent 直接调用：删除用户/电站/角色/API Key、注销账号、批量状态变更等高风险写操作。
