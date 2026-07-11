# Backend API For Frontend

本文档按当前 Spring Boot Controller 整理，是前端联调的交付接口清单。本文档列出的路径均来自当前后端代码；接口是否能返回业务成功数据，还取决于数据库初始化、账号权限、模型服务、第三方天气等运行依赖。

## 1. 基础约定

开发地址：

```text
http://localhost:8080
```

业务接口前缀：

```text
/api
```

开放 API 前缀：

```text
/openapi/v1
```

统一响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 1,
    "pageNum": 1,
    "pageSize": 10,
    "records": []
  }
}
```

认证请求头：

```http
Authorization: Bearer <jwt-token>
Content-Type: application/json
```

开放 API 请求头：

```http
X-API-KEY: <api-key>
Content-Type: application/json
```

错误响应使用同一结构。`BusinessException` 会按业务码设置 HTTP 状态码，例如 400、401、403、404、429、502。

时间格式：

```text
yyyy-MM-dd HH:mm:ss
```

## 2. 认证与账号

### 2.1 注册

```http
POST /api/auth/register
```

```json
{
  "username": "demo",
  "password": "Demo123456",
  "email": "demo@example.com"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "demo"
  }
}
```

### 2.2 登录

```http
POST /api/auth/login
```

```json
{
  "username": "demo",
  "password": "Demo123456"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "<jwt>",
    "expiresIn": 7200,
    "refreshToken": "<refresh-token>",
    "refreshExpiresIn": 604800,
    "userInfo": {
      "userId": 1,
      "username": "demo",
      "nickname": "demo",
      "roles": ["USER"]
    }
  }
}
```

### 2.3 刷新 Token

```http
POST /api/auth/refresh
```

```json
{
  "refreshToken": "<refresh-token>"
}
```

### 2.4 退出登录

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

退出会递增账号 token version，该账号已有 token 全部失效。

### 2.5 找回密码

```http
POST /api/auth/forgot-password
POST /api/auth/reset-password
```

发送验证码：

```json
{
  "email": "demo@example.com"
}
```

重置密码：

```json
{
  "email": "demo@example.com",
  "code": "123456",
  "newPassword": "NewDemo123456"
}
```

### 2.6 邮箱验证码登录/注册

```http
POST /api/auth/email/code/send
POST /api/auth/email/code/login
POST /api/auth/email/code/register
```

发送验证码：

```json
{
  "email": "demo@example.com"
}
```

邮箱验证码登录：

```json
{
  "email": "demo@example.com",
  "code": "123456"
}
```

邮箱验证码注册：

```json
{
  "username": "demo",
  "email": "demo@example.com",
  "code": "123456"
}
```

### 2.7 OAuth

```http
GET  /api/auth/oauth/{provider}/authorize?redirectUri=http://localhost:5173
GET  /api/auth/oauth/{provider}/callback
POST /api/auth/oauth/{provider}/callback
GET  /api/user/oauth-accounts
POST /api/user/oauth-accounts/{provider}/bind
DELETE /api/user/oauth-accounts/{oauthId}
```

`provider` 当前支持 `mock` 和 `github`。GitHub 需要配置 `OAUTH_GITHUB_ENABLED=true`、`OAUTH_GITHUB_CLIENT_ID`、`OAUTH_GITHUB_CLIENT_SECRET`。

### 2.8 人脸

```http
POST   /api/auth/face-login        multipart field: file
POST   /api/user/face/enroll       multipart field: file
GET    /api/user/face
DELETE /api/user/face
```

`FACE_PROVIDER=local` 是本地演示匹配；`FACE_PROVIDER=aliyun` 需要阿里云人脸和 OSS 配置。

## 3. 用户

### 3.1 当前用户资料

```http
GET /api/user/profile
```

响应字段：

```json
{
  "userId": 1,
  "username": "demo",
  "nickname": "Demo",
  "email": "demo@example.com",
  "phone": "13800000000",
  "avatarUrl": "/api/avatars/xxx.jpg",
  "gender": 1,
  "status": 1,
  "roles": ["USER"],
  "createdAt": "2026-07-06 10:00:00"
}
```

### 3.2 修改资料

```http
PUT /api/user/profile
```

```json
{
  "nickname": "Demo",
  "email": "new@example.com",
  "phone": "13800000000",
  "avatarUrl": "/api/avatars/xxx.jpg",
  "gender": 1
}
```

### 3.3 修改密码

```http
PUT /api/user/password
```

```json
{
  "oldPassword": "Demo123456",
  "newPassword": "NewDemo123456"
}
```

### 3.4 注销账号

```http
POST /api/user/account/cancel
```

```json
{
  "password": "Demo123456"
}
```

### 3.5 头像

```http
POST /api/user/avatar            multipart field: file
GET  /api/avatars/{storageName}
```

头像响应：

```json
{
  "avatarUrl": "/api/avatars/xxx.jpg"
}
```

## 4. 管理员：用户与角色

所有接口需要 `ROLE_ADMIN`。

```http
GET    /api/admin/users?pageNum=1&pageSize=10&keyword=&status=&role=
PUT    /api/admin/users/{userId}/status
PUT    /api/admin/users/{userId}/roles
DELETE /api/admin/users/{userId}
GET    /api/admin/login-logs?pageNum=1&pageSize=10&username=&status=&loginType=
```

修改用户状态：

```json
{
  "status": 1
}
```

修改用户角色：

```json
{
  "roles": ["USER", "API_USER"]
}
```

角色管理：

```http
GET    /api/admin/roles
POST   /api/admin/roles
PUT    /api/admin/roles/{roleId}
PUT    /api/admin/roles/{roleId}/status
DELETE /api/admin/roles/{roleId}
```

新增角色：

```json
{
  "roleCode": "OPERATOR",
  "roleName": "运维人员",
  "description": "电站运维"
}
```

## 5. 电站

```http
GET    /api/stations?pageNum=1&pageSize=10&keyword=&status=
GET    /api/stations/{stationId}
POST   /api/admin/stations
PUT    /api/admin/stations/{stationId}
DELETE /api/admin/stations/{stationId}
```

普通用户只能看到自己拥有或被授权的电站；管理员可查看全部。新增、修改、删除需要管理员。

电站对象：

```json
{
  "stationId": 1,
  "stationName": "成都示范光伏电站",
  "province": "四川省",
  "city": "成都市",
  "address": "高新区示范园",
  "longitude": 104.0668,
  "latitude": 30.5728,
  "capacity": 1200.0,
  "status": "RUNNING",
  "description": "示例电站"
}
```

新增/修改请求体同上，`stationId` 不需要传。

## 6. 光伏数据

### 6.1 最新实时数据

```http
GET /api/stations/{stationId}/realtime
```

响应字段：

```json
{
  "stationId": 1,
  "collectTime": "2026-07-06 10:30:00",
  "power": 523.5,
  "voltage": 380.2,
  "current": 120.6,
  "irradiance": 850.0,
  "temperature": 31.5,
  "humidity": 62.0,
  "windSpeed": 2.5
}
```

### 6.2 历史数据

```http
GET /api/stations/{stationId}/history?startTime=2026-07-06%2000:00:00&endTime=2026-07-06%2023:59:59&interval=15min
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `startTime` | 否 | 默认最近 24 小时 |
| `endTime` | 否 | 默认当前时间 |
| `interval` | 否 | `1min`、`5min`、`15min`、`1h`，默认 `1min` |

