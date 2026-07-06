from fastapi import FastAPI

from app.routers.predict import router as predict_router

app = FastAPI(
    title="PV Model Service",
    description="光伏功率预测模型服务（骨架版本）",
    version="0.1.0",
)
app.include_router(predict_router)


@app.get("/health", tags=["health"])
def health() -> dict[str, str]:
    return {"status": "ok", "service": "model-service"}
