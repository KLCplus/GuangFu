"""云图预测适配器 - SimVP (IncepU)

从 Cloud-map-prediction 项目迁移，使用 SimVP v1 (IncepU) 模型，
输入 10 张云图，输出 10 张预测云图。

与 model-service 中现有的 SimVP_gSTA 区别：
- model_type: IncepU (非 gSTA)
- 输入: 10 帧 128×128 灰度云图 (非 6 帧 64×64)
- 输出: 10 帧预测云图 (非 6 个功率值)
- 权重: epoch-epoch=099.ckpt (PyTorch Lightning 格式)
"""

import base64
import io
import os
import sys
from pathlib import Path
from typing import List, Optional

import numpy as np
import torch
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms
from PIL import Image, ImageFile

# ---------------------------------------------------------------------------
# 路径设置
# ---------------------------------------------------------------------------
_BASE = Path(__file__).resolve().parents[1]  # model-service/
_CLOUD_DIR = _BASE / "cloud_prediction"
OpenSTL_PATH = str(_BASE / "OpenSTL")  # 复用顶层已验证可用的 OpenSTL
CKPT_DIR = str(_CLOUD_DIR / "checkpoints")

if OpenSTL_PATH not in sys.path:
    sys.path.insert(0, OpenSTL_PATH)

from openstl.models.simvp_model import SimVP_Model

ImageFile.LOAD_TRUNCATED_IMAGES = True
torch.set_float32_matmul_precision('high')

# ---------------------------------------------------------------------------
# 常量
# ---------------------------------------------------------------------------
PRE_SEQ_LENGTH = 10
AFT_SEQ_LENGTH = 10
IMG_SIZE = 128
CKPT_FILENAME = "epoch-epoch=099.ckpt"


# ---------------------------------------------------------------------------
# 模型管理器（单例）
# ---------------------------------------------------------------------------
class CloudModelManager:
    def __init__(self):
        self.model = None
        self.device = None
        self.is_loaded = False

    def load_model(self, ckpt_path: str):
        if self.is_loaded:
            return

        print(f"[CloudPrediction] 正在加载模型: {ckpt_path}")

        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

        # 直接构建 SimVP_Model (IncepU)，不走 BaseExperiment，避免 timm 依赖问题
        in_shape = (PRE_SEQ_LENGTH, 1, IMG_SIZE, IMG_SIZE)
        self.model = SimVP_Model(
            in_shape,
            hid_S=64,
            hid_T=256,
            N_S=4,
            N_T=6,
            model_type='IncepU',
        )

        # 加载权重（兼容 Lightning ckpt 和纯 state_dict）
        ckpt = torch.load(ckpt_path, map_location=self.device)
        state_dict = ckpt.get('state_dict', ckpt)
        # 去掉 Lightning 的 "model." 前缀
        state_dict = {k.replace('model.', '', 1) if k.startswith('model.') else k: v
                      for k, v in state_dict.items()}
        self.model.load_state_dict(state_dict)
        self.model.to(self.device).eval()

        self.is_loaded = True
        print(f"[CloudPrediction] 模型加载完成，设备: {self.device}")

    def predict(self, input_tensor: torch.Tensor) -> torch.Tensor:
        if not self.is_loaded:
            raise RuntimeError("模型尚未加载")
        with torch.no_grad():
            input_tensor = input_tensor.to(self.device)
            pred_raw = self.model(input_tensor)
            return pred_raw.cpu()


cloud_model_manager = CloudModelManager()


# ---------------------------------------------------------------------------
# 图像预处理
# ---------------------------------------------------------------------------
def _decode_image(image_source: str) -> torch.Tensor:
    """从 base64 或文件路径解码图片，做与训练一致的预处理，返回 [1, H, W] 张量。

    预处理步骤（与 simvp_preprocess.py 一致）：
    1. 转灰度
    2. 涂黑左上角时间戳（前 8% 高、前 55% 宽）
    3. 涂黑右下角相机戳（后 15% 高、后 35% 宽）
    4. resize 到 128×128
    5. ToTensor 转为 [0,1] 范围
    """
    transform = transforms.ToTensor()

    if image_source.startswith('data:image'):
        # data URI: data:image/png;base64,xxxx
        header, data = image_source.split(',', 1)
        raw = base64.b64decode(data)
        img = Image.open(io.BytesIO(raw))
    elif os.path.isfile(image_source):
        img = Image.open(image_source)
    else:
        # 纯 base64 字符串
        raw = base64.b64decode(image_source)
        img = Image.open(io.BytesIO(raw))

    # 1. 转灰度
    img = img.convert('L')
    img_gray = np.array(img)
    h, w = img_gray.shape

    # 2. 涂黑左上角时间戳（缩小范围，仅覆盖文字区域）
    time_h = int(h * 0.03)
    time_w = int(w * 0.50)
    img_gray[0:time_h, 0:time_w] = 0

    # 3. 涂黑右下角相机戳（缩小范围）
    cam_h = int(h * 0.92)
    cam_w = int(w * 0.68)
    img_gray[cam_h:h, cam_w:w] = 0

    # 4. resize 到 128×128
    img = Image.fromarray(img_gray).resize((IMG_SIZE, IMG_SIZE), Image.BILINEAR)

    # 5. ToTensor
    return transform(img)  # [1, H, W], float [0,1]


def _encode_image(tensor: torch.Tensor) -> str:
    """将预测输出张量编码为 base64 PNG。"""
    img_array = (tensor.numpy() * 255).clip(0, 255).astype(np.uint8)
    img = Image.fromarray(img_array)
    buf = io.BytesIO()
    img.save(buf, format='PNG')
    return base64.b64encode(buf.getvalue()).decode('utf-8')


# ---------------------------------------------------------------------------
# 预测接口
# ---------------------------------------------------------------------------
def predict_cloud_images(input_images: List[str]) -> List[str]:
    """
    输入 10 张云图（base64 或文件路径），输出 10 张预测云图（base64 PNG）。

    Args:
        input_images: 10 个图片源（base64 字符串或文件路径）

    Returns:
        10 个 base64 编码的 PNG 图片字符串
    """
    if len(input_images) != PRE_SEQ_LENGTH:
        raise ValueError(f"需要恰好 {PRE_SEQ_LENGTH} 张输入云图，收到 {len(input_images)}")

    # 确定权重路径
    ckpt_path = os.path.join(CKPT_DIR, CKPT_FILENAME)
    if not os.path.exists(ckpt_path):
        raise FileNotFoundError(f"模型权重不存在: {ckpt_path}")

    # 加载模型
    cloud_model_manager.load_model(ckpt_path)

    # 预处理输入
    tensors = []
    for img_src in input_images:
        t = _decode_image(img_src)  # [1, H, W]
        tensors.append(t)

    # 构建输入张量: [1, T, C, H, W]
    input_tensor = torch.stack(tensors).unsqueeze(0)  # [1, 10, 1, 128, 128]

    # 推理
    pred_raw = cloud_model_manager.predict(input_tensor)  # [1, 10, 1, 128, 128]

    # 后处理：每帧编码为 base64
    results = []
    for t in range(pred_raw.shape[1]):
        frame = pred_raw[0, t, 0]  # [H, W]
        b64 = _encode_image(frame)
        results.append(b64)

    return results


def list_cloud_models() -> list:
    """返回可用的云图预测模型列表。"""
    return ["SimVP_Cloud"]