响应：

```json
[
  {
    "time": "2026-07-06 10:00:00",
    "power": 500.2,
    "irradiance": 820.5,
    "temperature": 31.2
  }
]
```

### 6.3 导入数据

```http
POST /api/stations/{stationId}/data/upload
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 说明 |
|---|---|
| `file` | CSV 或 XLSX |
| `duplicateStrategy` | `SKIP`、`UPDATE`、`FAIL`，默认 `SKIP` |

CSV 表头：

```text
collectTime,powerKw,energyTodayKwh,energyTotalKwh,voltageV,currentA,irradianceWM2,moduleTemperatureC,ambientTemperatureC,humidityPercent,windSpeedMS
```

### 6.4 导入任务

```http
GET /api/stations/{stationId}/data/imports/{importId}
```

## 7. 天气

```http
GET /api/stations/{stationId}/weather/current
GET /api/stations/{stationId}/weather/forecast
```

电站必须有 `longitude` 和 `latitude`，顺序由后端按 `longitude,latitude` 调用天气服务。

当前天气响应：

```json
{
  "stationId": 1,
  "weather": "晴",
  "temperature": 32.0,
  "humidity": 60.0,
  "windDirection": "东南风",
  "windPower": "3级",
  "windSpeed": 2.5,
  "reportTime": "2026-07-06 10:30:00",
  "source": "QWEATHER",
  "cached": false
}
```

预报响应：

```json
[
  {
    "date": "2026-07-07",
    "dayWeather": "晴",
    "nightWeather": "多云",
    "dayTemp": 34.0,
    "nightTemp": 26.0,
    "humidity": 60.0,
    "windDirection": "东南风",
    "windPower": "3级",
    "source": "QWEATHER",
    "cached": false
  }
]
```

天气调试页面：

```text
http://localhost:8080/weather-debug.html
```

## 8. PVOutput 公开电站

所有 PVOutput 调用都走后端，前端不要保存或传递 PVOutput API Key。后端从环境变量读取：

```text
PVOUTPUT_API_KEY
PVOUTPUT_AUTH_SYSTEM_ID
```

后端会把公开电站和状态数据落库。页面平时读取数据库数据；手动同步、启动同步和定时同步才会请求 PVOutput。同步失败时不会清空历史数据，接口仍可读取库内最近一次成功数据。

官方 `getstatus.jsp?sid1=...` 查询其他公共电站状态通常需要 PVOutput Donation mode；普通 API Key 可搜索公开电站，但可能返回 `Inaccessible System ID`。为了演示多电站数据，后端在官方状态 API 全部失败或无可用凭证时，会自动 fallback 到 `https://pvoutput.org/live.jsp` 公开实时页，解析前 30 个公开电站快照并写入 `external_pv_station` / `external_pv_station_status`。这条 fallback 适合展示使用；生产长期稳定接入建议开 Donation mode 或接入自有电站上传数据。

