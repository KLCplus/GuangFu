from datetime import datetime, timedelta

from pydantic import BaseModel, Field, model_validator


_TIME_FORMATS = (
    "%Y-%m-%d %H:%M:%S",
    "%Y-%m-%dT%H:%M:%S",
    "%Y-%m-%d %H:%M",
    "%Y/%m/%d %H:%M:%S",
    "%Y/%m/%d %H:%M",
)


def _parse_time(value: str) -> datetime:
    text = value.strip()
    for fmt in _TIME_FORMATS:
        try:
            return datetime.strptime(text, fmt)
        except ValueError:
            continue
    raise ValueError(f"Invalid time format: {value}")


class InputFrame(BaseModel):
    time: str
    power: float = Field(ge=0)
    cloudImage: str | None = None
    cloudImageBase64: str | None = None


class CloudImageFrame(BaseModel):
    time: str
    cloudImage: str | None = None
    cloudImageBase64: str | None = None
    source: str | None = None
    file: str | None = None

    @model_validator(mode="after")
    def validate_cloud_image_payload(self):
        if not any([self.cloudImage, self.cloudImageBase64, self.source, self.file]):
            raise ValueError("cloud image payload must include cloudImage, cloudImageBase64, source, or file")
        return self


class PredictRequest(BaseModel):
    modelName: str = Field(min_length=1)
    input: list[InputFrame] = Field(min_length=30, max_length=30)
    cloudImages: list[CloudImageFrame] | None = None

    @model_validator(mode="after")
    def validate_input_sequence(self):
        times = [_parse_time(frame.time) for frame in self.input]
        for i in range(1, len(times)):
            if times[i] <= times[i - 1]:
                raise ValueError("input timestamps must be strictly ascending")
            if times[i] - times[i - 1] != timedelta(minutes=1):
                raise ValueError("input timestamps must be continuous 1-minute intervals")
        if self.cloudImages:
            seen = set()
            for item in self.cloudImages:
                ts = _parse_time(item.time)
                if ts in seen:
                    raise ValueError("cloudImages timestamps must be unique")
                seen.add(ts)
        return self


class Prediction(BaseModel):
    timeOffset: int
    predictPower: float


class PredictData(BaseModel):
    modelName: str
    predictions: list[Prediction]
    costTime: int


class PredictResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: PredictData


class ModelInfo(BaseModel):
    modelName: str
    modelType: str
    description: str


class ModelListResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: list[ModelInfo]
