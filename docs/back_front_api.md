# 前端—Spring Boot 接口文档

本文定义 Vue Web、小程序与 Spring Boot 后端之间的接口。模型服务内部接口不属于前端调用范围，见 [module_back_api.md](module_back_api.md)。

## 1. 基础约定

### 1.1 服务地址

```text
开发环境：http://localhost:8080
业务前缀：/api
开放接口前缀：/openapi/v1
```

Vite 开发环境通过代理访问 `/api` 和 `/openapi`，前端代码不应写死 `localhost:8080`。

### 1.2 请求头

登录后业务接口预期使用：

```http
Authorization: Bearer <jwt-token>
Content-Type: application/json
```

开放 API 使用：

```http
X-API-KEY: <api-key>
Content-Type: application/json
```

JWT 认证已全面启用。未登录访问受保护接口返回 401，普通用户访问管理员接口返回 403。

### 1.3 统一响应

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

业务码：

| code | 前端处理 |
|---:|---|
| 200 | 正常读取 `data` |
| 400 | 展示具体业务提示 |
| 401 | 清除登录态并跳转登录页 |
| 403 | 展示无权限页面 |
| 404 | 展示资源不存在 |
| 500 | 展示系统异常 |
| 502 | 提示模型服务暂不可用 |

注意：当前全局异常处理可能仍以 HTTP 200 返回业务错误，前端必须同时判断响应体中的 `code`。

### 1.4 时间格式

请求中的业务时间统一使用：

```text
yyyy-MM-dd HH:mm:ss
```

部分当前 mock 接口可能返回 ISO 时间，如 `2026-07-06T10:30:00`。后续应在 Jackson 配置中统一。

## 2. 认证与用户

### 2.1 用户注册

```http
POST /api/auth/register
```

请求：

```json
{
  "username": "demo",
  "password": "Demo@123456",
  "email": "demo@example.com"
}
```

校验规则：

- 用户名 4～32 位，不能重复。
- 密码至少 8 位，必须包含字母和数字。
- 邮箱可选，填写时必须合法且唯一。
- 用户名或邮箱重复返回 400。

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

说明：

- 后端使用 BCrypt 保存密码哈希。
- 注册后默认关联 `USER` 角色。
- 如果 `USER` 角色不存在，注册事务回滚并返回 500。

### 2.2 用户登录

```http
POST /api/auth/login
```

请求：

```json
{
  "username": "demo",
  "password": "Demo@123456"
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
    "userInfo": {
      "userId": 1,
      "username": "demo",
      "nickname": "Demo",
      "roles": ["USER"]
    }
  }
}
```

说明：

- 登录成功返回真实 JWT（不再使用 mock token）。
- `expiresIn` 为 Token 有效秒数，默认 7200。
- 登录失败（用户名不存在、密码错误、用户禁用）统一返回 401 "用户名或密码错误"。
- 登录成功后更新 `last_login_time` 和 `last_login_ip`。

### 2.3 当前用户资料

```http
GET /api/user/profile
Authorization: Bearer <token>
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "demo",
    "nickname": "Demo",
    "email": "demo@example.com",
    "phone": "138****0000",
    "avatarUrl": null,
    "gender": 1,
    "status": 1,
    "roles": ["USER"],
    "createdAt": "2026-07-06 10:00:00"
  }
}
```

说明：

- 根据 Token 中的 userId 查询，严格返回当前登录用户。
- 不返回 `passwordHash`、`deleted` 等内部字段。
- 手机号中间 4 位脱敏显示。

### 2.4 修改个人资料

```http
PUT /api/user/profile
Authorization: Bearer <token>
```

请求：

```json
{
  "nickname": "新昵称",
  "email": "new@example.com",
  "phone": "13800000000",
  "avatarUrl": "https://example.com/avatar.png",
  "gender": 1
}
```

所有字段可选。邮箱和手机号非空时会校验唯一性。gender 取值 `0/1/2`。

用户不能通过此接口修改 `userId`、`username`、`status`、`roles`、`deleted`。

### 2.5 修改密码

```http
PUT /api/user/password
Authorization: Bearer <token>
```

请求：

```json
{
  "oldPassword": "Demo@123456",
  "newPassword": "NewDemo@123456"
}
```

校验旧密码，新旧密码不能相同，新密码重新 BCrypt。

> **限制**：本轮未实现 Token 版本控制，修改密码后旧 Token 仍然有效。前端可在修改密码成功后引导用户重新登录。