应用启动时如果库内没有 `PVOUTPUT` 来源电站，会自动写入少量公开展示电站：RPM Building 37、Woodrose、Arcare Parkwood 100kw LG neon2、Pro Tech Distributions Unit 1、HISA3。配置凭证后启动同步/定时同步优先尝试 `getstatus.jsp`，失败后使用公开实时页 fallback。

普通用户端展示入口：

```text
/pvoutput
```

该页面只读取本平台后端接口，不直接访问 PVOutput，也不会暴露 API Key。

### 8.1 搜索公开电站

```http
GET /api/pvoutput/stations/search?keyword=Enphase&countryCode=au&seenDays=7
```

参数默认值：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `keyword` | `Enphase` | 搜索关键词，可为空 |
| `countryCode` | `au` | 国家代码 |
| `seenDays` | `7` | 最近输出天数 |

响应：

```json
[
  {
    "systemName": "Demo PV",
    "systemSizeW": 6500,
    "postcode": "3000",
    "orientation": "North",
    "outputs": 1234,
    "lastOutputText": "Today",
    "externalSystemId": 123456,
    "panel": "Panel",
    "inverter": "Inverter",
    "distanceKm": 10.2,
    "latitude": -37.8136,
    "longitude": 144.9631
  }
]
```

### 8.2 添加或重新申请公开电站

```http
POST /api/pvoutput/stations
```

请求体使用搜索结果中的 `externalSystemId`。如果已存在，会更新基础信息并重新启用，不重复插入：

```json
{
  "externalSystemId": 123456,
  "systemName": "Demo PV",
  "systemSizeW": 6500,
  "postcode": "3000",
  "orientation": "North",
  "outputs": 1234,
  "lastOutputText": "Today",
  "panel": "Panel",
  "inverter": "Inverter",
  "distanceKm": 10.2,
  "latitude": -37.8136,
  "longitude": 144.9631
}
```

### 8.3 查询已添加公开电站

```http
GET /api/pvoutput/stations?enabled=true&keyword=Demo
```

`enabled` 和 `keyword` 都可选。返回数据库中的公开电站列表，包含最近同步状态：

```json
[
  {
    "id": 1,
    "source": "PVOUTPUT",
    "externalSystemId": 123456,
    "systemName": "Demo PV",
    "enabled": true,
    "lastSyncTime": "2026-07-10 10:00:00",
    "lastSyncStatus": "SUCCESS",
    "lastSyncError": null
  }
]
```

