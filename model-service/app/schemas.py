from pydantic import BaseModel, Field


class InputFrame(BaseModel):
    time: str
    power: float = Field(ge=0)
    temperature: float
    irradiance: float = Field(ge=0)


class ImageFrame(BaseModel):
    time: str
    image: str = Field(min_length=1)


class PredictRequest(BaseModel):
    modelName: str = Field(min_length=1)
    input: list[InputFrame] = Field(min_length=30, max_length=30)
    inputImages: list[ImageFrame] = Field(min_length=30, max_length=30)


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
