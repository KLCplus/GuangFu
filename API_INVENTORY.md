# API_INVENTORY

更新时间：2026-07-09

## 结论摘要

后端真实 Controller 已扫描，当前 Spring Boot API 以 `/api` 为主，开放预测接口为 `/openapi/v1/predict`。除登录、注册、刷新、邮箱验证码、OAuth、人脸登录、头像访问和 `/openapi/**` 外，`/api/**` 都需要 JWT；`/api/admin/**` 需要 `ADMIN` 角色。

后端大部分接口使用 `Result<T>{ code, message, data }` 统一包装。例外是 `GET /api/avatars/{storageName}` 直接返回二进制图片。

当前没有发现独立的“看板统计”“广场/Marketplace”“钱包/余额/消费记录”“云图文件上传/下载”后端 Controller。云图字段只出现在 `/openapi/v1/predict` 的 `cloudImages` 入参中，未形成完整云图模块接口。

## 全部接口清单

| 模块 | 方法 | 路径 | 参数 | 是否需登录 | 前端是否调用 | 后端是否存在 | 是否真实数据 | 状态 | 问题 |
|---|---|---|---|---|---|---|---|---|---|
| 认证 | POST | `/api/auth/register` | body: username,password,email | 否 | 是 | 是 | 写入 `sys_user/sys_user_role` | 可测 | 会产生真实用户 |
| 认证 | POST | `/api/auth/login` | body: username,password | 否 | 是 | 是 | 查询 `sys_user`，写 `sys_login_log` | 可测 | 无 |
| 认证 | POST | `/api/auth/refresh` | body: refreshToken | 否 | 是 | 是 | JWT 校验 | 可测 | 无 |
| 认证 | POST | `/api/auth/logout` | Authorization | 是 | 是 | 是 | 当前实现为无状态退出 | 可用 | 服务端未持久化 token 黑名单 |
| 认证 | POST | `/api/auth/forgot-password` | body: email | 否 | 是 | 是 | 依赖验证码/邮件配置 | 部分可用 | `MAIL_ENABLED=false` 时只适合本地流程 |
| 认证 | POST | `/api/auth/reset-password` | body: email,code,newPassword | 否 | 是 | 是 | 更新 `sys_user` | 可测 | 依赖验证码 |
| 认证 | POST | `/api/auth/email/code/send` | body: email | 否 | 是 | 是 | 验证码服务 | 部分可用 | 邮件禁用时不是生产级 |
| 认证 | POST | `/api/auth/email/code/login` | body: email,code | 否 | 是 | 是 | 查询用户 | 可测 | 依赖验证码 |
| 认证 | POST | `/api/auth/email/code/register` | body: username,email,code | 否 | 是 | 是 | 写数据库 | 可测 | 依赖验证码 |
| OAuth | GET | `/api/auth/oauth/{provider}/authorize` | redirectUri | 否 | 是 | 是 | mock/github provider | 部分真实 | mock provider 是开发用途 |
| OAuth | GET | `/api/auth/oauth/{provider}/callback` | code,state | 否 | 部分 | 是 | GitHub/mock 回调 | 部分真实 | 前端主要用 POST 回调 |
| OAuth | POST | `/api/auth/oauth/{provider}/callback` | code,state,redirectUri | 否 | 是 | 是 | mock/github 登录 | 部分真实 | mock provider 硬编码用户信息 |
| 人脸 | POST | `/api/auth/face-login` | multipart file | 否 | 是 | 是 | local mock 或 Aliyun | 部分真实 | 默认 `FACE_PROVIDER=local` 是 mock |
| 用户 | GET | `/api/user/profile` | 无 | 是 | 是 | 是 | 查询 `sys_user/sys_role` | 可用 | Agent 可读 |
| 用户 | PUT | `/api/user/profile` | nickname,email,phone,avatarUrl,gender | 是 | 是 | 是 | 更新 `sys_user` | 可用 | 写操作需确认 |
| 用户 | PUT | `/api/user/password` | oldPassword,newPassword | 是 | 是 | 是 | 更新密码 hash | 可用 | 写操作需确认 |
| 用户 | POST | `/api/user/account/cancel` | password | 是 | 是 | 是 | 更新账号状态 | 高风险 | Agent 不应直接调用 |
| 用户 | POST | `/api/user/avatar` | multipart file | 是 | 是 | 是 | 文件系统 + `sys_user.avatar_url` | 可用 | 文件写入 |
| 用户 | GET | `/api/avatars/{storageName}` | path | 否 | 间接 | 是 | 文件系统读取 | 可用 | 非 Result 包装 |
| 用户 | GET | `/api/user/oauth-accounts` | 无 | 是 | 是 | 是 | 查询 `sys_oauth_account` | 可用 | 无 |
| 用户 | POST | `/api/user/oauth-accounts/{provider}/bind` | OAuth callback body | 是 | 是 | 是 | 写 `sys_oauth_account` | 部分真实 | mock provider 开发用途 |
| 用户 | DELETE | `/api/user/oauth-accounts/{oauthId}` | path | 是 | 是 | 是 | 删除绑定 | 高风险 | 需确认 |
| 用户 | POST | `/api/user/face/enroll` | multipart file | 是 | 是 | 是 | local mock/Aliyun + `sys_face_auth` | 部分真实 | 默认 local mock |
| 用户 | GET | `/api/user/face` | 无 | 是 | 是 | 是 | 查询 `sys_face_auth` | 可用 | 无 |
| 用户 | DELETE | `/api/user/face` | 无 | 是 | 是 | 是 | 删除人脸绑定 | 高风险 | 需确认 |
| 电站 | GET | `/api/stations` | pageNum,pageSize,keyword,status | 是 | 是 | 是 | 查询 `power_station`，普通用户带权限过滤 | 可用 | 无 |
| 电站 | GET | `/api/stations/{stationId}` | path | 是 | 是 | 是 | 查询 `power_station` | 可用 | 详情当前未强制普通用户权限过滤 |
| 电站管理 | POST | `/api/admin/stations` | StationRequest | ADMIN | 是 | 是 | 写 `power_station` | 可用 | 写操作需确认 |
| 电站管理 | PUT | `/api/admin/stations/{stationId}` | StationRequest | ADMIN | 是 | 是 | 更新 `power_station` | 可用 | 写操作需确认 |
| 电站管理 | DELETE | `/api/admin/stations/{stationId}` | path | ADMIN | 是 | 是 | 删除 `power_station` | 高风险 | 不建议 Agent 直连 |
| 光伏数据 | GET | `/api/stations/{stationId}/realtime` | path | 是 | 是 | 是 | 查询 `pv_data` 最新一条 | 可用 | 依赖数据存在 |
| 光伏数据 | GET | `/api/stations/{stationId}/history` | startTime,endTime,interval | 是 | 是 | 是 | Mapper 聚合查询 `pv_data` | 联调失败 | live 测试返回 500，需排查聚合 SQL/VO 映射 |
| 光伏数据 | POST | `/api/stations/{stationId}/data/upload` | multipart file,duplicateStrategy | 是 | 是 | 是 | 文件解析并写 `pv_data/pv_data_import_task` | 可用 | 写操作需确认 |
| 光伏数据 | GET | `/api/stations/{stationId}/data/imports/{importId}` | path | 是 | 是 | 是 | 查询 `pv_data_import_task` | 可用 | 无 |
| 天气 | GET | `/api/stations/{stationId}/weather/current` | path | 是 | 是 | 是 | `weather_data` 缓存 + provider | 部分真实 | 默认 LOCAL provider 是确定性本地数据 |
| 天气 | GET | `/api/stations/{stationId}/weather/forecast` | path | 是 | 是 | 是 | `weather_data` 缓存 + provider | 部分真实 | QWEATHER 才是第三方真实天气 |
| 模型 | GET | `/api/models` | type | 是 | 是 | 是 | 查询 `model_info` | 可用 | 无 |
| 模型 | GET | `/api/models/{modelId}` | path | 是 | 是 | 是 | 查询 `model_info` | 可用 | 无 |
| 模型管理 | GET | `/api/admin/models` | 无 | ADMIN | 是 | 是 | 查询 `model_info` | 可用 | 无 |
| 模型管理 | POST | `/api/admin/models` | CreateModelRequest | ADMIN | 是 | 是 | 写 `model_info` | 可用 | 会校验 model-service 模型名 |
| 模型管理 | PUT | `/api/admin/models/{modelId}` | UpdateModelRequest | ADMIN | 是 | 是 | 更新 `model_info` | 可用 | 写操作需确认 |
| 模型管理 | PUT | `/api/admin/models/{modelId}/status` | modelStatus | ADMIN | 是 | 是 | 更新状态 | 可用 | 上下架操作需确认 |
| 预测 | POST | `/api/predictions` | stationId,modelId,inputMode,inputStartTime,inputEndTime | 是 | 是 | 是 | 写 `prediction_task/input_snapshot/result`，调用 model-service | 部分真实 | model-service 当前是 mock predictor |
| 预测 | GET | `/api/predictions/{taskId}` | path | 是 | 是 | 是 | 查询 `prediction_task` | 可用 | 权限按任务用户过滤 |
| 预测 | GET | `/api/predictions/{taskId}/results` | path | 是 | 是 | 是 | 查询 `prediction_result` | 可用 | 无 |
| 预测 | GET | `/api/predictions/history` | pageNum,pageSize,stationId,modelId,status | 是 | 是 | 是 | 查询 `prediction_task` | 可用 | 无 |
| 报告 | POST | `/api/analysis/report` | stationId,taskId?,title,includeWeather,includePrediction | 是 | 是 | 是 | 查询电站/PV/天气/预测并写 `analysis_report` | 可用 | includePrediction=true 时需要 taskId |
| 报告 | GET | `/api/analysis/reports` | pageNum,pageSize,stationId | 是 | 是 | 是 | 查询 `analysis_report` | 可用 | 无 |
| 报告 | GET | `/api/analysis/reports/{reportId}` | path | 是 | 是 | 是 | 查询 `analysis_report.report_id` | 可用 | 无 |
| API Key | POST | `/api/open/apply-key` | keyName,expireDays | 是 | 是 | 是 | 写 `api_key`，明文只返回一次 | 可用 | Agent 不应打印 key |
| API Key | GET | `/api/open/keys` | 无 | 是 | 是 | 是 | 查询当前用户 `api_key` | 可用 | 无 |
| API Key | PUT | `/api/open/keys/{apiKeyId}/status` | status | 是 | 是 | 是 | 更新 `api_key` | 可用 | 写操作需确认 |
| API Key | DELETE | `/api/open/keys/{apiKeyId}` | path | 是 | 是 | 是 | 删除当前用户 key | 高风险 | 需确认 |
| API 日志 | GET | `/api/open/call-logs` | pageNum,pageSize | 是 | 是 | 是 | 查询 `api_call_log` | 可用 | 前端 query 里有 apiKeyId/status，但后端忽略 |
| OpenAPI | POST | `/openapi/v1/predict` | X-API-KEY + stationId,modelName,input,cloudImages? | API Key | 是 | 是 | 校验 `api_key`，写预测任务/日志，调用 model-service | 部分真实 | model-service mock；不走 `/api` baseURL |
| 管理 API Key | GET | `/api/admin/api-keys` | 无 | ADMIN | 是 | 是 | 查询 `api_key` | 可用 | 无 |
| 管理 API Key | PUT | `/api/admin/api-keys/{apiKeyId}/status` | status | ADMIN | 是 | 是 | 更新任意 key | 可用 | 高权限 |
| 管理 API 日志 | GET | `/api/admin/api-call-logs` | pageNum,pageSize | ADMIN | 是 | 是 | 查询 `api_call_log` | 可用 | 前端 query 里有 apiKeyId/status，但后端忽略 |
| 新闻 | GET | `/api/news` | pageNum,pageSize,type | 是 | 是 | 是 | 查询已发布 `news` | 可用 | 无 |
| 新闻 | GET | `/api/news/{newsId}` | path | 是 | 是 | 是 | 查询 `news` | 可用 | 无 |
| 新闻管理 | GET | `/api/admin/news` | pageNum,pageSize,status,type | ADMIN | 是 | 是 | 查询 `news` | 可用 | 无 |
| 新闻管理 | POST | `/api/admin/news` | NewsRequest | ADMIN | 是 | 是 | 写 `news` | 可用 | 写操作需确认 |
| 新闻管理 | PUT | `/api/admin/news/{newsId}` | NewsRequest | ADMIN | 是 | 是 | 更新 `news` | 可用 | 写操作需确认 |
| 新闻管理 | PUT | `/api/admin/news/{newsId}/publish` | path | ADMIN | 是 | 是 | 更新发布状态 | 可用 | 写操作需确认 |
| 新闻管理 | PUT | `/api/admin/news/{newsId}/offline` | path | ADMIN | 是 | 是 | 更新发布状态 | 可用 | 写操作需确认 |
| 新闻管理 | DELETE | `/api/admin/news/{newsId}` | path | ADMIN | 是 | 是 | 删除 `news` | 高风险 | 不建议 Agent 直连 |
| 通知 | GET | `/api/notifications` | pageNum,pageSize,readStatus | 是 | 是 | 是 | 查询 `user_notification` | 可用 | 无 |
| 通知 | GET | `/api/notifications/unread-count` | 无 | 是 | 是 | 是 | 查询 `user_notification` | 可用 | 前端类型期望 `count`，后端返回 `unreadCount` |
| 通知 | PUT | `/api/notifications/{notificationId}/read` | path | 是 | 是 | 是 | 更新 `user_notification` | 可用 | 写操作需确认 |
| 通知 | PUT | `/api/notifications/read-all` | 无 | 是 | 是 | 是 | 批量更新通知 | 可用 | 写操作需确认 |
| 管理用户 | GET | `/api/admin/users` | pageNum,pageSize,keyword,status,role | ADMIN | 是 | 是 | 查询 `sys_user/sys_role` | 可用 | 无 |
| 管理用户 | PUT | `/api/admin/users/{userId}/status` | status | ADMIN | 是 | 是 | 更新 `sys_user` | 可用 | 高权限 |
| 管理用户 | PUT | `/api/admin/users/{userId}/roles` | roles | ADMIN | 是 | 是 | 更新 `sys_user_role` | 可用 | 高权限 |
| 管理用户 | DELETE | `/api/admin/users/{userId}` | path | ADMIN | 是 | 是 | 删除/禁用用户 | 高风险 | 不建议 Agent 直连 |
| 管理角色 | GET | `/api/admin/roles` | 无 | ADMIN | 是 | 是 | 查询 `sys_role` | 可用 | 无 |
| 管理角色 | POST | `/api/admin/roles` | roleCode,roleName,description | ADMIN | 是 | 是 | 写 `sys_role` | 可用 | 高权限 |
| 管理角色 | PUT | `/api/admin/roles/{roleId}` | role fields | ADMIN | 是 | 是 | 更新 `sys_role` | 可用 | 高权限 |
| 管理角色 | PUT | `/api/admin/roles/{roleId}/status` | status | ADMIN | 是 | 是 | 更新 `sys_role` | 可用 | 高权限 |
| 管理角色 | DELETE | `/api/admin/roles/{roleId}` | path | ADMIN | 是 | 是 | 删除角色 | 高风险 | 不建议 Agent 直连 |
| 登录日志 | GET | `/api/admin/login-logs` | pageNum,pageSize,username,status,loginType | ADMIN | 是 | 是 | 查询 `sys_login_log` | 可用 | 无 |

