# 云图预测 API 调用文档

本文档面向购买云图预测 API 的用户，说明如何调用 SimVP 云图预测模型，输入 10 张历史云图，预测未来 10 张云图。

## 1. 接口概览

- 服务基础地址：`http://<your-host>:9000`
- Swagger 文档：`http://<your-host>:9000/docs`
- 健康检查：`GET /health`
- 查询云图模型列表：`GET /cloud-api/models`
- 发起云图预测：`POST /cloud-api/predict`

云图预测与光伏功率预测是两套独立接口，互不影响：

- 光伏功率预测：`/model-api/predict`（输入 30 个时间步，输出 6 个功率值）
- 云图预测：`/cloud-api/predict`（输入 10 张云图，输出 10 张预测云图）

## 2. 调用流程

1. 调用 `GET /cloud-api/models`，确认可用云图预测模型。
2. 选择一个模型名称作为 `modelName`。
3. 准备 10 张连续时间步的云图，编码为 Base64 字符串。
4. 调用 `POST /cloud-api/predict`，获取 10 张预测云图。

## 3. 查询云图模型列表

### 请求

```http
GET /cloud-api/models
```

### 返回示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "modelName": "SimVP_Cloud",
      "modelType": "CLOUD_PREDICTION",
      "description": "SimVP (IncepU) 云图预测模型，输入10张云图预测未来10张云图"
    }
  ]
}
```

### 说明

- `modelName`：预测时必须原样填写，区分大小写。
- `modelType`：`CLOUD_PREDICTION`，表示云图预测类模型。

## 4. 发起云图预测

### 请求地址

```http
POST /cloud-api/predict
Content-Type: application/json
```

### 请求体格式

```json
{
  "modelName": "SimVP_Cloud",
  "inputImages": [
    "<base64-image-1>",
    "<base64-image-2>",
    "<base64-image-3>",
    "<base64-image-4>",
    "<base64-image-5>",
    "<base64-image-6>",
    "<base64-image-7>",
    "<base64-image-8>",
    "<base64-image-9>",
    "<base64-image-10>"
  ]
}
```

## 5. 请求字段要求

### 顶层字段

- `modelName`
  - 类型：`string`
  - 必填：是
  - 含义：要调用的云图预测模型名称
  - 要求：必须来自 `GET /cloud-api/models` 返回的 `modelName`

- `inputImages`
  - 类型：`array[string]`
  - 必填：是
  - 长度：必须恰好 `10` 个元素
  - 含义：10 张历史云图，按时间顺序排列

### inputImages 数组内每个元素

- 类型：`string`
- 格式：Base64 编码的图片数据（支持 PNG / JPEG）
- 也支持 Data URI 格式：`data:image/png;base64,xxxx`
- 也支持服务端可读的文件路径
- 图片分辨率：任意（服务端会自动 resize 到 128×128）
- 顺序：必须按时间从早到晚排列

## 6. 服务端预处理

服务端对每张输入图片做以下预处理（与训练时一致）：

1. 转为灰度图（单通道）
2. 涂黑左上角时间戳区域
3. 涂黑右下角相机信息区域
4. 缩放到 128×128
5. 归一化到 [0, 1] 范围

调用方无需自行做预处理，直接传原始图片即可。

## 7. 返回格式

### 返回示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "modelName": "SimVP_Cloud",
    "predictions": [
      {
        "frameIndex": 0,
        "image": "<base64-png>"
      },
      {
        "frameIndex": 1,
        "image": "<base64-png>"
      },
      ...
      {
        "frameIndex": 9,
        "image": "<base64-png>"
      }
    ],
    "costTime": 350
  }
}
```

### 返回字段说明

- `code`：业务状态码，正常为 `200`
- `message`：返回消息，正常为 `success`
- `data.modelName`：本次调用的模型名称
- `data.predictions`：预测结果数组，共 `10` 帧
- `data.predictions[].frameIndex`：预测帧序号，从 `0` 到 `9`
- `data.predictions[].image`：预测云图的 Base64 编码 PNG 图片
- `data.costTime`：本次推理耗时，单位毫秒

## 8. curl 调用示例

### Python 生成请求示例

```python
import base64
import requests
from pathlib import Path

# 准备 10 张云图
imgs = sorted(Path("/data/cloud").glob("*.jpg"))[:10]
b64_list = []
for p in imgs:
    with open(p, "rb") as f:
        b64_list.append(base64.b64encode(f.read()).decode("utf-8"))

# 调用预测
resp = requests.post(
    "http://<your-host>:9000/cloud-api/predict",
    json={
        "modelName": "SimVP_Cloud",
        "inputImages": b64_list
    },
    timeout=60
)

# 保存预测结果
data = resp.json()["data"]
for p in data["predictions"]:
    img_bytes = base64.b64decode(p["image"])
    with open(f"pred_frame_{p['frameIndex']:02d}.png", "wb") as f:
        f.write(img_bytes)

print(f"预测完成，{len(data['predictions'])} 帧，耗时 {data['costTime']}ms")
```

## 9. 模型说明

### SimVP_Cloud

- **模型架构**：SimVP (IncepU)
- **输入**：10 帧灰度云图（128×128）
- **输出**：10 帧预测云图（128×128，PNG 格式）
- **模型参数**：hid_S=64, hid_T=256, N_S=4, N_T=6
- **权重文件**：`epoch-epoch=099.ckpt`
- **推理速度**：约 300-400ms（GPU）/ 1-2s（CPU）

## 10. 常见错误

### 1. 422 参数校验失败

常见原因：

- `inputImages` 数量不是 10 个
- `modelName` 为空
- 字段类型错误

### 2. 模型名称错误

常见原因：

- `modelName` 不在云图模型列表中
- 误用了光伏功率模型名称（如 `SimVP_gSTA`）

正确做法：

- 先调用 `GET /cloud-api/models` 获取可用模型
- 云图预测用 `SimVP_Cloud`，不是 `SimVP_gSTA`

### 3. 图片解码失败

常见原因：

- Base64 字符串不是合法图片
- 图片文件路径不存在或不可读

## 11. 与光伏功率预测的区别

| 维度 | 光伏功率预测 | 云图预测 |
|------|------------|---------|
| 接口 | `POST /model-api/predict` | `POST /cloud-api/predict` |
| 模型列表 | `GET /model-api/models` | `GET /cloud-api/models` |
| 输入 | 30 个时间步（含功率值） | 10 张云图（Base64） |
| 输出 | 6 个功率预测值 | 10 张预测云图（Base64 PNG） |
| 模型类型 | NUMERIC / MULTIMODAL / FUSION | CLOUD_PREDICTION |
| 实时性 | 支持实时预测 | 不支持实时（无实时云图源） |