### 2.6 管理员用户列表

```http
GET /api/admin/users?pageNum=1&pageSize=10&keyword=demo&status=1&role=USER
Authorization: Bearer <admin-token>
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 1,
    "pageNum": 1,
    "pageSize": 10,
    "records": [
      {
        "userId": 1,
        "username": "demo",
        "nickname": "Demo",
        "email": "demo@example.com",
        "phone": "138****0000",
        "status": 1,
        "roles": ["USER"],
        "createdAt": "2026-07-06 10:00:00"
      }
    ]
  }
}
```

查询参数均为可选：`keyword` 匹配用户名/昵称/邮箱/手机号，`status` 筛选启用/禁用，`role` 按角色编码筛选。

### 2.7 启用或禁用用户

```http
PUT /api/admin/users/{userId}/status
Authorization: Bearer <admin-token>
```

请求：

```json
{
  "status": 0
}
```

规则：

- 只允许 `0` 或 `1`。
- 管理员不能禁用自己。
- 不能禁用最后一个有效管理员。
- 禁用后该用户新请求返回 401 "账号已被禁用"。

### 2.8 修改用户角色

```http
PUT /api/admin/users/{userId}/roles
Authorization: Bearer <admin-token>
```

请求：

```json
{
  "roles": ["USER", "API_USER"]
}
```

规则：

- 角色编码必须存在且启用。
- 去重后至少保留一个角色。
- 删除旧关联、写入新关联在同一事务中执行。
- 管理员不能移除自己的 `ADMIN` 角色。

### 2.9 刷新令牌

```http
POST /api/auth/refresh
```

请求：

```json
{
  "refreshToken": "<refresh-token>"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "<new-access-token>",
    "expiresIn": 7200,
    "refreshToken": "<new-refresh-token>",
    "refreshExpiresIn": 604800
  }
}
```

登录成功时返回的 `refreshToken` 有效期默认 7 天，用此接口换取新的 access + refresh token 对。

### 2.10 退出登录

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

退出会使当前账号的 `token_version` 递增，**所有设备**已有 Token 同时失效。

### 2.11 找回密码

```http
POST /api/auth/forgot-password
POST /api/auth/reset-password
```

发送验证码：

```json
{ "email": "user@example.com" }
```

凭验证码重置密码：

```json
{
  "email": "user@example.com",
  "code": "123456",
  "newPassword": "NewDemo@123456"
}
```

验证码有效期 5 分钟，最多尝试 5 次。配置真实 SMTP 后会发送验证码邮件，未配置时在服务端 WARN 日志输出验证码（便于本地调试）。重置成功会踢掉所有旧 Token。

### 2.12 第三方登录 (OAuth)

```http
GET  /api/auth/oauth/{provider}/authorize?redirectUri=<前端回调地址>
POST /api/auth/oauth/{provider}/callback
GET  /api/user/oauth-accounts
POST /api/user/oauth-accounts/{provider}/bind
DELETE /api/user/oauth-accounts/{oauthId}
```

**获取授权 URL：**

```
GET /api/auth/oauth/mock/authorize?redirectUri=http://localhost:5173
GET /api/auth/oauth/github/authorize?redirectUri=http://localhost:5173
```

响应：

```json
{
  "code": 200,
  "data": {
    "authorizeUrl": "https://github.com/login/oauth/authorize?client_id=...&state=...",
    "state": "<signed-state>"
  }
}
```

前端跳转到 `authorizeUrl`，用户同意后跳回 `redirectUri`，前端从 URL 上取 `code` 和 `state`。

**回调登录（换取 JWT）：**

```http
POST /api/auth/oauth/mock/callback
```

```json
{ "code": "任意字符串", "state": "<上步返回的state>" }
```

- 已绑定过该第三方账号 → 直接返回 JWT 登录。
- 第一次使用该第三方账号 → 后端自动创建本地用户 + 绑定 + 返回 JWT。

**绑定到已登录账号：**

```http
POST /api/user/oauth-accounts/mock/bind
Authorization: Bearer <token>
{ "code": "...", "state": "..." }
```

同一个 openId 不能重复绑定。

**查看绑定列表：**

```http
GET /api/user/oauth-accounts
Authorization: Bearer <token>
```

**解绑：**

```http
DELETE /api/user/oauth-accounts/{oauthId}
Authorization: Bearer <token>
```