## 前后端路径问题表

| 问题类型 | 前端路径 | 后端路径 | 影响页面 | 修复建议 |
|---|---|---|---|---|
| 返回字段不一致 | `/notifications/unread-count` 期望 `{ count }` | 返回 `{ unreadCount }` | 通知未读数可能显示为空 | 后端改为 `count` 或前端 normalize |
| 查询参数未生效 | `/open/call-logs` 前端可传 `apiKeyId,status` | 后端只接 `pageNum,pageSize` | API 日志筛选无效 | 后端补筛选参数 |
| 查询参数未生效 | `/admin/api-call-logs` 前端可传 `apiKeyId,status` | 后端只接 `pageNum,pageSize` | 管理端日志筛选无效 | 后端补筛选参数 |
| 返回结构偏差 | `createStation/updateStation` 前端类型是 `Station` | 后端返回 `{stationId}` 或 `{stationId,updated}` | 创建后若立即使用详情字段会缺失 | 前端创建后拉详情，或后端返回完整 Station |
| 返回结构偏差 | `createModel/updateModel` 前端类型是 `Model` | 后端返回 `{modelId,modelCode}` 或 `{modelId}` | 管理端保存后字段可能缺失 | 前端保存后刷新列表，或后端返回完整 Model |
| 缺失模块 | 前端/需求中的 marketplace/wallet/cloud upload | 后端无 Controller | 广场、钱包、云图上传不可真实联调 | 新增后端模块或隐藏入口 |

