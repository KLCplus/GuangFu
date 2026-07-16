"""云图预测路由"""

from time import perf_counter

from fastapi import APIRouter, HTTPException

from cloud_prediction.schemas import (
    CloudModelInfo,
    CloudModelListResponse,
    CloudPredictData,
    CloudPredictRequest,
    CloudPredictResponse,
)
from cloud_prediction.cloud_predictor import predict_cloud_images, list_cloud_models

router = APIRouter(prefix="/cloud-api", tags=["cloud-prediction"])

_CLOUD_MODEL_META = {
    "SimVP_Cloud": ("CLOUD_PREDICTION", "SimVP (IncepU) 云图预测模型，输入10张云图预测未来10张云图"),
}


@router.post("/predict", response_model=CloudPredictResponse)
def cloud_predict(request: CloudPredictRequest) -> CloudPredictResponse:
    if request.modelName not in list_cloud_models():
        raise HTTPException(status_code=400, detail=f"Unknown cloud model: {request.modelName}")
    try:
        t0 = perf_counter()
        results = predict_cloud_images(request.inputImages)
        cost = int((perf_counter() - t0) * 1000)

        predictions = [
            {
                "frameIndex": i,
                "image": item["image"],
                "cloudCoverage": item.get("cloudCoverage", 0.0),
                "confidence": item.get("confidence", 0.0),
            }
            for i, item in enumerate(results)
        ]
        data = CloudPredictData(
            modelName=request.modelName,
            predictions=predictions,
            costTime=cost,
        )
        return CloudPredictResponse(data=data)
    except (ValueError, FileNotFoundError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.get("/models", response_model=CloudModelListResponse)
def cloud_models() -> CloudModelListResponse:
    data = []
    for name in list_cloud_models():
        mtype, desc = _CLOUD_MODEL_META.get(name, ("UNKNOWN", name))
        data.append(CloudModelInfo(modelName=name, modelType=mtype, description=desc))
    return CloudModelListResponse(data=data)