### 8.4 启用或禁用公开电站

```http
PATCH /api/pvoutput/stations/{id}/enabled?enabled=false
```

禁用后保留历史状态数据，周期同步会跳过该电站。

### 8.5 手动同步

```http
POST /api/pvoutput/stations/{id}/sync
POST /api/pvoutput/stations/sync-all
POST /api/pvoutput/stations/sync-live
```

`sync-all` 会串行同步所有启用电站，每个 PVOutput 请求间隔约 1 秒，避免并发请求；如果官方状态 API 没有任何成功结果，会自动 fallback 到公开实时页。`sync-live` 会直接抓取公开实时页前 30 个电站快照。返回每个电站的同步结果：

```json
{
  "stationId": 1,
  "externalSystemId": 123456,
  "systemName": "Demo PV",
  "status": "SUCCESS",
  "message": "同步成功",
  "latestStatus": {
    "externalSystemId": 123456,
    "sampleTime": "2026-07-10 09:55:00",
    "energyGenerationWh": 12000,
    "powerGenerationW": 2300,
    "temperatureC": 28.5,
    "voltageV": 230.1
  }
}
```

PVOutput 返回 `Donation Mode`、`Inaccessible System ID`、`No status found`、超限等错误时，后端会把错误写入 `lastSyncError`，并继续保留历史状态数据。

### 8.6 查询状态数据

```http
GET /api/pvoutput/stations/{id}/latest-status
GET /api/pvoutput/stations/{id}/status?startTime=2026-07-01T00:00:00&endTime=2026-07-10T23:59:59
```

历史状态按 `sampleTime` 升序返回；不传时间默认最近 7 天。前端功率曲线使用 `sampleTime` 作为 x 轴，`powerGenerationW` 作为 y 轴。

### 8.7 公开电站天气

```http
GET /api/pvoutput/stations/{id}/weather/current
GET /api/pvoutput/stations/{id}/weather/forecast
```