> `provider` 可选值：`mock`（测试用，无需外部凭据）、`github`（配置见 §13）。

### 2.13 人脸识别登录

```http
POST /api/auth/face-login          (multipart, field "file")
POST /api/user/face/enroll          (multipart, field "file")  [需登录]
GET  /api/user/face                                           [需登录]
DELETE /api/user/face                                         [需登录]
```

**录入人脸：** 上传图片，提取特征并存入 `sys_face_auth`。
**人脸登录：** 上传图片，与库中已录入的人脸比对，匹配成功返回 JWT。
**查看状态：** 返回是否已录入及录入时间。
**撤销：** 将已录入的人脸记录置为不可用。

> ⚠ 当前使用 **LocalMockFaceProvider**：提取图片的 SHA-256 哈希作为"特征"，匹配方式为字节级精确比对。**同一张图片录入后可登录，不同图片一定失败**。非真实人脸识别，无活体检测。仅用于演示流程。接真实人脸服务见 §13。

### 2.14 头像上传

```http
POST /api/user/avatar              (multipart, field "file")  [需登录]
GET  /api/avatars/{storageName}    (公开访问)
```

上传成功后：
1. 文件存入 `file-storage.avatar-dir`（默认 `./data/avatars`）。
2. `file_resource` 表写入记录（business_type=AVATAR, SHA-256）。
3. `sys_user.avatar_url` 更新为 `/api/avatars/{storageName}`。

支持格式：png / jpg / jpeg / gif / webp，默认 ≤ 2 MB。

### 2.15 用户注销（自助删除账号）

```http
POST /api/user/account/cancel
Authorization: Bearer <token>
```

请求：

```json
{ "password": "当前密码" }
```

校验密码后逻辑删除当前用户、清空角色关联、踢掉所有 Token（不可恢复）。

### 2.16 角色管理（管理员）

```http
GET    /api/admin/roles                         # 角色列表（含各角色用户数）
POST   /api/admin/roles                         # 新增角色
PUT    /api/admin/roles/{roleId}                # 修改名称/描述
PUT    /api/admin/roles/{roleId}/status         # 启用/禁用
DELETE /api/admin/roles/{roleId}                # 删除角色
```

请求示例：

```json
// POST /api/admin/roles
{ "roleCode": "TESTER", "roleName": "测试员", "description": "仅测试用" }

// PUT .../status
{ "status": 0 }
```

规则：
- 角色编码必须大写字母开头，仅含大写字母、数字和下划线。
- 内置角色 `ADMIN` / `USER` / `API_USER` 不可禁用、不可删除。
- 已分配给用户的角色不可删除。

### 2.17 用户删除（管理员）

```http
DELETE /api/admin/users/{userId}
Authorization: Bearer <admin-token>
```

规则：不能删除自己、不能删除最后一个有效管理员。逻辑删除 + 清空角色关联。

### 2.18 登录审计日志（管理员）

```http
GET /api/admin/login-logs?pageNum=1&pageSize=10&username=&status=&loginType=
```

查询参数（均为可选）：

| 参数 | 说明 |
|---|---|
| `username` | 用户名模糊匹配 |
| `status` | `SUCCESS` 或 `FAIL` |
| `loginType` | `LOGIN` / `LOGOUT` / `REGISTER` / `REFRESH` / `OAUTH_LOGIN` / `FACE_LOGIN` / `PWD_RESET` |

记录每次登录、退出、注册、刷新 Token、第三方登录、人脸登录、密码重置的时间、IP、User-Agent 和结果。

## 3. 电站接口

### 3.1 电站列表

```http
GET /api/stations
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 1,
    "pageNum": 1,
    "pageSize": 10,
    "records": [
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
    ]
  }
}
```

当前接口返回全部数据，待增加 `pageNum`、`pageSize`、`keyword`、`status` 查询参数。

### 3.2 电站详情

```http
GET /api/stations/{stationId}
```

响应 `data` 与列表中的电站对象一致。

### 3.3 新增电站（管理员）

```http
POST /api/admin/stations
```

请求：

```json
{
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

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "stationId": 1
  }
}
```

`stationCode` 当前由后端自动生成。

### 3.4 修改电站（管理员）

```http
PUT /api/admin/stations/{stationId}
```

请求字段与新增接口一致。

### 3.5 删除电站（管理员）

```http
DELETE /api/admin/stations/{stationId}
```

