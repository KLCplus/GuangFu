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

当前 JWT 和 API Key 强制校验尚未完成，接口可能暂时放行。前端仍应按正式格式封装。

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
  "password": "123456",
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

说明：

- 用户名不能为空且不能重复。
- 邮箱必须符合邮箱格式。
- 后端使用 BCrypt 保存密码哈希。
- 注册后默认关联 `USER` 角色。

### 2.2 用户登录

```http
POST /api/auth/login
```

请求：

```json
{
  "username": "demo",
  "password": "123456"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "mock-jwt-token-1",
    "userInfo": {
      "userId": 1,
      "username": "demo",
      "role": "USER"
    }
  }
}
```

当前状态：账号和密码从数据库校验，但 token 仍为 mock；JWT 完成后响应字段保持不变。

### 2.3 当前用户资料

```http
GET /api/user/profile
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "demo",
    "email": "demo@example.com",
    "role": "USER",
    "status": "ENABLE"
  }
}
```

当前状态：JWT 接入前临时返回数据库第一位有效用户，后续必须根据 Token 中的 userId 查询。

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
| `interval` | 否 | `1min` | 当前仅保留参数，尚未实现聚合 |

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
| `file` | File | CSV 或 Excel |

当前状态：只返回 mock 导入数量，尚未解析文件和写入数据库。

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
    "reportTime": "2026-07-06T10:30:00"
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
      "dayTemp": 34,
      "nightTemp": 26
    }
  ]
}
```

当前状态：两个天气接口均为 mock，待接入第三方天气 API 和 `weather_data`。

## 6. 模型管理

### 6.1 模型列表

```http
GET /api/models
```

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
GET /api/predictions/history
```

当前返回全部任务概要，后续应增加分页和当前用户过滤。

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
  "includeWeather": true,
  "includePrediction": true
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "stationName": "成都示范光伏电站",
    "reportTime": "2026-07-06T10:35:00",
    "summary": "当前电站运行状态良好。",
    "weatherAnalysis": "当前天气有利于发电。",
    "predictionAnalysis": "未来功率小幅上升。",
    "suggestion": "继续监测天气变化。"
  }
}
```

当前状态：报告内容为模板 mock，尚未写入 `analysis_report`。

## 9. API 开放平台

### 9.1 申请 API Key

```http
POST /api/open/apply-key
```

当前返回 mock Key。正式实现时 Key 明文只在创建时返回一次，数据库仅保存哈希。

### 9.2 查询调用日志

```http
GET /api/open/call-logs
```

当前返回 mock 日志，后续从 `api_call_log` 分页查询。

### 9.3 外部预测

```http
POST /openapi/v1/predict
X-API-KEY: <api-key>
```

请求：

```json
{
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

当前接口会转发至 FastAPI，但 API Key 尚未强制校验。

## 10. 新闻接口

### 10.1 新闻列表

```http
GET /api/news
```

只查询 `PUBLISHED` 状态且未逻辑删除的数据。

### 10.2 新闻详情

```http
GET /api/news/{newsId}
```

### 10.3 发布新闻（管理员）

```http
POST /api/admin/news
```

请求：

```json
{
  "title": "模型更新通知",
  "content": "平台新增 Transformer 模型。",
  "type": "MODEL_UPDATE"
}
```

当前新增接口直接以 `PUBLISHED` 状态发布。

### 10.4 修改新闻（管理员）

```http
PUT /api/admin/news/{newsId}
```

请求字段与发布接口相同。

### 10.5 删除新闻（管理员）

```http
DELETE /api/admin/news/{newsId}
```

执行逻辑删除。

## 11. 前端联调检查清单

1. Axios `baseURL` 使用 `/api`，开放接口单独请求 `/openapi`。
2. 同时判断 HTTP 状态和响应体 `code`。
3. 不在前端保存数据库密码、天气 Key 或模型服务地址。
4. 功率、温度和辐照度字段按数字处理。
5. 时间选择器提交前转为 `yyyy-MM-dd HH:mm:ss`。
6. 管理端调用 `/api/admin/**`。
7. 创建预测任务时处理 400、502 和任务失败状态。
8. 接口字段变化必须同步修改本文档和 TypeScript 类型。
