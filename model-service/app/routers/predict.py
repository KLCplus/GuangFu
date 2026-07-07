from fastapi import APIRouter, HTTPException

from app.schemas import (
    ModelInfo,
    ModelListResponse,
    PredictRequest,
    PredictResponse,
)
from app.services.predictor import predictor

router = APIRouter(prefix="/model-api", tags=["model"])

# 模型描述信息
_MODEL_META = {
    # 时序基线模型
    "DLinear":      ("NUMERIC",   "DLinear 线性分解时序预测模型"),
    "PatchTST":     ("NUMERIC",   "PatchTST 分块 Transformer 时序预测模型"),
    "iTransformer": ("NUMERIC",   "iTransformer 通道独立 Transformer 模型"),
    "TimeXer":      ("NUMERIC",   "TimeXer 外生变量增强时序预测模型"),
    "TimeMixer":    ("NUMERIC",   "TimeMixer 多尺度混合时序预测模型"),
    "TSMixer":      ("NUMERIC",   "TSMixer 时序混合 MLP 预测模型"),
    "Transformer":  ("NUMERIC",   "标准 Transformer 时序预测模型"),
    # 云图时空递归模型
    "SimVP_gSTA":   ("MULTIMODAL", "SimVP (gSTA) 视频预测模型"),
    "TAU":          ("MULTIMODAL", "TAU 时空聚合注意力视频预测模型"),
    "ConvLSTM":     ("MULTIMODAL", "ConvLSTM 卷积长短期记忆时空模型"),
    "PredRNN":      ("MULTIMODAL", "PredRNN 时空预测递归网络"),
    "PredRNN++":    ("MULTIMODAL", "PredRNN++ 时空预测递归网络增强版"),
    "E3D_LSTM":    ("MULTIMODAL", "E3D-LSTM 三维门控时空记忆网络"),
    "swinLSTM":     ("MULTIMODAL", "SwinLSTM Swin Transformer + LSTM 时空模型"),
    "SUNSET":       ("MULTIMODAL", "SUNSET 斯坦福 CNN 太阳能预测模型"),
    # 多模态融合模型
    "CNN_MLP":       ("FUSION", "CNN-MLP 图像+数值融合预测模型"),
    "CNN_LSTM":      ("FUSION", "CNN-LSTM 图像CNN+数值LSTM融合模型"),
    "3DCNN_LSTM":    ("FUSION", "3DCNN-LSTM 三维CNN+LSTM时空融合模型"),
    "ConvLSTM_LSTM": ("FUSION", "ConvLSTM-LSTM 卷积LSTM+数值LSTM融合模型"),
}


@router.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    try:
        return PredictResponse(data=predictor.predict(request))
    except (ValueError, FileNotFoundError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.get("/models", response_model=ModelListResponse)
def models() -> ModelListResponse:
    data = []
    for name in predictor.list_models():
        mtype, desc = _MODEL_META.get(name, ("UNKNOWN", name))
        data.append(ModelInfo(modelName=name, modelType=mtype, description=desc))
    return ModelListResponse(data=data)