该接口执行 MyBatis Plus 逻辑删除。

## 4. 光伏数据

### 4.1 最新实时数据

```http
GET /api/stations/{stationId}/realtime
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
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
}
```

### 4.2 历史数据

```http
GET /api/stations/{stationId}/history
```

查询参数：

| 参数 | 必填 | 格式 | 说明 |
|---|---|---|---|
| `startTime` | 否 | `yyyy-MM-dd HH:mm:ss` | 开始时间 |
| `endTime` | 否 | `yyyy-MM-dd HH:mm:ss` | 结束时间 |
| `interval` | 否 | `1min` | `1min`、`5min`、`15min` 或 `1h` |

未传时间时默认查询最近 24 小时。开始时间必须早于结束时间，最大范围为
31 天；超过 7 天时必须使用 `15min` 或 `1h`。聚合在数据库中完成。

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "time": "2026-07-06 10:00:00",
      "power": 500.2,
      "irradiance": 820.5,
      "temperature": 31.2
    }
  ]
}
```

### 4.3 上传历史数据

```http
POST /api/stations/{stationId}/data/upload
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | File | CSV 或 XLSX，最大 10 MiB、100000 行 |
| `duplicateStrategy` | String | `SKIP`（默认）、`UPDATE` 或 `FAIL` |

文件表头固定为：

```text
collectTime,powerKw,energyTodayKwh,energyTotalKwh,voltageV,currentA,irradianceWM2,moduleTemperatureC,ambientTemperatureC,humidityPercent,windSpeedMS
```

`collectTime` 和 `powerKw` 必填。响应包含 `importId`、任务状态、总数、成功数、
失败数和最多 100 条逐行错误。文件使用随机存储名，元数据和 SHA-256 写入
`file_resource`，数据分批写入 `pv_data`。

### 4.4 查询导入任务

```http
GET /api/stations/{stationId}/data/imports/{importId}
```

仅任务创建者可查询；管理员可查询全部任务。查询和实时天气要求电站 `VIEW`
权限，上传要求 `MANAGE` 权限，管理员和电站所有者不受单独授权记录限制。

## 5. 天气接口

### 5.1 当前天气

```http
GET /api/stations/{stationId}/weather/current
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "stationId": 1,
    "weather": "晴",
    "temperature": 32.0,
    "humidity": 60.0,
    "windDirection": "东南",
    "windPower": "3",
    "windSpeed": 2.5,
    "reportTime": "2026-07-06 10:30:00",
    "source": "QWEATHER",
    "cached": true
  }
}
```

### 5.2 天气预报

```http
GET /api/stations/{stationId}/weather/forecast
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "date": "2026-07-06",
      "dayWeather": "晴",
      "nightWeather": "多云",
      "dayTemp": 34.0,
      "nightTemp": 26.0,
      "humidity": 60.0,
      "windDirection": "东南",
      "windPower": "3",
      "source": "QWEATHER",
      "cached": false
    }
  ]
}
```

天气接口通过 Provider 接入 QWeather。配置仅从环境变量读取：
`WEATHER_API_KEY`、`WEATHER_BASE_URL`、`WEATHER_CONNECT_TIMEOUT`、
`WEATHER_RESPONSE_TIMEOUT` 和 `WEATHER_CACHE_MINUTES`。有效数据库缓存会直接
返回；第三方暂时不可用时，如存在过期缓存则降级返回缓存，否则返回 502。
电站必须配置经纬度。

## 6. 模型管理

### 6.1 模型列表

```http
GET /api/models?type=NUMERIC
```

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `type` | 否 | 模型类型筛选：NUMERIC/MULTIMODAL/IMAGE_TO_NUMERIC |

