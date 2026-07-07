from app.schemas import PredictData, PredictRequest
from app.services.model_adapter import RealPredictor


class PredictorProxy:
    """按请求中的 modelName 路由到对应真实模型。"""

    def __init__(self):
        self._real = RealPredictor()

    def predict(self, request: PredictRequest) -> PredictData:
        if request.modelName not in self._real.list_models():
            raise ValueError(f"Unknown model: {request.modelName}")
        return self._real.predict(request)

    def list_models(self):
        return self._real.list_models()


predictor = PredictorProxy()
