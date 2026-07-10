# 模型服务内部开发说明

本文档面向接手 `model-service` 的研发同学，重点说明当前模型服务的代码入口、请求约束、模型分类和后续扩展方式。

## 1. 当前交付边界

- 当前服务已经接入 `20` 个模型（含云图预测模型）。
- 对外统一业务输入为 `30` 个 `1` 分钟时间步。
- 对外统一业务输出为未来 `6` 个 `5` 分钟时间步。
- 服务端不会依赖任何本地云图库。
- 数值模型只要求调用方提供 `time + power`。
- 云图相关模型要求调用方显式传云图，未传则返回 `400`。

## 2. 文档入口

- 对外 API 文档：`docs/api/MODEL_API_USAGE.md`
- 云图预测 API 文档：`docs/api/CLOUD_API_USAGE.md`
- 模型效果记录：`docs/reports/model_prediction_record.csv`
- 模型效果汇总：`docs/reports/model_prediction_summary.csv`

如果是给模型广场用户写接入说明，优先更新 API 文档；如果是给项目组内部同学接手维护，优先更新本文档。

## 3. 代码入口

### 3.1 请求与响应模型

- 文件：`app/schemas.py`
- 作用：
  - 定义 `PredictRequest`、`PredictResponse`
  - 校验 `input` 必须恰好 `30` 个点
  - 校验时间严格升序
  - 校验相邻时间必须严格相差 `1` 分钟
  - 校验 `cloudImages[].time` 不可重复

### 3.2 路由层

- 文件：`app/routers/predict.py`
- 作用：
  - 暴露 `GET /model-api/models`
  - 暴露 `POST /model-api/predict`
  - 将 `ValueError` / `FileNotFoundError` 统一转成 `400`
  - 维护对外展示的模型元信息 `_MODEL_META`

### 3.2.1 云图预测路由

- 文件：`cloud_prediction/router.py`
- 作用：
  - 暴露 `GET /cloud-api/models`
  - 暴露 `POST /cloud-api/predict`
  - 与功率预测路由（`/model-api/*`）独立，互不影响
  - 云图预测模型也注册在 `_MODEL_META` 中，类型为 `CLOUD_PREDICTION`

### 3.3 模型路由代理

- 文件：`app/services/predictor.py`
- 作用：
  - 严格按 `modelName` 路由
  - 未知模型名直接报错
  - 不再回退到 mock 或默认模型

### 3.4 实际推理适配层

- 文件：`app/services/model_adapter.py`
- 作用：
  - 加载 19 个 checkpoint
  - 完成 `30 x 1min -> 6 x 5min` 的服务端聚合
  - 为时序、视频、多模态模型分别构造输入张量
  - 加载调用方传入的图片路径或 Base64 云图
  - 返回统一的 6 步预测结果

## 4. 模型分类与调用要求

### 4.1 NUMERIC

包含：

- `DLinear`
- `PatchTST`
- `iTransformer`
- `TimeXer`
- `TimeMixer`
- `TSMixer`
- `Transformer`

调用要求：

- 只需要 `modelName + input[].time + input[].power`
- 不需要传云图

### 4.2 MULTIMODAL

包含：

- `SimVP_gSTA`
- `TAU`
- `ConvLSTM`
- `PredRNN`
- `PredRNN++`
- `E3D_LSTM`
- `swinLSTM`
- `SUNSET`

调用要求：

- 必须传 `30` 个连续 1 分钟输入点
- 必须显式传云图
- 如果某个聚合步没有任何显式云图，接口直接报 `400`

### 4.3 FUSION

包含：

- `CNN_MLP`
- `CNN_LSTM`
- `3DCNN_LSTM`
- `ConvLSTM_LSTM`

调用要求：

- 必须传 `30` 个连续 1 分钟输入点
- 必须显式传云图
- 数值分支只依赖 `power`

### 4.4 CLOUD_PREDICTION

包含：

- `SimVP_Cloud`

调用要求：

- 独立于功率预测接口，走 `/cloud-api/predict`
- 输入 10 张云图（Base64 编码），不要求功率值
- 输出 10 张预测云图（Base64 PNG），不是功率值
- 模型为 SimVP (IncepU)，与功率预测中的 `SimVP_gSTA` 不同：
  - `SimVP_gSTA`：6 帧 64×64 输入 → 6 个功率值
  - `SimVP_Cloud`：10 帧 128×128 输入 → 10 帧预测云图