公开电站必须有 `latitude` 和 `longitude`，后端按坐标调用天气服务。响应格式与主电站天气接口一致（参见第 7 节）。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "stationId": null,
    "weather": "晴",
    "temperature": 32.0,
    "humidity": 60.0,
    "windDirection": "东南风",
    "windPower": "3级",
    "windSpeed": 2.5,
    "reportTime": "2026-07-06 10:30:00",
    "source": "QWEATHER",
    "cached": false
  }
}
```

## 9. 模型

```http
GET /api/models?type=NUMERIC
GET /api/models/{modelId}
GET /api/admin/models
POST /api/admin/models
PUT /api/admin/models/{modelId}
PUT /api/admin/models/{modelId}/status
```

普通用户只看到在线模型；管理员接口可管理全部模型。

新增模型：

```json
{
  "modelCode": "lstm_v1",
  "modelName": "LSTM 光伏功率预测",
  "modelType": "NUMERIC",
  "modelVersion": "v1.0",
  "inputWindowMinutes": 30,
  "inputFrameIntervalSeconds": 60,
  "outputSteps": 6,
  "outputStepMinutes": 5,
  "serviceModelName": "lstm_v1",
  "apiPath": "/predict",
  "inputSchema": "{}",
  "outputSchema": "{}",
  "description": "短期功率预测"
}
```

修改模型状态：

```json
{
  "modelStatus": "ONLINE"
}
```

## 10. 预测

```http
POST /api/predictions
GET  /api/predictions/{taskId}
GET  /api/predictions/{taskId}/results
GET  /api/predictions/history?pageNum=1&pageSize=10&stationId=&modelId=&status=
```

创建预测：

```json
{
  "stationId": 1,
  "modelId": 1,
  "inputMode": "MANUAL_MULTIMODAL",
  "numericValues": [
    { "time": "2026-07-06 10:00:00", "value": 500.2 }
  ],
  "inputImages": [
    { "time": "2026-07-06 10:00:00", "image": "data:image/png;base64,..." }
  ]
}
```

`numericValues` 必须正好 30 个数值，`inputImages` 必须正好 30 张图片；两组数据都按时间升序排列，时间间隔均为 1 分钟，且同一序号的数值和图片时间必须一致。图片支持 data URL 或纯 base64 字符串。

创建预测响应：

```json
{
  "taskId": 1001
}
```

预测任务详情和结果通过下面两个接口查询。

`GET /api/predictions/{taskId}` 返回任务详情，不包含 `predictions`：

```json
{
  "taskId": 1001,
  "taskNo": "PRED-xxx",
  "stationId": 1,
  "stationName": "成都站",
  "modelId": 3,
  "modelName": "iTransformer光伏功率预测模型",
  "modelCode": "iTransformer",
  "inputMode": "STATION_HISTORY",
  "status": "SUCCESS",
  "createdAt": "2026-07-08 11:09:55",
  "startedAt": "2026-07-08 11:09:55",
  "finishedAt": "2026-07-08 11:09:55",
  "costTimeMs": 20,
  "errorMessage": null
}
```

`GET /api/predictions/{taskId}/results` 返回预测结果数组：

```json
[
  {
    "timeOffset": 5,
    "predictTime": "2026-07-08 11:14:00",
    "predictPower": 75.85,
    "actualPowerKw": null,
    "errorValue": null,
    "errorRate": null
  }
]
```

## 11. 综合分析

```http
POST /api/analysis/report
GET  /api/analysis/reports?pageNum=1&pageSize=10&stationId=
GET  /api/analysis/reports/{reportId}
```

生成报告：

```json
{
  "stationId": 1,
  "taskId": 1001,
  "title": "成都站运行分析",
  "includeWeather": true,
  "includePrediction": true
}
```

报告详情字段：

```json
{
  "reportId": 1,
  "userId": 1,
  "stationId": 1,
  "taskId": 1001,
  "title": "成都站运行分析",
  "summary": "...",
  "weatherAnalysis": "...",
  "predictionAnalysis": "...",
  "abnormalAnalysis": "...",
  "suggestion": "...",
  "reportContent": "...",
  "reportJson": "{}",
  "createdAt": "2026-07-06 10:30:00"
}
```

## 12. 用户看板

```http
GET /api/dashboard/overview?stationId=1
```

`stationId` 不传时，后端选当前用户可访问的第一个电站。接口聚合电站列表、当前天气、天气预报、实时光伏数据和服务资源状态；其中天气或实时数据某一项失败时，接口仍返回可用数据，`dataSource` 为 `PARTIAL`。

响应字段：

```json
{
  "stations": [],
  "selectedStationId": 1,
  "weather": {},
  "forecasts": [],
  "realtime": {},
  "resources": [
    {
      "name": "CPU",
      "value": 35,
      "detail": "后端服务负载",
      "level": "healthy"
    }
  ],
  "dataSource": "REMOTE",
  "lastUpdate": "2026-07-10 12:00:00"
}
```

## 13. 云图预测

```http
POST /api/cloud-forecast/predict
```

请求体：

```json
{
  "modelName": "SimVP_Cloud",
  "inputImages": [
    "data:image/png;base64,..."
  ]
}
```

`inputImages` 必须正好 10 张图片，支持 data URL 或纯 base64 字符串。Spring Boot 会转发到模型服务 `POST /cloud-api/predict`，模型服务不可用时返回 502。

响应：

```json
{
  "modelName": "SimVP_Cloud",
  "predictions": [
    {
      "frameIndex": 0,
      "timeOffset": 5,
      "image": "data:image/png;base64,...",
      "confidence": 96.0,
      "cloudCoverage": null
    }
  ],
  "costTime": 1200
}
```

## 14. 开放平台

### 14.1 用户 API Key

```http
POST   /api/open/apply-key
GET    /api/open/keys
PUT    /api/open/keys/{apiKeyId}/status
PUT    /api/open/keys/{apiKeyId}/name
DELETE /api/open/keys/{apiKeyId}
POST   /api/open/keys/{apiKeyId}/reset
GET    /api/open/call-logs?pageNum=1&pageSize=10
GET    /api/open/call-logs/export
GET    /api/open/usage/summary
GET    /api/open/usage/trend
GET    /api/open/usage/by-model
GET    /api/open/usage/by-key
POST   /api/open/trials
GET    /api/open/entitlements
GET    /api/open/wallet
GET    /api/open/plans
GET    /api/open/overview
```

申请 Key：

```json
{
  "keyName": "前端联调",
  "expireDays": 90
}
```

修改状态：

```json
{
  "status": "ACTIVE"
}
```

修改 Key 名称（不改变密钥本身）：

```http
PUT /api/open/keys/{apiKeyId}/name
```

```json
{
  "keyName": "生产环境预测服务"
}
```

响应返回更新后的 `ApiKeyVO`，需校验当前用户是 Key 所有者。

重置 Key 会重新生成密钥哈希，只在本次响应返回完整 `apiKey`，旧 Key 立即失效。

模型免费试用：

```http
POST /api/open/trials
```

```json
{
  "modelId": 1
}
```

响应：

```json
{
  "trialId": 1780000000000,
  "modelId": 1,
  "modelName": "iTransformer",
  "expireTime": "2026-07-17 12:00:00",
  "quota": 100
}
```

权益、钱包、套餐和开放账户总览：

```http
GET /api/open/entitlements
GET /api/open/wallet
GET /api/open/plans
GET /api/open/overview
```

`/api/open/overview` 返回：

```json
{
  "wallet": {
    "balance": 0.00,
    "frozenBalance": 0.00,
    "monthlyCost": 12.30,
    "currency": "CNY",
    "records": []
  },
  "apiEntitlements": [],
  "plans": []
}
```

当前版本不新增钱包充值/订单表，钱包月消费和权益已用量基于当前用户 API 调用日志聚合；套餐列表为后端固定配置，用于前端展示和后续购买接口衔接。

### 14.2 调用日志查询（增强）

```http
GET /api/open/call-logs?pageNum=1&pageSize=10&apiKeyId=&status=&startTime=&endTime=&modelId=
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNum` | 否 | 默认 1 |
| `pageSize` | 否 | 默认 10，最大 100 |
| `apiKeyId` | 否 | 按 API Key 筛选 |
| `status` | 否 | `SUCCESS` 或 `FAILED` |
| `startTime` | 否 | 开始时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `endTime` | 否 | 结束时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `modelId` | 否 | 按模型筛选 |

日志 VO 字段：

```json
{
  "logId": 1,
  "apiKeyId": 1,
  "modelId": 1,
  "modelName": "iTransformer",
  "path": "/openapi/v1/predict",
  "method": "POST",
  "requestIp": "127.0.0.1",
  "requestTime": "2026-07-10 12:00:00",
  "responseTime": "2026-07-10 12:00:01",
  "costTime": 123,
  "costTimeMs": 123,
  "statusCode": 200,
  "status": "SUCCESS",
  "errorMessage": null,
  "requestSummary": null,
  "responseSummary": null,
  "inputTokens": null,
  "outputTokens": null,
  "totalTokens": null,
  "createdAt": "2026-07-10 12:00:00"
}
```

`modelName` 通过关联 `model_info` 表获取；`inputTokens`、`outputTokens`、`totalTokens` 是兼容字段名，数据库、DO、VO 和聚合 SQL 已就绪。当前模型调用结果没有可靠的 input/output Token 字段，开放预测日志仍写入 null，不做估算或伪造。

### 14.3 调用日志导出

```http
GET /api/open/call-logs/export?startTime=&endTime=&apiKeyId=&modelId=&status=
```

参数与日志筛选保持一致。不使用分页，最多返回 10000 条记录。返回日志数组（JSON），前端按需生成 CSV。

### 14.4 使用统计汇总

```http
GET /api/open/usage/summary?startTime=&endTime=&apiKeyId=&modelId=
```

所有参数可选，按当前用户过滤。

响应：

```json
{
  "totalCalls": 1250,
  "successCalls": 1180,
  "failedCalls": 70,
  "successRate": 94.4,
  "avgCostTimeMs": 234,
  "inputTokens": 0,
  "outputTokens": 0,
  "totalTokens": 0
}
```

### 14.5 按日期调用趋势

```http
GET /api/open/usage/trend?startTime=&endTime=&apiKeyId=&modelId=&granularity=DAY
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `startTime` | 否 | 开始时间 |
| `endTime` | 否 | 结束时间 |
| `apiKeyId` | 否 | 按 Key 筛选 |
| `modelId` | 否 | 按模型筛选 |
| `granularity` | 否 | `DAY`（默认）或 `HOUR` |

