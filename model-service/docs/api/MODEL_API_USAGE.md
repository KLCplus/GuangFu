# 模型广场 API 调用文档

本文档面向购买模型广场 API 的用户，说明如何调用光伏功率预测模型服务。

## 1. 接口概览

- 服务基础地址：`http://<your-host>:9000`
- Swagger 文档：`http://<your-host>:9000/docs`
- 健康检查：`GET /health`
- 查询模型列表：`GET /model-api/models`
- 发起预测：`POST /model-api/predict`

## 2. 调用流程

推荐按下面顺序调用：

1. 先调用 `GET /model-api/models`，确认当前可用模型名称。
2. 选择一个模型名称作为 `modelName`。
3. 按要求组织 `30` 个输入时间步，调用 `POST /model-api/predict`。
4. 从返回结果中读取未来 `6` 个预测点。

## 3. 查询模型列表

### 请求

```http
GET /model-api/models
```

### 返回示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "modelName": "iTransformer",
      "modelType": "NUMERIC",
      "description": "iTransformer 通道独立 Transformer 模型"
    },
    {
      "modelName": "CNN_LSTM",
      "modelType": "FUSION",
      "description": "CNN-LSTM 图像CNN+数值LSTM融合模型"
    }
  ]
}
```

### 说明

- `modelName`：预测时必须原样填写，区分大小写。
- `modelType`：
  - `NUMERIC`：时序模型
  - `MULTIMODAL`：云图时空模型
  - `FUSION`：云图 + 数值融合模型

## 4. 发起预测

### 请求地址

```http
POST /model-api/predict
Content-Type: application/json
```

### 请求体格式

```json
{
  "modelName": "iTransformer",
  "input": [
    {
      "time": "2017-08-13 13:28:00",
      "power": 23.64
    }
  ]
}
```

上面只是字段结构示意，不是可直接发送的完整请求。

- 如果调用的是时序模型，最少只需要传 `modelName + input[].time + input[].power`。
- 如果调用的是云图相关模型，必须继续看下面的“可选云图字段”并显式传图。
- 无论调用哪一类模型，正式请求都必须传满 `30` 个连续 `1` 分钟时间步。

## 5. 请求字段要求

### 顶层字段

- `modelName`
  - 类型：`string`
  - 必填：是
  - 含义：要调用的模型名称
  - 要求：必须来自 `GET /model-api/models` 返回的 `modelName`

- `input`
  - 类型：`array`
  - 必填：是
  - 含义：输入时间序列

### input 数组内每个元素

- `time`
  - 类型：`string`
  - 必填：是
  - 含义：该时间步的时间戳
  - 推荐格式：`YYYY-MM-DD HH:MM:SS`
  - 示例：`2017-08-13 13:28:00`

- `power`
  - 类型：`number`
  - 必填：是
  - 含义：该分钟的历史功率
  - 约束：`>= 0`

### 云图字段

对于 `MULTIMODAL` 和 `FUSION` 类型模型，调用方必须显式传云图。

支持两种方式，二选一或混用都可以：

1. 在 `input` 的每个时间步里直接带图
2. 在顶层额外传 `cloudImages` 数组

#### 方式 A：在 `input` 中直接带图

- `input[].cloudImage`
  - 类型：`string`
  - 必填：否
  - 含义：该时间步对应云图文件路径

- `input[].cloudImageBase64`
  - 类型：`string`
  - 必填：否
  - 含义：该时间步对应云图的 Base64 字符串

#### 方式 B：顶层 `cloudImages`

- `cloudImages`
  - 类型：`array`
  - 必填：否
  - 含义：按时间补充云图

- `cloudImages[].time`
  - 类型：`string`
  - 必填：是
  - 含义：这张图对应的时间

- `cloudImages[].cloudImage`
  - 类型：`string`
  - 必填：否
  - 含义：云图文件路径

- `cloudImages[].cloudImageBase64`
  - 类型：`string`
  - 必填：否
  - 含义：云图的 Base64 字符串

- `cloudImages[].source`
  - 类型：`string`
  - 必填：否
  - 含义：兼容已有样本结构的云图源路径

- `cloudImages[].file`
  - 类型：`string`
  - 必填：否
  - 含义：兼容已有样本结构的云图文件名或路径

服务端取图优先级：

1. `input[].cloudImageBase64`
2. `input[].cloudImage`
3. `cloudImages` 中同一时间的图片
4. 如果一个聚合步完全没有显式云图，接口会直接报错

## 6. 强制格式要求

为了保证预测结果有效，调用方必须遵循以下业务格式：

1. `input` 必须传 `30` 个时间步。
2. 每个时间步表示 `1` 分钟。
3. 时间必须按从早到晚升序排列。
4. 相邻两个时间步必须严格相差 `1` 分钟。
5. `power` 不能为负数。
6. `modelName` 必须与模型列表接口返回完全一致。
7. 如果传了 `cloudImages`，同一个时间不能重复传两张图。
8. 对 `MULTIMODAL` 和 `FUSION` 模型，必须提供足够覆盖各聚合步的云图。

不建议的请求方式：

- 少传几个点再让服务补齐
- 时间乱序
- 时间跨度不是连续 `30` 分钟
- 字段缺失
- 模型名称手写猜测

## 7. 云图模型的调用说明

对于 `MULTIMODAL` 和 `FUSION` 类型模型，调用方必须显式传自己的云图。

这意味着：

- 调用方如果传了图片，服务会优先使用调用方传入的图片
- 调用方不需要传 `temperature`
- 调用方不需要传 `irradiance`
- 但时间戳必须真实有效
- 服务端不会再假设本地存在云图库
- 如果某个聚合步没有对应云图，接口会直接报错

因此，时间字段必须尽量准确，云图也必须随请求一起提供。

## 8. 返回格式

### 返回示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "modelName": "iTransformer",
    "predictions": [
      {
        "timeOffset": 5,
        "predictPower": 23.42
      },
      {
        "timeOffset": 10,
        "predictPower": 23.42
      },
      {
        "timeOffset": 15,
        "predictPower": 23.41
      },
      {
        "timeOffset": 20,
        "predictPower": 23.40
      },
      {
        "timeOffset": 25,
        "predictPower": 23.40
      },
      {
        "timeOffset": 30,
        "predictPower": 23.38
      }
    ],
    "costTime": 123
  }
}
```

