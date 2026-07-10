# AGENT_TOOLS_PLAN

更新时间：2026-07-09

## Agent 可调用工具清单

| 工具名 | 对应接口 | 功能说明 | 输入参数 | 输出数据 | 是否可用 | 是否真实数据 | 备注 |
|---|---|---|---|---|---|---|---|
| `user.profile` | `GET /api/user/profile` | 获取当前用户资料 | 无 | userId, username, roles, profile | 是 | 是 | 只读，适合直接接入 |
| `station.list` | `GET /api/stations` | 查询电站列表 | pageNum,pageSize,keyword,status | 分页电站 | 是 | 是 | 只读，P0 |
| `station.detail` | `GET /api/stations/{stationId}` | 查询电站详情 | stationId | 电站详情 | 是 | 是 | 只读，P0 |
| `pv.realtime` | `GET /api/stations/{stationId}/realtime` | 查询实时 PV 数据 | stationId | 最新功率/辐照度等 | 是 | 是 | 依赖数据存在 |
| `pv.history` | `GET /api/stations/{stationId}/history` | 查询历史功率数据 | stationId,startTime,endTime,interval | 时间序列 | 暂不可用 | 是 | live 测试返回 500，先修复 |
| `weather.current` | `GET /api/stations/{stationId}/weather/current` | 查询当前天气 | stationId | 天气、温度、湿度等 | 部分可用 | 默认 LOCAL | 生产需 QWEATHER |
| `weather.forecast` | `GET /api/stations/{stationId}/weather/forecast` | 查询天气预报 | stationId | 预报列表 | 部分可用 | 默认 LOCAL | 生产需 QWEATHER |
| `model.list` | `GET /api/models` | 查询模型列表 | type? | 模型列表 | 是 | 是 | 只读，P1 |
| `model.detail` | `GET /api/models/{modelId}` | 查询模型详情 | modelId | 模型详情 | 是 | 是 | 只读 |
| `prediction.list` | `GET /api/predictions/history` | 查询预测任务历史 | pageNum,pageSize,stationId,modelId,status | 分页任务 | 是 | 是 | P0 |
| `prediction.detail` | `GET /api/predictions/{taskId}` | 查询预测任务详情 | taskId | 任务状态/耗时 | 是 | 是 | P0 |
| `prediction.results` | `GET /api/predictions/{taskId}/results` | 查询预测结果 | taskId | 预测点列表 | 是 | 是 | P0 |
| `prediction.create` | `POST /api/predictions` | 创建预测任务 | stationId,modelId,inputMode | 任务和结果 | 部分可用 | model-service mock | 写操作，需确认 |
| `report.generate` | `POST /api/analysis/report` | 生成综合分析报告 | stationId,taskId?,title,includeWeather,includePrediction | 报告详情 | 是 | 是，规则生成 | 写操作，P0，可作为 `/report` |
| `report.list` | `GET /api/analysis/reports` | 查询报告历史 | pageNum,pageSize,stationId | 分页报告 | 是 | 是 | P0 |
| `report.detail` | `GET /api/analysis/reports/{reportId}` | 查询报告详情 | reportId | 报告正文/段落 | 是 | 是 | P0 |
| `api.key.list` | `GET /api/open/keys` | 查询 API Key 列表 | 无 | key 列表，不含明文 | 是 | 是 | P0 |
| `api.key.create` | `POST /api/open/apply-key` | 创建 API Key | keyName,expireDays | 一次性明文 key | 是 | 是 | 高敏感，需二次确认且禁止日志打印 |
| `api.key.status` | `PUT /api/open/keys/{apiKeyId}/status` | 启用/禁用 Key | apiKeyId,status | success | 是 | 是 | 写操作需确认 |
| `api.usage` | `GET /api/open/call-logs` | 查询调用日志 | pageNum,pageSize | 分页日志 | 是 | 是 | 筛选能力待补 |
| `openapi.predict` | `POST /openapi/v1/predict` | 使用 API Key 调用预测 | X-API-KEY,modelName,input | 预测结果 | 部分可用 | model-service mock | 面向外部 API，不建议普通 Agent 默认用 |
| `news.list` | `GET /api/news` | 查询新闻列表 | pageNum,pageSize,type | 分页新闻 | 是 | 是 | 只读，P1 |
| `news.detail` | `GET /api/news/{newsId}` | 查询新闻详情 | newsId | 新闻正文 | 是 | 是 | 只读 |
| `notification.list` | `GET /api/notifications` | 查询通知 | pageNum,pageSize,readStatus | 分页通知 | 是 | 是 | 只读 |
| `notification.unread` | `GET /api/notifications/unread-count` | 查询未读数 | 无 | unreadCount | 是 | 是 | 需 normalize 到 count |
| `admin.user.list` | `GET /api/admin/users` | 管理用户列表 | pageNum,pageSize,keyword,status,role | 分页用户 | 是 | 是 | ADMIN only，默认隐藏 |
| `admin.station.list` | `GET /api/stations` | 管理电站查看 | page params | 电站列表 | 是 | 是 | ADMIN 可看到更多 |
| `admin.model.list` | `GET /api/admin/models` | 管理模型列表 | 无 | 模型列表 | 是 | 是 | ADMIN only |
| `admin.apiKey.list` | `GET /api/admin/api-keys` | 管理 API Key | 无 | key 列表 | 是 | 是 | ADMIN only |
| `wallet.balance` | 无 | 钱包余额 | userId? | 无 | 否 | 否 | 后端缺失 |
| `marketplace.list` | 无 | 模型广场商品列表 | page params | 无 | 否 | 否 | 后端缺失 |
| `cloud.predict` | 无独立接口 | 云图预测 | image/files | 无 | 否 | 否 | 仅 OpenAPI 入参有 `cloudImages`，缺上传/查询/预览 |

