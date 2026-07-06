# Spring Boot—模型服务接口文档

本文定义 Spring Boot 业务后端与 Python FastAPI 模型服务之间的内部接口。

> 这些接口只允许 Spring Boot 调用。Vue Web、小程序和外部 API 用户不得直接访问模型服务。

## 1. 调用关系

```text
前端 POST /api/predictions
        │
        ▼
Spring Boot PredictionService
        │
        ├── 查询 model_info
        ├── 查询最近 30 条 pv_data
        ├── 保存 prediction_task
        ├── 保存 prediction_input_snapshot
        │
        ▼
ModelServiceClient
        │ POST /model-api/predict
        ▼
FastAPI MockPredictor / 后续真实模型
        │
        ▼
Spring Boot 保存 prediction_result
```

## 2. 服务配置

Spring Boot：

```yaml
model-service:
  base-url: ${MODEL_SERVICE_BASE_URL:http://localhost:9000}
```

本地模型服务：

```text
http://localhost:9000
```

Docker Compose 内部地址：

```text
http://model-service:9000
```

Spring Boot 代码位置：

```text
backend/src/main/java/com/example/pvplatform/client/
├── ModelServiceClient.java
├── dto/ModelPredictRequest.java
└── vo/ModelPredictResponse.java
```

FastAPI 代码位置：

```text
model-service/app/
├── main.py
├── schemas.py
├── routers/predict.py
└── services/predictor.py
```

## 3. 健康检查

```http
GET /health
```

响应：

```json
{
  "status": "ok",
  "service": "model-service"
}
```

用途：

- 启动验证。
- Docker/Kubernetes 健康检查。
- 后端管理端展示模型服务是否可用。

当前 `ModelServiceClient` 尚未主动调用该接口，可在后续增加健康检查方法。

## 4. 模型列表

```http
GET /model-api/models
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "modelName": "lstm_v1",
      "modelType": "NUMERIC",
      "description": "基于历史功率数据的短期预测模型"
    },
    {
      "modelName": "transformer_v1",
      "modelType": "NUMERIC",
      "description": "基于 Transformer 的光伏功率预测模型"
    },
    {
      "modelName": "multimodal_v1",
      "modelType": "MULTIMODAL",
      "description": "多模态光伏预测模型"
    }
  ]
}
```

数据职责：

- `model_info` 是业务系统中的模型元数据和上下线状态来源。
- `/model-api/models` 表示当前模型服务实际可执行的模型。
- 后续上线模型时，应校验数据库的 `service_model_name` 是否存在于该列表。

## 5. 模型预测

### 5.1 请求

```http
POST /model-api/predict
Content-Type: application/json
```

完整请求：

```json
{
  "modelName": "lstm_v1",
  "input": [
    {
      "time": "2026-07-06 10:00:00",
      "power": 500.2,
      "temperature": 31.2,
      "irradiance": 820.5
    },
    {
      "time": "2026-07-06 10:01:00",
      "power": 501.8,
      "temperature": 31.3,
      "irradiance": 825.0
    }
  ]
}
```

字段：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `modelName` | string | 是 | 与 `model_info.service_model_name` 对应 |
| `input` | array | 是 | 正常预测传 30 帧，按时间升序 |
| `input[].time` | string | 是 | `yyyy-MM-dd HH:mm:ss` |
| `input[].power` | number | 是 | 非负，单位 kW |
| `input[].temperature` | number | 是 | 单位 ℃ |
| `input[].irradiance` | number | 是 | 非负，单位 W/m² |

正式数值模型输入规则：

```text
输入窗口：30 分钟
帧间隔：1 分钟
输入帧数：30
输出步数：6
输出间隔：5 分钟
输出范围：未来 30 分钟
```

当前 FastAPI Schema 只要求至少一帧；Spring Boot 已要求数据库中至少有 30 条数据。下一步应在模型服务中增加：

1. 必须恰好 30 帧。
2. 时间必须严格升序。
3. 相邻帧必须间隔 1 分钟。
4. 不允许 NaN、Infinity 或非法负值。
5. `modelName` 必须存在且已加载。