- 不参与服务端 30→6 聚合，直接使用原始图片

## 5. 为什么服务端仍然做 30->6 聚合

业务侧接口已经固定为 `30` 个 1 分钟点输入，但当前可用 checkpoint 的内部时序长度仍然按较短步长工作，因此服务端在 `model_adapter.py` 中统一做一次聚合。

当前规则是：

- 先把 `30` 个输入点顺序切成 `6` 组
- 每组对 `power` 做均值聚合
- 每组时间取该组最后一个时间戳
- 时序模型直接消费这 `6` 个聚合步
- 云图模型也按相同聚合步组织图像输入

这意味着：

- 对外业务定义没有变
- 调用方仍然必须传满 `30` 个 1 分钟点
- 服务端内部负责适配当前权重

## 6. 云图传入规则

服务支持两类显式传图方式：

### 6.1 直接挂在 `input[]`

可用字段：

- `input[].cloudImage`
- `input[].cloudImageBase64`

### 6.2 顶层 `cloudImages[]`

可用字段：

- `cloudImages[].cloudImage`
- `cloudImages[].cloudImageBase64`
- `cloudImages[].source`
- `cloudImages[].file`

### 6.3 服务端取图优先级

优先级固定如下：

1. `input[].cloudImageBase64`
2. `input[].cloudImage`
3. `cloudImages[]` 中同一时间的图片记录
4. 否则报错，不再尝试本地目录兜底

### 6.4 聚合步取图规则

由于当前推理前会做 `30 -> 6` 聚合，因此一个聚合步可能覆盖多个原始分钟点。服务端目前采用的规则是：

- 在该聚合步内，从后往前查找“最后一个显式传入的云图”
- 找到即使用
- 整个聚合步都没有显式图则报错

这个逻辑在 `model_adapter.py` 的 `_resolve_group_cloud_tensor()` 中实现。

## 7. 常改文件清单

以下是最常见的修改入口：

- 新增/调整请求字段：`app/schemas.py`
- 修改接口错误码或模型说明：`app/routers/predict.py`
- 修改模型路由策略：`app/services/predictor.py`
- 修改预处理、聚合、图像装载、模型前向：`app/services/model_adapter.py`
- 修改对外文档：`docs/api/MODEL_API_USAGE.md`
- 修改云图预测对外文档：`docs/api/CLOUD_API_USAGE.md`
- 修改 Java 客户端 DTO：`../backend/src/main/java/com/example/pvplatform/client/dto/ModelPredictRequest.java`

## 8. 新增模型时的最小步骤

1. 在 `app/services/model_adapter.py` 中注册模型构建逻辑。
2. 明确该模型属于 `NUMERIC`、`MULTIMODAL` 或 `FUSION`。
3. 对齐该模型的输入张量形状，优先复用现有聚合逻辑。
4. 在 `app/routers/predict.py` 的 `_MODEL_META` 中补充模型说明。
5. 在 `GET /model-api/models` 中确认能看到新模型。
6. 用真实请求跑通 `POST /model-api/predict`。
7. 同步更新 `docs/api/MODEL_API_USAGE.md` 与本文档。

## 9. 本地联调建议

推荐最少验证以下 4 类场景：

1. `GET /model-api/models` 返回完整模型列表
2. 数值模型最小请求可成功预测
3. 云图模型带 `cloudImage` 可成功预测
4. 云图模型不带图时返回明确 `400`

如果修改了请求协议，必须同时检查：

- Swagger 展示是否与 `schemas.py` 一致
- API 文档示例是否仍然正确
- Java 客户端 DTO 是否需要同步

## 10. 当前已知约束

- 当前权重链路仍依赖服务端 `30 -> 6` 聚合，不是直接以 30 步张量喂给 checkpoint。
- 云图相关模型不支持“服务端自动去本地找图”。
- 用户上传图片路径时，路径必须在服务端运行环境中真实可读。
- 用户上传 Base64 时，必须是合法图片内容。

## 11. 维护建议

- 改接口前先看 `schemas.py`，不要只改文档。
- 改模型类别前先看 `predict.py` 中的 `_MODEL_META` 和 `model_adapter.py` 的实际前向逻辑是否一致。
- 改云图读取逻辑时，不要重新引入本地目录兜底。
- 改请求结构时，同时维护 Python 服务端和 Java 客户端 DTO，避免前后端协议漂移。