普通用户只返回 `ONLINE` 且未删除的模型；管理员可通过 `GET /api/admin/models` 查看全部状态。

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "modelId": 1,
      "modelName": "LSTM光伏功率预测模型",
      "modelCode": "lstm_v1",
      "modelType": "NUMERIC",
      "modelVersion": "v1.0",
      "modelStatus": "ONLINE",
      "description": "短期光伏功率预测模型"
    }
  ]
}
```

### 6.2 模型详情

```http
GET /api/models/{modelId}
```

响应 `data` 为单个模型对象。

### 6.3 新增模型（管理员）

```http
POST /api/admin/models
```

请求：

```json
{
  "modelName": "新模型",
  "modelCode": "new_model_v1",
  "modelType": "NUMERIC",
  "modelVersion": "v1.0",
  "modelStatus": "OFFLINE",
  "description": "模型说明"
}
```

### 6.4 修改模型状态（管理员）

```http
PUT /api/admin/models/{modelId}/status
```

请求：

```json
{
  "modelStatus": "ONLINE"
}
```

允许状态应限制为 `ONLINE`、`OFFLINE`、`TESTING`，当前代码尚需补枚举校验。

## 7. 预测任务

### 7.1 创建预测任务

```http
POST /api/predictions
```

请求：

```json
{
  "stationId": 1,
  "modelId": 1,
  "inputMode": "STATION_HISTORY",
  "inputStartTime": "2026-07-06 10:00:00",
  "inputEndTime": "2026-07-06 10:30:00"
}
```

后端当前同步执行以下流程：

1. 查询模型。
2. 查询电站最新 30 条光伏数据。
3. 创建 `prediction_task`。
4. 保存 `prediction_input_snapshot`。
5. 调用 FastAPI。
6. 保存 6 条 `prediction_result`。
7. 更新任务为 `SUCCESS` 或 `FAILED`。

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 1001,
    "taskStatus": "SUCCESS",
    "modelName": "lstm_v1",
    "predictions": [
      {
        "timeOffset": 5,
        "predictPower": 530.2
      }
    ],
    "costTime": 120
  }
}
```

输入少于 30 条时返回业务错误：

```json
{
  "code": 400,
  "message": "预测需要该电站连续 30 分钟的数据，当前仅有 10 条",
  "data": null
}
```

### 7.2 查询任务

```http
GET /api/predictions/{taskId}
```

任务状态：

```text
PENDING
RUNNING
SUCCESS
FAILED
```

### 7.3 查询预测结果

```http
GET /api/predictions/{taskId}/results
```

响应 `data`：

```json
[
  {
    "timeOffset": 5,
    "predictPower": 530.2
  },
  {
    "timeOffset": 10,
    "predictPower": 535.6
  }
]
```

### 7.4 预测历史