响应记录字段：`timeBucket`、`totalCalls`、`successCalls`、`failedCalls`、`avgCostTimeMs`、`totalTokens`。当前未写入可靠 Token 时，`totalTokens` 聚合值为 0。按 `timeBucket` 升序返回数组。

### 14.6 按模型统计

```http
GET /api/open/usage/by-model?startTime=&endTime=&apiKeyId=
```

响应记录字段：`modelId`、`modelName`、`totalCalls`、`successCalls`、`failedCalls`、`avgCostTimeMs`、`totalTokens`。当前未写入可靠 Token 时，`totalTokens` 聚合值为 0。按 `totalCalls` 降序返回。

### 14.7 按 API Key 统计

```http
GET /api/open/usage/by-key?startTime=&endTime=&modelId=
```

响应记录字段：`apiKeyId`、`keyName`、`apiKeyPrefix`、`totalCalls`、`successCalls`、`failedCalls`、`avgCostTimeMs`、`totalTokens`。当前未写入可靠 Token 时，`totalTokens` 聚合值为 0。按 `totalCalls` 降序返回。

### 14.8 开放预测接口

```http
POST /openapi/v1/predict
X-API-KEY: <api-key>
```

```json
{
  "stationId": 1,
  "modelName": "lstm_v1",
  "input": [
    {
      "time": "2026-07-06 10:00:00",
      "power": 500.2,
      "temperature": 31.2,
      "irradiance": 820.5
    }
  ],
  "inputImages": [
    { "time": "2026-07-06 10:00:00", "image": "data:image/png;base64,..." }
  ]
}
```