### 返回字段说明

- `code`：业务状态码，正常为 `200`
- `message`：返回消息，正常为 `success`
- `data.modelName`：本次调用的模型名称
- `data.predictions`：预测结果数组
- `data.predictions[].timeOffset`：预测步长，单位为分钟，固定为 `5/10/15/20/25/30`
- `data.predictions[].predictPower`：该步长对应的预测功率
- `data.costTime`：本次推理耗时，单位毫秒

## 9. curl 调用示例

```bash
curl -X POST "http://<your-host>:9000/model-api/predict" \
  -H "Content-Type: application/json" \
  -d '{
    "modelName": "iTransformer",
    "input": [
      {"time": "2017-08-13 13:28:00", "power": 23.64},
      {"time": "2017-08-13 13:29:00", "power": 23.60},
      {"time": "2017-08-13 13:30:00", "power": 23.55},
      {"time": "2017-08-13 13:31:00", "power": 23.52},
      {"time": "2017-08-13 13:32:00", "power": 23.58},
      {"time": "2017-08-13 13:33:00", "power": 23.70},
      {"time": "2017-08-13 13:34:00", "power": 23.62},
      {"time": "2017-08-13 13:35:00", "power": 23.57},
      {"time": "2017-08-13 13:36:00", "power": 23.60},
      {"time": "2017-08-13 13:37:00", "power": 23.54},
      {"time": "2017-08-13 13:38:00", "power": 23.52},
      {"time": "2017-08-13 13:39:00", "power": 23.53},
      {"time": "2017-08-13 13:40:00", "power": 23.56},
      {"time": "2017-08-13 13:41:00", "power": 23.54},
      {"time": "2017-08-13 13:42:00", "power": 23.62},
      {"time": "2017-08-13 13:43:00", "power": 23.59},
      {"time": "2017-08-13 13:44:00", "power": 23.52},
      {"time": "2017-08-13 13:45:00", "power": 23.45},
      {"time": "2017-08-13 13:46:00", "power": 23.46},
      {"time": "2017-08-13 13:47:00", "power": 23.51},
      {"time": "2017-08-13 13:48:00", "power": 23.47},
      {"time": "2017-08-13 13:49:00", "power": 23.43},
      {"time": "2017-08-13 13:50:00", "power": 23.43},
      {"time": "2017-08-13 13:51:00", "power": 23.39},
      {"time": "2017-08-13 13:52:00", "power": 23.34},
      {"time": "2017-08-13 13:53:00", "power": 23.34},
      {"time": "2017-08-13 13:54:00", "power": 23.49},
      {"time": "2017-08-13 13:55:00", "power": 23.49},
      {"time": "2017-08-13 13:56:00", "power": 23.40},
      {"time": "2017-08-13 13:57:00", "power": 23.45}
    ]
  }'
```

