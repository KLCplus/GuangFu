from fastapi import APIRouter

from app.schemas import (
    ModelInfo,
    ModelListResponse,
    PredictRequest,
    PredictResponse,
)
from app.services.predictor import predictor

router = APIRouter(prefix="/model-api", tags=["model"])


@router.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    return PredictResponse(data=predictor.predict(request))


@router.get("/models", response_model=ModelListResponse)
def models() -> ModelListResponse:
    return ModelListResponse(
        data=[
            ModelInfo(
                modelName="lstm_v1",
                modelType="NUMERIC",
                description="基于历史功率数据的短期预测模型",
            ),
            ModelInfo(
                modelName="transformer_v1",
                modelType="NUMERIC",
                description="基于 Transformer 的光伏功率预测模型",
            ),
            ModelInfo(
                modelName="multimodal_v1",
                modelType="MULTIMODAL",
                description="多模态光伏预测模型",
            ),
        ]
    )