```http
GET /api/predictions/history?pageNum=1&pageSize=10&stationId=1&modelId=1&status=SUCCESS
```

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNum` | 否 | 页码，默认 1 |
| `pageSize` | 否 | 每页大小，默认 10 |
| `stationId` | 否 | 电站 ID 筛选 |
| `modelId` | 否 | 模型 ID 筛选 |
| `status` | 否 | 状态筛选：PENDING/RUNNING/SUCCESS/FAILED |

响应为分页格式，普通用户只返回自己的任务，管理员返回全部。

## 8. 综合分析

### 8.1 生成报告

```http
POST /api/analysis/report
```

请求：

```json
{
  "stationId": 1,
  "taskId": 1001,
  "title": "成都站运行分析",
  "includeWeather": true,
  "includePrediction": true
}
```

服务读取 `power_station`、最近光伏数据、天气、成功预测任务及预测点，
生成结构化报告并写入 `analysis_report`。天气缺失时报告会明确降级。

```http
GET /api/analysis/reports?pageNum=1&pageSize=10&stationId=1
GET /api/analysis/reports/{reportId}
```

普通用户仅可查看自己创建的报告，管理员可查看全部。

## 9. API 开放平台

### 9.1 申请 API Key

```http
POST /api/open/apply-key
```

请求：`{"keyName":"毕业设计联调","expireDays":90}`。完整 Key 只在本次响应的
`data.apiKey` 返回；后续列表仅返回 `apiKeyPrefix`，数据库只保存 SHA-256 哈希。

### 9.2 Key 与调用日志

```http
GET    /api/open/keys
PUT    /api/open/keys/{apiKeyId}/status
DELETE /api/open/keys/{apiKeyId}
GET    /api/open/call-logs?pageNum=1&pageSize=10
```

状态请求：`{"status":"ACTIVE"}` 或 `{"status":"DISABLED"}`。用户只能操作和查询自己的数据。

### 9.3 外部预测

```http
POST /openapi/v1/predict
X-API-KEY: <api-key>
```

请求：

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

`input` 必须恰好 30 帧且时间间隔符合模型配置。接口强制校验 Key 状态、过期时间、
每分钟限流和每日额度，创建 `OPEN_API` 预测任务、快照和结果，并记录成功或失败日志。
限流计数当前为单机内存实现，服务重启会丢失且多实例不共享，生产环境应切换 Redis。

管理员接口：`GET /api/admin/api-keys`、`PUT /api/admin/api-keys/{id}/status`、
`GET /api/admin/api-call-logs`。

## 10. 新闻接口

### 10.1 新闻列表

```http
GET /api/news?pageNum=1&pageSize=10&type=NOTICE
```

只查询 `PUBLISHED` 状态且未逻辑删除的数据。

### 10.2 新闻详情

```http
GET /api/news/{newsId}
```

### 10.3 管理新闻

```http
POST /api/admin/news
GET  /api/admin/news?pageNum=1&pageSize=10&status=DRAFT
PUT  /api/admin/news/{newsId}
PUT  /api/admin/news/{newsId}/publish
PUT  /api/admin/news/{newsId}/offline
DELETE /api/admin/news/{newsId}
```

请求：

```json
{
  "title": "模型更新通知",
  "summary": "新增 LSTM v2",
  "content": "平台新增 Transformer 模型。",
  "coverUrl": "https://example.com/cover.png",
  "newsType": "MODEL_UPDATE",
  "targetRole": "ALL"
}
```

新建状态为 `DRAFT`，允许 `DRAFT/OFFLINE -> PUBLISHED -> OFFLINE`，删除为逻辑删除。
普通用户只看到与自己角色匹配的 `PUBLISHED` 新闻。

### 10.4 站内通知

```http
GET /api/notifications?pageNum=1&pageSize=10&readStatus=0
GET /api/notifications/unread-count
PUT /api/notifications/{notificationId}/read
PUT /api/notifications/read-all
```

发布 `NOTICE/ALERT/MODEL_UPDATE` 时按目标角色批量生成通知。通知查询和已读更新均按当前用户隔离。

## 11. 前端联调检查清单

1. Axios `baseURL` 使用 `/api`，开放接口单独请求 `/openapi`。
2. 同时判断 HTTP 状态和响应体 `code`。
3. 不在前端保存数据库密码、天气 Key 或模型服务地址。
4. 功率、温度和辐照度字段按数字处理。
5. 时间选择器提交前转为 `yyyy-MM-dd HH:mm:ss`。
6. 管理端调用 `/api/admin/**`。
7. 创建预测任务时处理 400、502 和任务失败状态。
8. 接口字段变化必须同步修改本文档和 TypeScript 类型。

## 12. 凭据配置说明

### 12.1 JWT 密钥（必须）

启动后端前**必须**设置环境变量 `JWT_SECRET`，至少 256 bit（32 字节）随机字符串。

```bash
# Windows PowerShell
$env:JWT_SECRET = "your-random-secret-at-least-32-characters-long-here"

# Linux / macOS / Git Bash
export JWT_SECRET="your-random-secret-at-least-32-characters-long-here"
```

后端不会提供默认值（`application.yml` 中 `${JWT_SECRET}` 无默认值），不设置则启动失败。

其他可用 JWT 配置（可选，有默认值）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `JWT_ACCESS_TOKEN_EXPIRATION` | 7200 | Access Token 有效期（秒） |
| `JWT_REFRESH_TOKEN_EXPIRATION` | 604800 | Refresh Token 有效期（秒，默认 7 天） |

### 12.2 邮箱 SMTP（找回密码用）

找回密码需要发送验证码邮件。配置真实 SMTP 后才会真实发送，否则降级为服务端 **WARN 日志**输出验证码（本地调试可用）。

**以 QQ 邮箱为例（推荐，配置最简单）：**

1. 登录 QQ 邮箱 → 设置 → 账户 → POP3/SMTP 服务 → **开启**。
2. 获取**授权码**（16 位，不是 QQ 密码）。
3. 在启动脚本中设置环境变量：

```bash
export MAIL_HOST="smtp.qq.com"
export MAIL_PORT="587"
export MAIL_USERNAME="你的QQ号@qq.com"
export MAIL_PASSWORD="授权码（16位）"
export MAIL_FROM="你的QQ号@qq.com"
```

**以网易 163 邮箱为例：**

```bash
export MAIL_HOST="smtp.163.com"
export MAIL_PORT="465"
export MAIL_USERNAME="你的账号@163.com"
export MAIL_PASSWORD="授权码"
export MAIL_FROM="你的账号@163.com"
```

**以 Gmail 为例：**

```bash
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="你的账号@gmail.com"
export MAIL_PASSWORD="应用专用密码（App Password）"
export MAIL_FROM="你的账号@gmail.com"
```

> **未配置的后果：** 找回密码接口仍然返回 HTTP 200（防止邮箱枚举），但验证码不会发出。此时去服务端控制台找 `WARN ... 邮件降级日志: ... body=您的验证码是: 123456` 即可拿到验证码。

### 12.3 GitHub OAuth（第三方登录）

GitHub 是无门槛接入的第三方登录，只需注册 OAuth App 即可真实联调。

**步骤：**

1. 登录 GitHub → Settings → Developer settings → OAuth Apps → **New OAuth App**。
2. 填写：
   - Application name: 随便填（如 `PV-Platform-Dev`）
   - Homepage URL: `http://localhost:5173`
   - Authorization callback URL: `http://localhost:5173/api/auth/oauth/github/callback`
3. 创建后拿到 **Client ID** 和 **Client Secret**（Secrets → Generate a new client secret）。
4. 启动后端前设置环境变量：

```bash
export OAUTH_GITHUB_ENABLED="true"
export OAUTH_GITHUB_CLIENT_ID="你的Client ID"
export OAUTH_GITHUB_CLIENT_SECRET="你的Client Secret"
export OAUTH_CALLBACK_BASE_URL="http://localhost:5173"
```

**测试方法：**

1. 前端调用 `GET /api/auth/oauth/github/authorize?redirectUri=http://localhost:5173`。
2. 拿到 `authorizeUrl` → 浏览器跳转 → GitHub 授权 → 回到前端。
3. 前端从 URL 中取 `?code=...&state=...` → 调 `POST /api/auth/oauth/github/callback`。
4. 后端返回 JWT → 登录成功。

> **不配置时：** Mock provider (`mock`) 始终可用，无需任何外部凭据。把 URL 中的 `github` 换成 `mock` 即可走跳过 HTTP 的测试路径（code 随便填、openId 由后端自动生成）。

### 12.4 人脸识别

当前默认使用 `LocalMockFaceProvider`（图片 SHA-256 特征 + 精确匹配），用于演示流程，**非真实识别**。

**预留真实云服务接入位：**

```bash
export FACE_PROVIDER="remote"
export FACE_REMOTE_API_KEY="云服务API Key"
export FACE_REMOTE_BASE_URL="云服务地址"
```

实现 `FaceRecognitionProvider` 接口即可接入（见 `FaceAuthService`）。

### 12.5 其他可选配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `AVATAR_STORAGE_DIR` | `./data/avatars` | 头像存储目录 |
| `AVATAR_MAX_SIZE` | 2097152 | 头像最大字节数（2 MB） |
| `FACE_STORAGE_DIR` | `./data/faces` | 人脸图片存储目录 |
| `LOGIN_MAX_FAILURES` | 5 | 登录失败锁定阈值 |
| `LOGIN_LOCK_MINUTES` | 15 | 锁定分钟数 |
| `VERIFY_CODE_EXPIRE_MINUTES` | 5 | 验证码有效期 |
| `VERIFY_CODE_LENGTH` | 6 | 验证码位数 |

### 12.6 数据库迁移

如果已有运行中的数据库，需手动执行：

```sql
ALTER TABLE sys_user ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE TABLE sys_login_log (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL,
    username VARCHAR(64) NOT NULL,
    login_type VARCHAR(32) NOT NULL,
    login_ip VARCHAR(64),
    user_agent VARCHAR(512),
    status VARCHAR(16) NOT NULL,
    message VARCHAR(255),
    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_login_user (user_id),
    KEY idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

全新环境可直接用 `backend/src/main/resources/sql/init.sql`（注意其中 `DROP TABLE` 会清空已有数据）。

## 13. HTML 调试页

`docs/debug-user-auth.html` 一个自包含 HTML 文件，直接在浏览器打开，可调试本节全部新接口。

**用法：**

1. 确保后端运行在 `localhost:8080`。
2. 浏览器打开 `docs/debug-user-auth.html`。
3. 按页面按钮顺序操作：注册→登录（Token 自动保存）→各功能模块。
4. 接口响应以格式化 JSON 显示在对应按钮下方。

页面涵盖全部新增端点——登录/刷新/退出、OAuth mock 授权回调、人脸录入登录、找回密码、头像上传、角色管理、用户删除、登录审计日志。