### 5.2 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "modelName": "lstm_v1",
    "predictions": [
      {
        "timeOffset": 5,
        "predictPower": 530.2
      },
      {
        "timeOffset": 10,
        "predictPower": 535.6
      },
      {
        "timeOffset": 15,
        "predictPower": 540.1
      },
      {
        "timeOffset": 20,
        "predictPower": 542.4
      },
      {
        "timeOffset": 25,
        "predictPower": 545.0
      },
      {
        "timeOffset": 30,
        "predictPower": 548.3
      }
    ],
    "costTime": 120
  }
}
```

字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | integer | 业务码，成功为 200 |
| `message` | string | 业务信息 |
| `data.modelName` | string | 实际执行模型 |
| `data.predictions` | array | 6 个预测点 |
| `timeOffset` | integer | 相对最后输入时间的分钟偏移 |
| `predictPower` | number | 预测功率，单位 kW |
| `costTime` | integer | 模型推理耗时，单位 ms |

Spring Boot 将预测时间计算为：

```text
最后一帧输入时间 + timeOffset
```

并写入 `prediction_result.predict_time`。

### 5.3 当前 mock 算法

当前模型服务不加载真实模型，以最后一帧功率为基准生成稳定递增结果：

```text
predictPower = lastPower × (1 + stepIndex × 0.006)
```

替换真实模型时必须保持请求和响应结构兼容，避免修改 Spring Boot 和前端。

## 6. Spring Boot 处理流程

`POST /api/predictions` 当前同步执行：

1. 根据 `modelId` 查询 `model_info`。
2. 根据 `stationId` 查询 `pv_data` 最新 30 条记录。
3. 少于 30 条时返回 400，不调用模型。
4. 创建状态为 `RUNNING` 的 `prediction_task`。
5. 保存 30 条 `prediction_input_snapshot`。
6. 使用 `ModelServiceClient` 发起 POST 请求。
7. 保存 6 条 `prediction_result`。
8. 更新任务为 `SUCCESS`，写入 `cost_time_ms`。
9. 调用异常时更新任务为 `FAILED` 并保存错误信息。

任务状态：

| 状态 | 含义 |
|---|---|
| `PENDING` | 已创建，等待执行 |
| `RUNNING` | 正在准备数据或推理 |
| `SUCCESS` | 推理和结果保存成功 |
| `FAILED` | 校验、调用或保存失败 |

## 7. 错误处理

### 7.1 Spring Boot 无法连接 FastAPI

对前端返回：

```json
{
  "code": 502,
  "message": "模型服务调用失败，请确认 FastAPI 服务已启动",
  "data": null
}
```

同时应保证 `prediction_task.status = FAILED`。

### 7.2 FastAPI 参数校验失败

FastAPI 当前可能返回 HTTP 422：

```json
{
  "detail": [
    {
      "loc": ["body", "input"],
      "msg": "List should have at least 1 item",
      "type": "too_short"
    }
  ]
}
```

后续建议为 FastAPI 增加统一异常处理，使内部接口也返回：

```json
{
  "code": 400,
  "message": "模型输入不合法",
  "data": null
}
```

### 7.3 超时配置（已实施）

WebClient 已在 `WebClientConfig` 中配置超时：

```text
连接超时：3 秒（ChannelOption.CONNECT_TIMEOUT_MILLIS）
响应超时：30 秒（HttpClient.responseTimeout）
```

真实模型超过 30 秒时不应无限等待，应改为异步任务或按模型配置单独超时。

## 8. 内部接口安全

开发环境可使用本机端口，部署环境应满足：

1. FastAPI 只监听内部网络，不配置公网入口。
2. 防火墙只允许 Spring Boot 访问模型服务端口。
3. 可增加内部请求签名或服务间 Token。
4. 输入日志不得记录用户密钥或完整敏感文件。
5. 模型路径、文件路径和异常堆栈不得直接返回前端。
6. `/docs` 在生产环境应关闭或限制访问。

## 9. 模型接入规范

新增模型时：

1. 在 FastAPI 注册模型名。
2. 在 `model_info` 新增唯一 `model_code`。
3. `service_model_name` 与 FastAPI 名称一致。
4. 记录输入窗口、输入帧间隔、输出步数和输出间隔。
5. 增加输入转换器，不在路由函数中堆叠模型特定逻辑。
6. 增加固定样例和自动化测试。
7. 验证异常输入、模型不存在和推理异常。
8. 模型验证完成后才将状态改为 `ONLINE`。

建议模型服务内部结构：

```text
services/
├── registry.py          # 模型注册和按名称查找
├── base_predictor.py    # 统一预测接口
├── numeric_predictor.py
├── multimodal_predictor.py
└── predictor.py         # 当前 mock 实现
```

## 10. 联调命令

启动模型服务：

```bash
cd model-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 9000
```

健康检查：

```bash
curl http://localhost:9000/health
```

预测测试：

```bash
curl -X POST http://localhost:9000/model-api/predict \
  -H "Content-Type: application/json" \
  -d '{
    "modelName": "lstm_v1",
    "input": [
      {
        "time": "2026-07-06 10:00:00",
        "power": 500.2,
        "temperature": 31.2,
        "irradiance": 820.5
      }
    ]
  }'
```

Windows PowerShell 可使用：

```powershell
$body = @{
  modelName = "lstm_v1"
  input = @(
    @{
      time = "2026-07-06 10:00:00"
      power = 500.2
      temperature = 31.2
      irradiance = 820.5
    }
  )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Uri "http://localhost:9000/model-api/predict" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

## 11. 后续改进清单

- [x] FastAPI 强制校验 30 帧和时间连续性。
- [x] WebClient 增加连接、响应和读取超时。
- [x] 增加模型不存在、输入异常的统一错误格式。
- [x] Spring Boot 调用前校验模型状态为 `ONLINE`。
- [x] 增加 requestId/taskId，贯穿两端日志。
- [x] 增加健康检查和模型列表同步。
- [ ] 增加真实模型 Registry 和按模型适配输入。
- [x] 为预测成功、失败、超时补集成测试。
- [ ] 预测耗时较长时改为异步队列。

## 12. 第四阶段后端补充

- 分析报告：`POST /api/analysis/report`、`GET /api/analysis/reports`、
  `GET /api/analysis/reports/{reportId}`，使用真实电站、光伏、天气与预测数据。
- 开放平台：JWT 用于 Key 管理，`/openapi/v1/**` 使用独立 API Key 认证；
  Key 明文仅创建时返回，持久化前缀和 SHA-256 哈希。
- 开放预测复用 `PredictionExecutionService` 与 `PredictionPersistenceService`，
  创建 `OPEN_API` 任务、输入快照、预测结果和调用日志。
- 新闻支持 `DRAFT/PUBLISHED/OFFLINE`、分页、类型及目标角色过滤。
- 通知接口支持分页、未读数、单条已读和全部已读；重要新闻发布时按角色批量创建。
- 当前限流和日额度采用单机内存计数，生产多实例部署应替换为 Redis 原子计数。
