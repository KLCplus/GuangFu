# API 文档

当前版本均为 mock 接口。业务 API 基址为 `http://localhost:8080`，模型服务基址为 `http://localhost:9000`。

## 1. 统一返回格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

常用业务码：`200` 成功、`400` 参数错误、`401` 未认证、`403` 无权限、`404` 不存在、`500` 服务异常、`502` 模型服务调用失败。分页数据放在 `data` 中：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 2,
    "pageNum": 1,
    "pageSize": 10,
    "records": []
  }
}
```

## 2. 前端调用 Spring Boot

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 认证 | POST | `/api/auth/register` | 注册 |
| 认证 | POST | `/api/auth/login` | 登录，骨架返回 mock token |
| 用户 | GET | `/api/user/profile` | 当前用户 |
| 电站 | GET | `/api/stations` | 电站列表 |
| 电站 | GET | `/api/stations/{stationId}` | 电站详情 |
| 电站管理 | POST | `/api/admin/stations` | 新增电站 |
| 电站管理 | PUT | `/api/admin/stations/{stationId}` | 修改电站 |
| 电站管理 | DELETE | `/api/admin/stations/{stationId}` | 删除电站 |
| 光伏数据 | GET | `/api/stations/{stationId}/realtime` | 实时数据 |
| 光伏数据 | GET | `/api/stations/{stationId}/history` | 历史数据 |
| 光伏数据 | POST | `/api/stations/{stationId}/data/upload` | `multipart/form-data` 上传 |
| 天气 | GET | `/api/stations/{stationId}/weather/current` | 当前天气 |
| 天气 | GET | `/api/stations/{stationId}/weather/forecast` | 天气预报 |
| 模型 | GET | `/api/models` | 模型列表 |
| 模型 | GET | `/api/models/{modelId}` | 模型详情 |
| 模型管理 | POST | `/api/admin/models` | 新增模型元信息 |
| 模型管理 | PUT | `/api/admin/models/{modelId}/status` | 修改模型状态 |
| 预测 | POST | `/api/predictions` | 创建并同步执行预测任务 |
| 预测 | GET | `/api/predictions/{taskId}` | 任务状态 |
| 预测 | GET | `/api/predictions/{taskId}/results` | 预测结果 |
| 预测 | GET | `/api/predictions/history` | 预测历史 |
| 分析 | POST | `/api/analysis/report` | 生成综合报告 |
| 开放平台 | POST | `/api/open/apply-key` | 申请 mock API Key |
| 开放平台 | GET | `/api/open/call-logs` | 调用日志 |
| 开放接口 | POST | `/openapi/v1/predict` | 外部预测，预留 `X-API-KEY` |
| 新闻 | GET | `/api/news` | 新闻列表 |
| 新闻 | GET | `/api/news/{newsId}` | 新闻详情 |
| 新闻管理 | POST | `/api/admin/news` | 发布新闻 |
| 新闻管理 | PUT | `/api/admin/news/{newsId}` | 修改新闻 |
| 新闻管理 | DELETE | `/api/admin/news/{newsId}` | 删除新闻 |

### 登录示例

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "demo",
  "password": "123456"
}
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "mock-jwt-token",
    "userInfo": {
      "userId": 1,
      "username": "demo",
      "role": "USER"
    }
  }
}
```

### 创建预测任务

```http
POST /api/predictions
Content-Type: application/json
```

```json
{
  "stationId": 1,
  "modelId": 1,
  "inputMode": "STATION_HISTORY",
  "inputStartTime": "2026-07-06 10:00:00",
  "inputEndTime": "2026-07-06 10:30:00"
}
```

骨架后端生成 30 帧 mock 历史数据，调用 FastAPI 后同步返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 1001,
    "taskStatus": "SUCCESS",
    "modelName": "lstm_v1",
    "predictions": [
      { "timeOffset": 5, "predictPower": 523.93 },
      { "timeOffset": 10, "predictPower": 527.05 }
    ],
    "costTime": 1
  }
}
```

生产版本可将该流程改为异步，状态始终使用 `PENDING / RUNNING / SUCCESS / FAILED`。

### 综合报告

```json
{
  "stationId": 1,
  "taskId": 1001,
  "includeWeather": true,
  "includePrediction": true
}
```

### 开放预测

```http
POST /openapi/v1/predict
X-API-KEY: pv_mock_key_replace_after_jwt_enabled
Content-Type: application/json
```

请求体与下方模型服务请求一致。Spring Boot 校验请求并转发，客户端不直接访问 FastAPI。

## 3. Spring Boot 调用模型服务

### 健康检查

```http
GET /health
```

```json
{
  "status": "ok",
  "service": "model-service"
}
```

### 模型列表

```http
GET /model-api/models
```

返回 `lstm_v1`、`transformer_v1`、`multimodal_v1` 三个 mock 模型。

### 模型预测

```http
POST /model-api/predict
Content-Type: application/json
```

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

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "modelName": "lstm_v1",
    "predictions": [
      { "timeOffset": 5, "predictPower": 503.2 },
      { "timeOffset": 10, "predictPower": 506.2 },
      { "timeOffset": 15, "predictPower": 509.2 },
      { "timeOffset": 20, "predictPower": 512.2 },
      { "timeOffset": 25, "predictPower": 515.21 },
      { "timeOffset": 30, "predictPower": 518.21 }
    ],
    "costTime": 1
  }
}
```

`modelName` 决定后续真实模型路由；`input` 按时间升序，当前允许至少 1 帧，真实模型接入后应要求连续 30 帧。所有功率和辐照度必须为非负数。
