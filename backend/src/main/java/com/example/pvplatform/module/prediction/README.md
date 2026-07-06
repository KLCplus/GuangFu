# 第三阶段：模型与预测模块开发指导

本文用于指导 Agent 完成第三阶段，范围包括：

```text
module/model
module/prediction
client/ModelServiceClient.java
client/dto/ModelPredictRequest.java
client/vo/ModelPredictResponse.java
config/WebClientConfig.java
persistence/entity/Model*
persistence/entity/Prediction*
persistence/mapper/Model*
persistence/mapper/Prediction*
docs/back_front_api.md
docs/module_back_api.md
model-service（仅在接口需要保持兼容时修改）
```

目标是将当前同步预测主链路完善为可校验、可追踪、可恢复、按用户隔离的预测业务。

## 1. 当前现状

### 已完成

- `model_info`、`model_metric`、`model_file` 已有 DO 和 Mapper。
- `prediction_task`、`prediction_input_snapshot`、`prediction_result` 已有 DO 和 Mapper。
- 模型列表、详情、新增和状态修改已接数据库。
- 创建预测任务时会查询光伏数据、保存任务和输入快照。
- Spring Boot 会调用 `POST /model-api/predict`。
- 成功后会保存 6 条预测结果。
- 调用异常时会尝试将任务改为 `FAILED`。
- FastAPI 已提供 mock 预测接口。

### 当前缺陷

- 创建预测前没有强制检查模型状态为 `ONLINE`。
- 模型新增缺少编码唯一性、类型、状态和参数校验。
- `service_model_name` 暂时直接使用 modelCode。
- 没有管理模型指标和模型文件。
- WebClient 没有显式连接、响应和读取超时。
- 对 FastAPI 业务错误和 HTTP 错误区分不足。
- 输入快照保存发生在 try/catch 之外，失败时任务状态可能不正确。
- 多表写入没有清晰事务边界。
- 历史任务没有分页和当前用户过滤。
- 查询 taskId 时没有任务归属校验，存在越权风险。
- 只检查最近 30 条数量，连续性依赖第二阶段完善。
- 当前同步请求会一直等待模型完成。
- 没有幂等键、重试、任务取消和超时状态处理。

## 2. 本阶段必须完成

1. 模型元数据完整校验。
2. 模型状态机和上下线规则。
3. 模型服务健康检查与模型列表核对。
4. WebClient 超时和错误转换。
5. 预测输入完整校验。
6. 任务状态机。
7. 输入快照和结果可靠持久化。
8. 当前用户任务隔离。
9. 预测历史分页和筛选。
10. 结果查询和任务详情。
11. 同步预测主链路稳定运行。
12. 为未来异步执行预留清晰边界。
13. 单元、数据库和模型服务集成测试。
14. 更新两份接口文档。

## 3. 本阶段不做

- 在 Java 中加载 Python 模型。
- 前端直连 FastAPI。
- 模型训练。
- GPU 调度和服务器资源监控。
- 复杂工作流编排平台。
- 未经评估直接引入消息队列。

## 4. 推荐目录

```text
module/
├── model/
│   ├── controller/
│   │   ├── ModelController.java
│   │   └── AdminModelController.java
│   ├── dto/
│   │   ├── CreateModelRequest.java
│   │   ├── UpdateModelRequest.java
│   │   └── UpdateModelStatusRequest.java
│   ├── service/
│   │   ├── ModelService.java
│   │   └── ModelValidationService.java
│   └── vo/
│       ├── ModelDetailVO.java
│       └── ModelListItemVO.java
└── prediction/
    ├── controller/
    │   └── PredictionController.java
    ├── dto/
    │   └── PredictionRequest.java
    ├── service/
    │   ├── PredictionService.java
    │   ├── PredictionInputService.java
    │   ├── PredictionExecutionService.java
    │   └── PredictionPersistenceService.java
    ├── converter/
    │   └── ModelRequestConverter.java
    └── vo/
        ├── PredictionTaskVO.java
        ├── PredictionDetailVO.java
        └── PredictionResultVO.java

client/
├── ModelServiceClient.java
├── ModelServiceHealthClient.java
├── dto/
└── vo/
```

不要让一个 `PredictionService` 无限膨胀。输入准备、外部调用、持久化和查询应逐步拆分。

## 5. 模型数据库设计

### 5.1 model_info

