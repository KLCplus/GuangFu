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

## 8. 模型

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

## 9. 预测

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
  "inputMode": "STATION_HISTORY",
  "inputStartTime": "2026-07-06 10:00:00",
  "inputEndTime": "2026-07-06 10:30:00"
}
```

预测任务响应关键字段：

```json
{
  "taskId": 1001,
  "taskNo": "PRED-xxx",
  "taskStatus": "SUCCESS",
  "modelName": "LSTM 光伏功率预测",
  "modelCode": "lstm_v1",
  "stationId": 1,
  "inputMode": "STATION_HISTORY",
  "createdAt": "2026-07-06 10:30:00",
  "costTime": 120,
  "predictions": [
    {
      "timeOffset": 5,
      "predictTime": "2026-07-06 10:35:00",
      "predictPower": 530.2,
      "actualPowerKw": null,
      "errorValue": null,
      "errorRate": null
    }
  ]
}
```

## 10. 综合分析

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

## 11. 开放平台

### 11.1 用户 API Key

```http
POST   /api/open/apply-key
GET    /api/open/keys
PUT    /api/open/keys/{apiKeyId}/status
DELETE /api/open/keys/{apiKeyId}
GET    /api/open/call-logs?pageNum=1&pageSize=10
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

### 11.2 开放预测接口

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
  ]
}
```

`input` 必须正好 30 帧。

### 11.3 管理员开放平台

```http
GET /api/admin/api-keys
PUT /api/admin/api-keys/{apiKeyId}/status
GET /api/admin/api-call-logs?pageNum=1&pageSize=10
```

## 12. 新闻与通知

### 12.1 新闻

```http
GET /api/news?pageNum=1&pageSize=10&type=
GET /api/news/{newsId}
```

### 12.2 管理员新闻

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

### 12.3 站内通知

```http
GET /api/notifications?pageNum=1&pageSize=10&readStatus=0
GET /api/notifications/unread-count
PUT /api/notifications/{notificationId}/read
PUT /api/notifications/read-all
```

通知按当前用户隔离。

## 13. 联调前置条件

后端接口已经按当前代码整理完毕，可以进入前端联调。联调前需要确认：

1. MySQL 已启动，并已初始化表结构和基础角色数据。
2. 后端使用 `backend/run-local.ps1` 或等价环境变量启动。
3. `JWT_SECRET`、`MYSQL_PASSWORD` 已配置。
4. 预测功能需要模型服务 `MODEL_SERVICE_BASE_URL` 可访问。
5. QWeather 功能需要 `WEATHER_PROVIDER=QWEATHER`、JWT 凭证和电站经纬度。
6. Redis 可选；生产或多实例部署建议开启 `REDIS_ENABLED=true` 和 `CACHE_TYPE=redis`。
7. 管理员接口必须使用 `ROLE_ADMIN` 账号。
8. 文件上传目录需要后端进程有读写权限。
