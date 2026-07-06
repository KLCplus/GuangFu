from time import perf_counter

from app.schemas import PredictData, PredictRequest, Prediction


class MockPredictor:
    """稳定、可复现的 mock 推理器，后续可替换为真实模型适配器。"""

    def predict(self, request: PredictRequest) -> PredictData:
        started_at = perf_counter()
        base_power = request.input[-1].power
        predictions = [
            Prediction(
                timeOffset=offset,
                predictPower=round(max(0.0, base_power * (1 + index * 0.006)), 2),
            )
            for index, offset in enumerate(range(5, 31, 5), start=1)
        ]
        elapsed_ms = max(1, int((perf_counter() - started_at) * 1000))
        return PredictData(
            modelName=request.modelName,
            predictions=predictions,
            costTime=elapsed_ms,
        )


predictor = MockPredictor()