| 字段 | 作用 |
|---|---|
| `model_code` | 业务唯一编码 |
| `model_name` | 展示名称 |
| `model_type` | `NUMERIC/MULTIMODAL/IMAGE_TO_NUMERIC` |
| `model_version` | 版本 |
| `input_window_minutes` | 输入窗口，当前 30 |
| `input_frame_interval_seconds` | 输入间隔，当前 60 |
| `output_steps` | 输出步数，当前 6 |
| `output_step_minutes` | 输出步长，当前 5 |
| `service_model_name` | FastAPI 注册名称 |
| `api_path` | 模型服务内部路径 |
| `input_schema` | 输入结构 JSON |
| `output_schema` | 输出结构 JSON |
| `status` | `ONLINE/OFFLINE/TESTING` |
| `deleted` | 逻辑删除 |

校验：

- `model_code` 唯一且创建后原则上不修改。
- `service_model_name` 不能为空。
- 数值参数必须为正数。
- 当前统一预测页面只支持输出 6 步；变化时必须同步前端。
- JSON Schema 必须是合法 JSON。

### 5.2 model_metric

用于记录：

```text
MAE
RMSE
MAPE
R²
其他 metric_json
```

模型上线前建议至少存在一次测试集评估。指标接口可在本阶段后半部分实现，不应阻塞主预测链路。

### 5.3 model_file

只记录模型文件元数据和路径，模型文件由 Python 服务使用。

Spring Boot 不读取 `.pth/.onnx/.pkl` 内容，也不直接执行模型。

## 6. 模型状态机

允许转换：

```text
OFFLINE → TESTING
TESTING → ONLINE
TESTING → OFFLINE
ONLINE  → OFFLINE
OFFLINE → ONLINE  # 只有已验证模型才允许，可按项目简化
```

禁止任意字符串状态。

上线前检查：

1. `service_model_name` 存在。
2. FastAPI 健康检查通过。
3. 模型出现在 `/model-api/models`。
4. 输入输出配置合法。
5. 可选：存在模型指标。
6. 使用固定样例调用成功。

普通用户只能查询 `ONLINE` 模型；管理员可以查看全部状态。

## 7. 模型管理接口

### 用户模型列表

```http
GET /api/models?type=NUMERIC
```

只返回 `ONLINE` 且未删除模型。

### 模型详情

```http
GET /api/models/{modelId}
```

不要向普通用户返回内部文件路径。

### 管理员新增

```http
POST /api/admin/models
```

建议完整请求：

```json
{
  "modelCode": "lstm_v2",
  "modelName": "LSTM v2",
  "modelType": "NUMERIC",
  "modelVersion": "v2.0",
  "inputWindowMinutes": 30,
  "inputFrameIntervalSeconds": 60,
  "outputSteps": 6,
  "outputStepMinutes": 5,
  "serviceModelName": "lstm_v2",
  "apiPath": "/model-api/predict",
  "description": "短期预测模型"
}
```

新增默认状态建议为 `OFFLINE`，不允许直接信任前端传 `ONLINE`。

### 修改状态

```http
PUT /api/admin/models/{modelId}/status
```

请求：

```json
{
  "modelStatus": "ONLINE"
}
```

Service 必须执行状态转换和上线检查。

## 8. 预测任务状态机

```text
PENDING → RUNNING → SUCCESS
                  ↘ FAILED
PENDING → FAILED
```

状态含义：

| 状态 | 何时写入 |
|---|---|
| `PENDING` | 请求已校验，等待执行 |
| `RUNNING` | 开始准备输入或调用模型 |
| `SUCCESS` | 结果全部保存成功 |
| `FAILED` | 输入、调用或保存失败 |

同步版本可以从 `RUNNING` 开始，但为异步升级建议保留 `PENDING`。

规则：

- `SUCCESS` 必须存在预期数量的结果。
- `FAILED` 必须尽量记录安全的 `error_message`。
- 终态不能随意回到 RUNNING。
- 状态更新应校验当前状态，防止并发覆盖。

## 9. 创建预测任务流程

目标流程：

```text
获取当前 userId
    ↓
校验电站存在和访问权限
    ↓
查询模型并要求 ONLINE
    ↓
校验 inputMode
    ↓
获取并校验连续输入
    ↓
创建 PENDING 任务
    ↓
保存输入快照
    ↓
更新 RUNNING
    ↓
调用 FastAPI
    ↓
校验响应模型名、步数、偏移和数值
    ↓
保存结果
    ↓
更新 SUCCESS、耗时、结束时间
```

任何执行阶段异常：

```text
更新 FAILED
记录安全错误摘要
向调用方返回业务错误
```

不要在任务创建前进行耗时很长的外部调用，否则失败请求没有 taskId 可追踪。

## 10. 输入模式

数据库定义：

