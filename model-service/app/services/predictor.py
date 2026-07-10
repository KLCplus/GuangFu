from statistics import mean

from app.schemas import PredictData, PredictRequest, Prediction


MODEL_NAMES = [
    "DLinear",
    "PatchTST",
    "iTransformer",
    "TimeXer",
    "TimeMixer",
    "TSMixer",
    "Transformer",
    "SimVP_gSTA",
    "TAU",
    "ConvLSTM",
    "PredRNN",
    "PredRNN++",
    "E3D_LSTM",
    "swinLSTM",
    "SUNSET",
    "CNN_MLP",
    "CNN_LSTM",
    "3DCNN_LSTM",
    "ConvLSTM_LSTM",
]


class PredictorProxy:
    """Mock predictor for local front/back integration.

    The contract stays the same as the real model service:
    - GET /model-api/models returns the supported model names.
    - POST /model-api/predict returns six prediction points.

    This intentionally avoids loading checkpoints or running PyTorch so backend
    prediction APIs can be tested with stable local data.
    """

    def predict(self, request: PredictRequest) -> PredictData:
        if request.modelName not in MODEL_NAMES:
            raise ValueError(f"Unknown model: {request.modelName}")

        powers = [frame.power for frame in request.input]
        recent = powers[-6:]
        baseline = mean(recent)
        slope = (recent[-1] - recent[0]) / max(len(recent) - 1, 1)
        model_bias = (MODEL_NAMES.index(request.modelName) % 5) * 0.4

        predictions = [
            Prediction(
                timeOffset=offset,
                predictPower=round(max(0.0, baseline + slope * step + model_bias), 2),
            )
            for step, offset in enumerate(range(5, 31, 5), start=1)
        ]
        return PredictData(modelName=request.modelName, predictions=predictions, costTime=8)

    def list_models(self):
        return MODEL_NAMES


predictor = PredictorProxy()