## 统一响应结构建议

- 后端业务 JSON 基本统一为 `{ code, message, data }`。
- 前端 `request.ts` 已自动 unwrap `data`，Agent 工具如果复用前端 request 应拿 `data`；如果直接 HTTP 调用后端，应保留原始响应用于调试，再把 `data` 传给业务逻辑。
- 需要 normalize 的重点接口：报告列表/详情、通知未读数、创建/更新类接口、分页接口。

## 数据库真实性关系

| 接口 | 相关表 | 是否真实查询 | 是否真实写入 | 备注 |
|---|---|---|---|---|
| 认证/用户 | `sys_user`, `sys_role`, `sys_user_role`, `sys_login_log` | 是 | 是 | OAuth/Face 可走 mock provider |
| 电站 | `power_station`, `user_station_permission` | 是 | 管理端是 | 普通用户列表有权限过滤 |
| PV 数据 | `pv_data`, `pv_data_import_task` | 是 | 上传是 | 历史走聚合 SQL |
| 天气 | `weather_data`, `power_station` | 是 | 是 | LOCAL provider 是本地确定性数据，QWEATHER 才是第三方真实天气 |
| 模型 | `model_info`, `model_metric`, `model_file` | 是 | 管理端是 | 创建会校验 model-service 模型名 |
| 预测 | `prediction_task`, `prediction_input_snapshot`, `prediction_result` | 是 | 是 | 结果来自 model-service；当前 model-service 是 mock predictor |
| 报告 | `analysis_report`, `pv_data`, `weather_data`, `prediction_task`, `prediction_result` | 是 | 是 | 报告内容由后端规则生成，不是 LLM |
| API 管理 | `api_key`, `api_call_log` | 是 | 是 | 明文 key 只创建时返回 |
| 新闻通知 | `news`, `user_notification` | 是 | 管理端/已读接口是 | 无钱包表 |

## model-service 链路

| 功能 | Spring Boot 接口 | model-service 接口 | 是否连通 | 是否真实模型结果 | 问题 |
|---|---|---|---|---|---|
| 健康检查 | `ModelServiceHealthClient.isHealthy()` | `GET /health` | 未 live 验证 | 不涉及 | 本地 9000 未监听 |
| 模型列表校验 | 管理端模型创建/更新 | `GET /model-api/models` | 未 live 验证 | 返回硬编码模型名 | `predictor.py` 是 mock |
| 预测任务 | `POST /api/predictions` | `POST /model-api/predict` | 未 live 验证 | 否，当前是 Mock predictor | 可联调但不是真实模型 |
| OpenAPI 预测 | `POST /openapi/v1/predict` | `POST /model-api/predict` | 未 live 验证 | 否，当前是 Mock predictor | 支持 cloudImages 入参但未真实处理 |