```text
STATION_HISTORY
FILE_UPLOAD
MANUAL_INPUT
OPEN_API
```

本阶段优先完整支持 `STATION_HISTORY`。

不同模式应由策略实现：

```java
public interface PredictionInputProvider {
    boolean supports(String inputMode);
    List<ModelInputFrame> load(PredictionContext context);
}
```

不要在 Service 写不断增长的 `if/else`。

### STATION_HISTORY 规则

- 恰好 30 帧。
- 升序排列。
- 每帧间隔 60 秒。
- 最后一帧不能过旧。
- 功率、温度、辐照度非空。
- 数值不能是 NaN 或 Infinity。
- 功率、辐照度不能为负。

## 11. 输入快照

`prediction_input_snapshot` 保存实际发送给模型的数据，而不是查询条件。

要求：

- taskId 创建后再保存。
- 一条输入一条快照。
- `(task_id, point_time)` 唯一。
- 必须与发给 FastAPI 的数据一致。
- 原始扩展字段可放 `raw_data`。

保存快照的目的：

- 复现预测。
- 排查错误。
- 对比模型版本。
- 审计开放 API 调用。

## 12. 模型服务调用

接口完整定义：

```text
docs/module_back_api.md
```

请求：

```http
POST /model-api/predict
```

响应必须校验：

- `code == 200`。
- `data` 非空。
- `modelName` 与请求匹配。
- `predictions` 数量等于 `outputSteps`。
- timeOffset 为 `5/10/15/20/25/30`。
- predictPower 为有限非负数。
- costTime 非负。

不能只判断 HTTP 200。

## 13. WebClient 要求

建议：

```text
连接超时：3 秒
响应超时：30 秒
读取超时：30 秒
```

配置集中在 `WebClientConfig`，不在每个请求里重复。

错误转换：

| 情况 | 业务处理 |
|---|---|
| 连接拒绝 | 502 模型服务不可用 |
| 连接/读取超时 | 502 模型预测超时 |
| HTTP 4xx | 400 模型输入不合法或模型不存在 |
| HTTP 5xx | 502 模型服务执行失败 |
| 响应无法解析 | 502 模型服务响应异常 |

日志记录：

- taskId。
- modelCode/serviceModelName。
- 调用耗时。
- HTTP 状态。
- 不记录完整 30 帧敏感输入。

## 14. 结果持久化

`prediction_result` 每个任务正常保存 6 条：

```text
time_offset_minutes: 5,10,15,20,25,30
predict_time: 最后一帧时间 + offset
predict_power_kw: 模型值
```

规则：

- `(task_id, time_offset_minutes)` 唯一。
- 保存前验证无重复 offset。
- 批量保存，避免 6 次无控制写入。
- 结果全部成功后才能将任务设为 `SUCCESS`。
- 后续真实值到达时回填 `actual_power_kw`、`error_value`、`error_rate`。

## 15. 事务边界

外部 HTTP 调用不应被一个数据库事务长时间包围。

建议拆分：

1. 短事务：创建任务和输入快照。
2. 无数据库事务：调用 FastAPI。
3. 短事务：保存结果并更新 SUCCESS。
4. 短事务：异常时更新 FAILED。

注意 Spring 同类方法内部调用不会触发 `@Transactional` 代理，事务方法应放到独立 Bean 或通过合适代理调用。

## 16. 用户和权限

- 创建任务使用 SecurityContext 中 userId。
- 用户只能访问自己的任务。
- 管理员可以查看全部任务。
- 查询 taskId 时必须同时校验归属，不能先返回再判断。
- 用户必须有目标电站 VIEW 权限。
- 管理员模型管理接口要求 ADMIN。
- 开放 API 创建的任务使用 `OPEN_API` 模式并关联 API Key 日志。

建议查询条件：

```text
普通用户：task_id = ? AND user_id = currentUserId
管理员：task_id = ?
```

## 17. 前端业务接口

### 创建任务

```http
POST /api/predictions
```

### 任务详情

```http
GET /api/predictions/{taskId}
```

建议详情同时返回：

- taskNo。
- stationId/stationName。
- modelId/modelName/modelCode。
- inputMode。
- status。
- create/start/finish 时间。
- costTime。
- errorMessage（安全摘要）。

### 预测结果

```http
GET /api/predictions/{taskId}/results
```

### 预测历史

```http
GET /api/predictions/history?pageNum=1&pageSize=10&stationId=1&modelId=1&status=SUCCESS
```

必须分页并按当前用户过滤。

## 18. 同步到异步的升级路径

第一版可保持同步以完成联调，但代码边界必须允许以后改为：

