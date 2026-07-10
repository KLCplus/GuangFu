# Model Service

FastAPI 模型预测服务。当前使用可复现的 mock 算法，以最后一帧功率为基准生成未来 5～30 分钟的 6 个预测点，不加载真实模型。

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