`input` 必须正好 30 帧，`inputImages` 必须正好 30 张图片；两组数据都按时间升序排列，时间间隔均为 1 分钟，且同一序号时间必须一致。

### 14.9 管理员开放平台

```http
GET /api/admin/api-keys
PUT /api/admin/api-keys/{apiKeyId}/status
GET /api/admin/api-call-logs?pageNum=1&pageSize=10&apiKeyId=&status=&startTime=&endTime=&modelId=
```

管理员调用日志接口参数与 `14.2` 用户端一致，但不过滤 userId。

## 15. 新闻与通知

### 15.1 新闻

```http
GET /api/news?pageNum=1&pageSize=10&type=
GET /api/news/{newsId}
```

### 15.2 管理员新闻

```http
GET    /api/admin/news?pageNum=1&pageSize=10&status=&type=
POST   /api/admin/news
PUT    /api/admin/news/{newsId}
PUT    /api/admin/news/{newsId}/publish
PUT    /api/admin/news/{newsId}/offline
DELETE /api/admin/news/{newsId}
```

新闻请求：

```json
{
  "title": "模型更新通知",
  "summary": "新增 LSTM v2",
  "content": "平台新增预测模型。",
  "coverUrl": "https://example.com/cover.png",
  "newsType": "MODEL_UPDATE",
  "targetRole": "ALL"
}
```

### 15.3 站内通知

```http
GET /api/notifications?pageNum=1&pageSize=10&readStatus=0
GET /api/notifications/unread-count
PUT /api/notifications/{notificationId}/read
PUT /api/notifications/read-all
```

通知按当前用户隔离。

## 14. 联调前置条件

后端接口已经按当前代码整理完毕，可以进入前端联调。联调前需要确认：

1. MySQL 已启动，并已初始化表结构和基础角色数据。
2. 后端使用根目录 `./start-local.sh --backend-only` 启动。
3. `JWT_SECRET`、`MYSQL_PASSWORD` 已配置。
4. 预测功能需要模型服务 `MODEL_SERVICE_BASE_URL` 可访问。
5. QWeather 功能需要 `WEATHER_PROVIDER=QWEATHER`、JWT 凭证和电站经纬度。
6. Redis 可选；生产或多实例部署建议开启 `REDIS_ENABLED=true` 和 `CACHE_TYPE=redis`。
7. 管理员接口必须使用 `ROLE_ADMIN` 账号。
8. 文件上传目录需要后端进程有读写权限。