```text
POST /api/predictions
    ↓
创建 PENDING 任务并立即返回 taskId
    ↓
后台 Worker 执行
    ↓
前端轮询 GET /api/predictions/{taskId}
```

不要现在就引入消息队列，除非同步流程和测试已经稳定。

## 19. 异常规范

| 场景 | code | message |
|---|---:|---|
| 模型不存在 | 404 | 模型不存在 |
| 模型未上线 | 400 | 模型当前不可用 |
| 电站无权限 | 403 | 无权访问该电站 |
| 输入不足 | 400 | 预测需要连续 30 分钟数据 |
| 输入断档 | 400 | 预测输入数据时间不连续 |
| 任务不存在或无权访问 | 404 | 预测任务不存在 |
| 模型连接失败 | 502 | 模型服务不可用 |
| 模型超时 | 502 | 模型预测超时 |
| 模型响应异常 | 502 | 模型服务响应异常 |

为防止枚举资源，不应向普通用户区分“任务不存在”和“任务属于别人”。

## 20. 测试要求

### ModelService

- 普通用户列表只返回 ONLINE。
- 管理员可查看全部。
- modelCode 重复失败。
- 非法类型、状态和数值参数失败。
- 非法状态转换失败。
- FastAPI 不健康时不能上线。

### PredictionInput

- 30 条连续数据成功。
- 少于 30 条失败。
- 30 条但间隔不连续失败。
- 空值、负值、过旧数据失败。
- 输入升序发送。

### ModelServiceClient

- 正常响应解析。
- 连接失败转换 502。
- 超时转换 502。
- HTTP 4xx/5xx 正确转换。
- 空 data、错误步数、重复 offset 被拒绝。

可使用 MockWebServer 或 WireMock，测试不能依赖真实 FastAPI。

### PredictionService

- 成功保存任务、30 条快照、6 条结果。
- 结果保存完才 SUCCESS。
- 外部调用失败时任务 FAILED。
- 快照保存失败时任务状态可追踪。
- 普通用户不能查询他人任务。
- 历史只返回当前用户且分页正确。
- 管理员查询全部成功。

## 21. 实施顺序

### 第 1 步：模型管理

- DTO/VO 明确化。
- 完整参数校验。
- ONLINE 列表过滤。
- 状态机和上线检查。

### 第 2 步：模型 Client

- 超时配置。
- 错误分类。
- 响应校验。
- MockWebServer 测试。

### 第 3 步：预测输入

- 接入第二阶段连续性校验。
- 建立 InputProvider。
- 固化请求转换。

### 第 4 步：任务持久化

- 调整状态机。
- 拆分事务。
- 批量保存快照和结果。
- 失败可靠回写。

### 第 5 步：权限和查询

- 当前用户任务隔离。
- 电站权限。
- 历史分页和筛选。
- 管理员查询。

### 第 6 步：测试和文档

- 完成单元与集成测试。
- 更新 `docs/back_front_api.md`。
- 更新 `docs/module_back_api.md`。
- 执行 `mvn clean test`。

## 22. 验收标准

- [ ] 普通用户只能看到 ONLINE 模型。
- [ ] 模型状态转换和上线检查有效。
- [ ] WebClient 有明确超时。
- [ ] 模型 HTTP/业务/解析错误被正确区分。
- [ ] 输入严格满足连续 30 帧。
- [ ] 每个成功任务保存 30 条快照和 6 条结果。
- [ ] 失败任务可靠写入 FAILED 和错误摘要。
- [ ] 普通用户不能访问其他用户任务。
- [ ] 历史接口已分页和筛选。
- [ ] 任务状态机无非法转换。
- [ ] 测试不依赖真实 FastAPI。
- [ ] 两份接口文档与代码一致。
- [ ] `mvn clean test` 通过。

## 23. 交给 Agent 的任务模板

```text
请按照 backend/src/main/java/com/example/pvplatform/module/prediction/README.md
完成第三阶段“模型与预测”开发。

要求：
1. 先检查工作树，避开用户、光伏数据等其他 Agent 的改动。
2. 先完成模型校验和 ModelServiceClient，再调整预测主流程。
3. 前端不能直接调用 FastAPI。
4. 模型必须 ONLINE，输入必须连续 30 帧。
5. 任务、快照、结果和失败状态必须可靠持久化。
6. 普通用户只能访问自己的任务。
7. 使用 MockWebServer/WireMock 测试模型调用异常，不依赖真实服务。
8. 更新 docs/back_front_api.md 和 docs/module_back_api.md。
9. 执行 mvn clean test，并报告改动、接口、测试和剩余限制。
```