### 显式传云图片段示例

```json
{
  "modelName": "CNN_LSTM",
  "input": [
    {"time": "2017-08-13 13:28:00", "power": 23.64, "cloudImage": "/data/cloud/20170813132800.jpg"},
    {"time": "2017-08-13 13:29:00", "power": 23.60},
    {"time": "2017-08-13 13:30:00", "power": 23.55}
  ],
  "cloudImages": [
    {"time": "2017-08-13 13:29:00", "cloudImage": "/data/cloud/20170813132900.jpg"},
    {"time": "2017-08-13 13:30:00", "cloudImageBase64": "<base64-image-data>"}
  ]
}
```

说明：

- 这只是云图字段写法片段，正式请求仍然必须传满 `30` 个连续 1 分钟时间步
- 上例里 `13:28` 这张图直接取 `input[].cloudImage`
- `13:29` 这张图从顶层 `cloudImages` 里取
- `13:30` 这张图从顶层 `cloudImages[].cloudImageBase64` 里取
- 其他没显式传图的时间步，如果所属聚合步也没有其他显式云图，接口会直接报错

## 10. 常见错误

### 1. 422 参数校验失败

常见原因：

- 漏传 `modelName`
- `input` 不是数组
- `power` 为负数
- 字段类型错误

### 2. 模型名称错误

常见原因：

- `modelName` 不存在
- 大小写不一致

正确做法：

- 先调用 `GET /model-api/models`
- 复制返回的 `modelName`

当前服务行为：

- 如果 `modelName` 不在模型列表中，接口会直接返回错误
- 不会自动切换到其他模型
- 不会回退到默认模型

### 3. 时间格式或时间序列不规范

常见原因：

- 时间格式不规范
- 时间不是升序
- 时间不是连续 `30` 个 `1` 分钟步长

正确做法：

- 统一使用 `YYYY-MM-DD HH:MM:SS`
- 保证 `30` 个点连续且升序

### 4. 云图模型未显式传图

常见原因：

- 调用了 `MULTIMODAL` 或 `FUSION` 模型，但请求里没有任何可用云图
- 只传了少量云图，导致某个聚合步没有图可用
- `cloudImage` 路径不可读
- `cloudImageBase64` 不是合法图片内容

当前服务行为：

- 接口直接返回 `400`
- 不会自动去服务端本地目录找图
- 不会自动补默认图片

## 11. 推荐接入规则

如果你是模型广场客户，建议接入时直接采用以下规则：

- 单次请求只调用一个模型
- 每次固定传 `30` 个点
- 每个点间隔 `1` 分钟
- 先查模型列表，再选模型调用
- 模型切换时只改 `modelName`，其余输入结构保持一致

这样接入最稳定，也最方便后续扩展到不同模型。
