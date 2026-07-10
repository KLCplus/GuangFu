"""云图预测 Schema 定义"""

from typing import List

from pydantic import BaseModel, Field


class CloudPredictRequest(BaseModel):
    modelName: str = Field(default="SimVP_Cloud", min_length=1)
    inputImages: List[str] = Field(min_length=10, max_length=10,
                                    description="10 张输入云图，每张为 base64 编码字符串或文件路径")


class CloudPredictionFrame(BaseModel):
    frameIndex: int
    image: str


class CloudPredictData(BaseModel):
    modelName: str
    predictions: List[CloudPredictionFrame]
    costTime: int


class CloudPredictResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: CloudPredictData


class CloudModelInfo(BaseModel):
    modelName: str
    modelType: str
    description: str


class CloudModelListResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: List[CloudModelInfo]