## 接入优先级

P0 直接接入：

- `station.list`
- `station.detail`
- `pv.realtime`
- `pv.history`，但需先修复当前 500
- `weather.current`
- `prediction.list`
- `prediction.detail`
- `prediction.results`
- `report.generate`
- `report.list`
- `report.detail`
- `api.key.list`
- `api.usage`
- `user.profile`

P1 条件接入：

- `prediction.create`：需要确认 model-service 是否真实模型。
- `weather.forecast`：生产需要 QWEATHER。
- `model.list/detail`
- `news.list/detail`
- `notification.list/unread`

P2 管理端接入：

- `admin.user.list`
- `admin.model.list`
- `admin.apiKey.list`
- `admin.loginLogs`
- 管理端写操作必须二次确认，并限制 ADMIN。

暂不接入：

- 用户注销、删除用户、删除电站、删除角色、删除 API Key、新闻删除。
- wallet/marketplace/cloud 独立工具，后端尚不存在。

## Agent 调用适配建议

1. 直接封装成只读工具：用户资料、电站、PV、天气、预测历史、报告查询、模型查询、新闻通知、调用日志。
2. 需要 normalize：分页接口、报告接口、通知未读数、创建/更新返回摘要的接口。
3. 需要补字段：通知未读数统一 `count`；OpenAPI 日志补 `apiKeyId/status` 筛选；创建/更新电站/模型返回完整对象。
4. 需要分页：API Key 列表、管理模型列表目前返回数组，可以后续分页。
5. 权限隔离：`/api/admin/**` 只能 ADMIN；普通 Agent 工具默认不暴露。
6. 写操作二次确认：预测创建、报告生成、API Key 创建/禁用、通知已读、资料修改。
7. `/` 命令建议：`/report`, `/station`, `/weather`, `/predict`, `/api`, `/news`。
8. 隐藏内部调用：`user.profile`、权限检查、API 日志读取可作为上下文工具，不必直接展示给用户。

## 最终结论

当前项目接口基础可以支持 Agent 调用，尤其是电站、PV 数据、预测历史、综合分析报告、API 管理、用户资料这些模块已经具备工具化条件。

主要阻塞是 model-service 当前返回模拟预测、天气默认 LOCAL provider、广场/钱包/云图独立后端接口缺失，以及少量前后端字段/参数不一致。下一步应优先把 P0 只读工具和 `report.generate` 接入 Agent，再补真实模型服务和天气 provider 的生产配置。
