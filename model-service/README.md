# Model Service

FastAPI 模型预测服务。当前通过 `checkpoints/` 加载真实模型权重，统一提供未来 5～30 分钟的 6 个功率预测点。

## 启动

```bash
python -m venv .venv
# Windows: .venv\Scripts\activate
# Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 9000
```

## 接口

- `GET /health`
- `GET /model-api/models`
- `POST /model-api/predict`
- Swagger：`http://localhost:9000/docs`

请求格式：

```json
{
  "modelName": "iTransformer",
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

正式请求必须包含 30 个连续 1 分钟时间步。`MULTIMODAL` 和 `FUSION` 模型还需要通过 `input[].cloudImage/cloudImageBase64` 或顶层 `cloudImages` 显式传入云图。
