import math
import os
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
    "SimVP_Cloud",
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

        # 放宽校验：inputImages 可以为空（功率模型）或数量不等于 input（云图模型）
        if request.inputImages and len(request.inputImages) == len(request.input):
            for numeric_frame, image_frame in zip(request.input, request.inputImages):
                if numeric_frame.time != image_frame.time:
                    raise ValueError("inputImages timestamps must match input timestamps")

        powers = [frame.power for frame in request.input]
        recent = powers[-6:]
        baseline = recent[-1]

        # 线性回归斜率
        n = len(recent)
        mean_x = (n - 1) / 2
        mean_y = sum(recent) / n
        slope = sum((i - mean_x) * (y - mean_y) for i, y in enumerate(recent)) / sum((i - mean_x) ** 2 for i in range(n))

        # 历史波动率（标准差），用于生成合理噪声幅度
        if len(recent) > 1:
            variance = sum((y - mean_y) ** 2 for y in recent) / (n - 1)
            volatility = variance ** 0.5
        else:
            volatility = baseline * 0.02

        # 每个模型有不同的特性曲线
        model_idx = MODEL_NAMES.index(request.modelName)
        # 模型差异：趋势衰减系数（越远预测越平缓）、振荡幅度、噪声种子
        decay = 0.6 + (model_idx % 4) * 0.12       # 0.60~0.96
        osc_amp = volatility * (0.3 + (model_idx % 3) * 0.2)
        osc_freq = 0.8 + (model_idx % 5) * 0.15
        noise_scale = volatility * 0.4
        # 用 model_idx 做固定随机种子，保证同一模型同一输入结果可复现
        import random as _rand
        rng = _rand.Random(hash(request.modelName) % 2**32 + int(baseline * 100) + len(powers))

        predictions = []
        for step, offset in enumerate(range(5, 31, 5), start=1):
            # 趋势衰减：越远斜率越小
            trend = slope * step * (decay ** (step - 1))
            # 正弦振荡模拟云遮挡周期性
            oscillation = osc_amp * math.sin(step * osc_freq)
            # 随机噪声
            noise = rng.gauss(0, noise_scale)
            value = baseline + trend + oscillation + noise
            predictions.append(
                Prediction(timeOffset=offset, predictPower=round(max(0.0, value), 2))
            )
        return PredictData(modelName=request.modelName, predictions=predictions, costTime=8)

    def list_models(self):
        return MODEL_NAMES


# TSL 模型列表（有权重的真实模型）
TSL_MODEL_NAMES = {
    "DLinear", "PatchTST", "iTransformer",
    "TimeXer", "TimeMixer", "TSMixer", "Transformer",
}


class HybridPredictor:
    """混合预测器：TSL 模型使用真实推理，其他模型使用 mock。

    TSL 模型（DLinear, PatchTST, iTransformer, TimeXer, TimeMixer, TSMixer, Transformer）
    在 /root/shixun/GuangFu/model-service/TSL 下有训练好的权重，必须做到真实调用。
    其他模型暂无权重，使用 mock 预测器保证接口可用。
    """

    def __init__(self):
        self._mock = PredictorProxy()
        self._real = None  # 延迟初始化，避免不必要的内存占用

    def _get_real(self):
        if self._real is None:
            from app.services.model_adapter import RealPredictor
            self._real = RealPredictor()
        return self._real

    def predict(self, request: PredictRequest) -> PredictData:
        if request.modelName in TSL_MODEL_NAMES:
            return self._get_real().predict(request)
        return self._mock.predict(request)

    def list_models(self):
        return MODEL_NAMES


# 始终使用混合预测器：TSL 模型真实调用，其他模型 mock
predictor = HybridPredictor()
