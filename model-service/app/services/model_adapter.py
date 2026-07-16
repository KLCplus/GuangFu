"""Real model inference adapter for the PV prediction model service.

Loads 19 models (7 time-series + 8 video spatiotemporal + 4 multimodal fusion)
from checkpoints, prepares inputs from the FastAPI request, runs forward
passes, and returns 6 power predictions (5/10/15/20/25/30 min ahead).

The service accepts 30 one-minute business input steps. These trained weights
currently expect shorter sequences, so the adapter first aggregates the 30
steps into 6 model steps. Time-series models then consume the aggregated
numeric sequence directly. Video / multimodal models also use the aggregated
time structure, but the image content must come from caller-provided cloud
images via file paths / base64 payloads. The service does not depend on any
local cloud-image archive.
"""

import base64
import hashlib
import io
import os
import sys
import importlib
from datetime import datetime, timedelta
from pathlib import Path
from time import perf_counter
from typing import Dict, Optional, Tuple

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image
from types import SimpleNamespace

from app.schemas import PredictData, PredictRequest, Prediction

# ---------------------------------------------------------------------------
# Paths (所有路径限定在 /root/shixun 范围内)
# ---------------------------------------------------------------------------
_BASE = Path(__file__).resolve().parents[2]
TSL_PATH = str(_BASE / "Time-Series-Library")
OpenSTL_PATH = str(_BASE / "OpenSTL")
TSL_CKPT_BASE = str(_BASE / "TSL")  # 训练好的权重目录: TSL/{model}/checkpoints/best_epoch.pth
CKPT_DIR = str(_BASE / "checkpoints")
for _p in (TSL_PATH, OpenSTL_PATH):
    if _p not in sys.path:
        sys.path.insert(0, _p)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
NORM_FACTOR = 30.1   # 归一化因子：输入÷30.1，输出×30.1
SEQ_LEN = 30         # 输入时间步数（30步×1分钟）
PRED_LEN = 6         # 预测步数（6步×5分钟）
LABEL_LEN = 15       # Transformer/TimeMixer decoder label length
TIME_FEAT_DIM = 5    # freq='t' → [Minute, Hour, DayOfWeek, DayOfMonth, DayOfYear]
IMG_SIZE = 64
TIME_OFFSETS = list(range(5, 31, 5))  # [5, 10, 15, 20, 25, 30]
_IMAGE_TENSOR_CACHE: Dict[Tuple[str, str], torch.Tensor] = {}


def _resize_images(img: torch.Tensor) -> torch.Tensor:
    """强制将云图张量 resize 到 IMG_SIZE×IMG_SIZE。

    支持以下输入形状（末尾两维为 H, W）：
        (B, C, H, W)            → 2D 卷积类
        (B, T, C, H, W)         → 视频序列类
        (B, C, T, H, W)         → 3D 卷积类
    任何 H/W 不等于 IMG_SIZE 的输入都会通过双线性插值被强制缩放到 IMG_SIZE×IMG_SIZE。
    """
    if img.shape[-1] == IMG_SIZE and img.shape[-2] == IMG_SIZE:
        return img
    # 统一按 (N, C, H, W) 做插值，再还原
    if img.dim() == 4:  # (B, C, H, W)
        return F.interpolate(img, size=(IMG_SIZE, IMG_SIZE), mode='bilinear', align_corners=False)
    elif img.dim() == 5:  # (B, T, C, H, W) 或 (B, C, T, H, W)
        # 把序列维度展平到 batch 维，逐帧 resize
        B, d1, d2, H, W = img.shape
        flat = img.reshape(B * d1, d2, H, W)
        flat = F.interpolate(flat, size=(IMG_SIZE, IMG_SIZE), mode='bilinear', align_corners=False)
        return flat.reshape(B, d1, d2, IMG_SIZE, IMG_SIZE)
    return img


# ---------------------------------------------------------------------------
# Time-feature computation (matches TSL utils/timefeatures.py for freq='t')
# ---------------------------------------------------------------------------
def _time_features(dates):
    """Return (len(dates), 5) array of calendar features normalised to [-0.5, 0.5]."""
    import pandas as pd
    idx = pd.DatetimeIndex(dates)
    feats = np.stack([
        idx.minute / 59.0 - 0.5,
        idx.hour / 23.0 - 0.5,
        idx.dayofweek / 6.0 - 0.5,
        (idx.day - 1) / 30.0 - 0.5,
        (idx.dayofyear - 1) / 365.0 - 0.5,
    ], axis=-1).astype(np.float32)
    return feats


_TIME_FORMATS = ["%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M",
                 "%Y/%m/%d %H:%M:%S", "%Y/%m/%d %H:%M"]


def _parse_time(s: str) -> datetime:
    for fmt in _TIME_FORMATS:
        try:
            return datetime.strptime(s.strip(), fmt)
        except ValueError:
            continue
    # last resort: let pandas try
    import pandas as pd
    return pd.to_datetime(s).to_pydatetime()


def _aggregate_frames(input_frames, target_steps=SEQ_LEN):
    """将分钟级输入聚合成 target_steps 个等长时间步。

    当前业务侧会传入 30 个 1 分钟时间步，而这批已训练权重仍使用
    6 个输入步长。这里按顺序把输入切成 target_steps 段，并对
    power 做均值聚合，时间取每段最后一个时间戳。
    如果传入步长已经小于等于 target_steps，则仅做左侧补齐/截断。
    """
    if not input_frames:
        return []

    if len(input_frames) <= target_steps:
        frames = list(input_frames)
        while len(frames) < target_steps:
            frames.insert(0, frames[0])
        return frames[-target_steps:]

    groups = _split_input_frames(input_frames, target_steps=target_steps)
    aggregated = []
    for part in groups:
        aggregated.append(SimpleNamespace(
            time=part[-1].time,
            power=float(np.mean([f.power for f in part])),
        ))
    return aggregated


def _split_input_frames(input_frames, target_steps=SEQ_LEN):
    if not input_frames:
        return []

    if len(input_frames) <= target_steps:
        frames = list(input_frames)
        while len(frames) < target_steps:
            frames.insert(0, frames[0])
        return [[frame] for frame in frames[-target_steps:]]

    chunk = len(input_frames) // target_steps
    if chunk <= 0:
        chunk = 1

    frames = list(input_frames)[-chunk * target_steps:]
    return [frames[i * chunk:(i + 1) * chunk] for i in range(target_steps)]


def _series_to_numpy(values):
    return np.asarray(list(values), dtype=np.float32)


def _normalise_series(values):
    arr = _series_to_numpy(values)
    if arr.size == 0:
        return arr
    lo = float(arr.min())
    hi = float(arr.max())
    if hi - lo < 1e-6:
        return np.full_like(arr, 0.5, dtype=np.float32)
    return ((arr - lo) / (hi - lo)).astype(np.float32)


def _build_aggregated_series(input_frames, target_steps=SEQ_LEN):
    frames = _aggregate_frames(input_frames, target_steps=target_steps)
    powers = _series_to_numpy(f.power for f in frames)
    return frames, powers


def _cloud_image_lookup(request: PredictRequest):
    lookup = {}
    for item in request.cloudImages or []:
        lookup[_parse_time(item.time)] = item
    return lookup


def _load_cloud_image(path: Optional[str], mode: str, cloud_image_base64: Optional[str] = None):
    if cloud_image_base64 is not None:
        digest = hashlib.sha1(cloud_image_base64.encode("utf-8")).hexdigest()
        cache_key = (f"base64:{digest}", mode)
    else:
        if path is None:
            raise FileNotFoundError("cloud image path is empty")
        cache_key = (os.path.abspath(path), mode)
    cached = _IMAGE_TENSOR_CACHE.get(cache_key)
    if cached is not None:
        return cached

    if cloud_image_base64 is not None:
        raw = cloud_image_base64.split(",", 1)[-1]
        try:
            image = Image.open(io.BytesIO(base64.b64decode(raw)))
        except Exception as exc:
            raise ValueError("invalid cloudImageBase64 payload") from exc
    else:
        image = Image.open(path)
    image = image.convert(mode)
    image = image.resize((IMG_SIZE, IMG_SIZE), Image.BILINEAR)
    arr = np.asarray(image, dtype=np.float32) / 255.0
    if arr.ndim == 2:
        tensor = torch.from_numpy(arr)
    else:
        tensor = torch.from_numpy(arr).permute(2, 0, 1)
    _IMAGE_TENSOR_CACHE[cache_key] = tensor
    return tensor


def _explicit_cloud_tensor(frame, request_cloud_lookup, mode: str):
    if getattr(frame, "cloudImageBase64", None):
        return _load_cloud_image(None, mode, cloud_image_base64=frame.cloudImageBase64)
    if getattr(frame, "cloudImage", None):
        return _load_cloud_image(frame.cloudImage, mode)

    item = request_cloud_lookup.get(_parse_time(frame.time))
    if item is None:
        return None

    if item.cloudImageBase64:
        return _load_cloud_image(None, mode, cloud_image_base64=item.cloudImageBase64)

    path = item.cloudImage or item.source or item.file
    if path:
        return _load_cloud_image(path, mode)
    return None


def _resolve_group_cloud_tensor(group, request_cloud_lookup, mode: str):
    # Prefer the latest explicit image within the group so 6-step or 8-step
    # image payloads can still drive the current aggregated inference path.
    for frame in reversed(group):
        explicit = _explicit_cloud_tensor(frame, request_cloud_lookup, mode)
        if explicit is not None:
            return explicit
    raise ValueError(
        "Cloud images are required for video/fusion models. "
        "Please provide explicit cloudImage or cloudImageBase64 data."
    )


def _build_real_image_sequence(request: PredictRequest, target_steps, mode):
    groups = list(_split_input_frames(request.input, target_steps=target_steps))
    request_cloud_lookup = _cloud_image_lookup(request)
    tensors = []
    for group in groups:
        tensors.append(_resolve_group_cloud_tensor(group, request_cloud_lookup, mode))
    return tensors


def _build_video_tensor(request: PredictRequest):
    step_images = _build_real_image_sequence(request, target_steps=SEQ_LEN, mode="L")
    x = torch.stack(step_images, dim=0).unsqueeze(0).unsqueeze(2)
    return _resize_images(x)


def _build_multimodal_images(request: PredictRequest, name):
    step_images = _build_real_image_sequence(request, target_steps=SEQ_LEN, mode="L")
    stacked = torch.stack(step_images, dim=0)
    if name == 'CNN_MLP':
        return _resize_images(stacked.unsqueeze(0))
    if name in ('CNN_LSTM', 'ConvLSTM_LSTM'):
        return _resize_images(stacked.unsqueeze(0).unsqueeze(2))
    return _resize_images(stacked.unsqueeze(0).unsqueeze(1))


def _decode_base64_image(image_data: str):
    """解码 base64 图片数据，返回 PIL Image。"""
    if not image_data:
        return None
    try:
        raw = image_data.split(",", 1)[-1]
        return Image.open(io.BytesIO(base64.b64decode(raw)))
    except Exception:
        return None


def _build_sunset_inputs(request: PredictRequest):
    """构建 SUNSET 模型输入。

    训练时的输入格式：
      - images: [B, 90, 64, 64]  (30张RGB云图，通道堆叠: 3*30=90)
      - power: [B, 30]           (30步功率序列，已÷30.1归一化)

    前端传入30个时间步，每个步有1张云图和1个功率值。
    功率需要除以30.1归一化，与训练数据一致。
    """
    NORM_FACTOR = 30.1

    # 功率：归一化（÷30.1），与训练数据一致
    powers = [frame.power / NORM_FACTOR for frame in request.input]
    power_tensor = torch.tensor(powers, dtype=torch.float32).view(1, 30)

    # 云图：从30个时间步的图片构建RGB序列
    # 每张图转为RGB 3通道，resize到64x64，归一化到[0,1]，然后通道堆叠 → [1, 90, 64, 64]
    rgb_channels = []
    for img_frame in request.inputImages[:30]:
        img = _decode_base64_image(img_frame.image)
        if img is None:
            # 无图片时用灰色填充
            img = Image.new('RGB', (IMG_SIZE, IMG_SIZE), color=(128, 128, 128))
        img = img.convert('RGB').resize((IMG_SIZE, IMG_SIZE), Image.BILINEAR)
        arr = np.array(img, dtype=np.float32) / 255.0  # [H, W, 3]
        # 分离RGB通道，每通道 [H, W]
        for c in range(3):
            rgb_channels.append(torch.from_numpy(arr[:, :, c]))

    # 堆叠: [90, H, W] → [1, 90, H, W]
    images = torch.stack(rgb_channels).unsqueeze(0)  # [1, 90, 64, 64]

    return images, power_tensor


def _build_timestamps(input_frames):
    """Parse aggregated input timestamps and build decoder timestamps."""
    times = [_parse_time(f.time) for f in input_frames]
    # Infer sampling interval from the last two points (default 5 min)
    if len(times) >= 2:
        delta = times[-1] - times[-2]
        if delta.total_seconds() <= 0:
            delta = timedelta(minutes=5)
    else:
        delta = timedelta(minutes=5)
    # Decoder: last LABEL_LEN input stamps + PRED_LEN future stamps
    dec_times = times[-LABEL_LEN:] + [times[-1] + delta * (i + 1) for i in range(PRED_LEN)]
    return times, dec_times


# ===========================================================================
# 1. TIME-SERIES MODELS (7) — 使用 TSL/{model}/checkpoints/best_epoch.pth
# ===========================================================================
# 模型名 → 权重子目录名映射
_TSL_CKPT_DIRS = {
    'DLinear':      'dlinear',
    'PatchTST':     'patchtst',
    'iTransformer': 'iTransformer',
    'TimeXer':      'TimeXer',
    'TimeMixer':    'TimeMixer',
    'TSMixer':      'TSMixer',
    'Transformer':   'transformer',
}


def _make_tsl_configs(**overrides):
    """创建 TSL 模型配置，与 train.py 中的配置完全一致。"""
    cfg = SimpleNamespace(
        task_name='long_term_forecast',
        seq_len=SEQ_LEN,       # 30
        pred_len=PRED_LEN,     # 6
        label_len=LABEL_LEN,   # 15
        d_model=128, n_heads=4, e_layers=2, d_layers=1, d_ff=256,
        enc_in=1, dec_in=1, c_out=1,
        dropout=0.1, factor=1, activation='gelu',
        embed='timeF', freq='t',
        output_attention=False, use_norm=1,
        # DLinear
        moving_avg=5, individual=False,
        # TimeMixer
        down_sampling_layers=3, down_sampling_window=2, down_sampling_method='avg',
        channel_independence=True, decomp_method='moving_avg', top_k=5,
        # TimeXer
        features='S', patch_len=5,
    )
    for k, v in overrides.items():
        setattr(cfg, k, v)
    return cfg


# 每个模型的专属配置和前向类型
_TSL_SPECS = {
    'DLinear': {
        'configs': _make_tsl_configs(),
        'kwargs': {},
        'forward_type': 'simple',
    },
    'PatchTST': {
        'configs': _make_tsl_configs(),
        'kwargs': {'patch_len': 4, 'stride': 2},
        'forward_type': 'patchtst',
    },
    'iTransformer': {
        'configs': _make_tsl_configs(),
        'kwargs': {},
        'forward_type': 'simple',
    },
    'TimeXer': {
        'configs': _make_tsl_configs(features='M', enc_in=2, patch_len=5),
        'kwargs': {},
        'forward_type': 'timexer',
    },
    'TimeMixer': {
        'configs': _make_tsl_configs(
            down_sampling_layers=3, down_sampling_window=2,
            down_sampling_method='avg', channel_independence=True,
            decomp_method='moving_avg', moving_avg=5, top_k=5,
            use_norm=1, c_out=1,
        ),
        'kwargs': {},
        'forward_type': 'simple',
    },
    'TSMixer': {
        'configs': _make_tsl_configs(),
        'kwargs': {},
        'forward_type': 'simple',
    },
    'Transformer': {
        'configs': _make_tsl_configs(d_layers=1, dec_in=1, c_out=1),
        'kwargs': {},
        'forward_type': 'transformer',
    },
}


def _build_tsl_model(name):
    """加载 TSL 模型权重（CPU 推理，避免影响 GPU 训练）。"""
    spec = _TSL_SPECS[name]
    mod = importlib.import_module(f'models.{name}')
    model = mod.Model(spec['configs'], **spec['kwargs']) if spec['kwargs'] else mod.Model(spec['configs'])

    ckpt_subdir = _TSL_CKPT_DIRS[name]
    ckpt_path = os.path.join(TSL_CKPT_BASE, ckpt_subdir, 'checkpoints', 'best_epoch.pth')
    obj = torch.load(ckpt_path, map_location='cpu', weights_only=False)
    # 训练时保存格式: {'model_state_dict': ..., 'configs': ..., ...}
    if isinstance(obj, dict) and 'model_state_dict' in obj:
        model.load_state_dict(obj['model_state_dict'])
    else:
        model.load_state_dict(obj)
    model.eval()
    return model


def _tsl_forward(model, name, input_frames):
    """TSL 模型前向推理。

    输入: 30步功率数据（原始值）
    处理: ÷30.1 归一化 → 模型推理 → ×30.1 反归一化
    输出: 6步预测功率值（原始单位）
    """
    forward_type = _TSL_SPECS[name]['forward_type']

    # 提取30步功率值并归一化
    powers = [f.power for f in input_frames]
    if len(powers) < SEQ_LEN:
        powers = [powers[0]] * (SEQ_LEN - len(powers)) + powers
    powers = powers[-SEQ_LEN:]
    powers_norm = [p / NORM_FACTOR for p in powers]

    bx = torch.tensor(powers_norm, dtype=torch.float32).view(1, SEQ_LEN, 1)  # [1, 30, 1]

    with torch.no_grad():
        if forward_type == 'simple':
            # DLinear, iTransformer, TimeMixer, TSMixer
            out = model(bx, None, None, None).squeeze(-1)     # [1, 6]

        elif forward_type == 'patchtst':
            # PatchTST: 需要 x_dec
            x_dec = torch.zeros(1, PRED_LEN, 1)
            out = model(bx, None, x_dec, None).squeeze(-1)    # [1, 6]

        elif forward_type == 'timexer':
            # TimeXer: 复制单变量为2列，取输出最后一列
            bx_2var = bx.repeat(1, 1, 2)                       # [1, 30, 2]
            out = model(bx_2var, None, None, None)             # [1, 6, 2]
            out = out[:, :, -1]                                 # [1, 6]

        elif forward_type == 'transformer':
            # Transformer: encoder-decoder
            start_token = bx[:, -LABEL_LEN:, :]                 # [1, 15, 1]
            zeros = torch.zeros(1, PRED_LEN, 1)                 # [1, 6, 1]
            x_dec = torch.cat([start_token, zeros], dim=1)      # [1, 21, 1]
            out = model(bx, None, x_dec, None)                  # [1, 21, 1]
            out = out[:, -PRED_LEN:, :].squeeze(-1)             # [1, 6]

        else:
            raise ValueError(f"Unknown forward_type: {forward_type}")

    # 反归一化: ×30.1
    out = out * NORM_FACTOR
    return out.squeeze(0).tolist()


# ===========================================================================
# 2. VIDEO MODELS (8)
# ===========================================================================
class _VideoWrapper(nn.Module):
    """Unified wrapper: backbone → mean over spatial dims → reg_head."""

    def __init__(self, backbone, reg_head, forward_type='simvp'):
        super().__init__()
        self.backbone = backbone
        self.reg_head = reg_head
        self.forward_type = forward_type

    def forward(self, x):
        """x: (B, T, C, H, W) in NCHW."""
        if self.forward_type == 'simvp':
            frames = self.backbone(x)                                  # (B, T, C, H, W)
            pooled = frames.mean(dim=[2, 3, 4])                        # (B, T)
        elif self.forward_type == 'convlstm':
            frames = self._convlstm_forward(x)
            pooled = frames.mean(dim=[2, 3, 4])
        elif self.forward_type == 'rnn':
            frames = self._rnn_forward(x)
            pooled = frames.mean(dim=[2, 3, 4])
        elif self.forward_type == 'swinlstm':
            frames = self._swinlstm_forward(x)
            pooled = frames.mean(dim=[2, 3, 4])
        else:
            raise ValueError(f"unknown forward_type {self.forward_type}")
        return self.reg_head(pooled)

    # --- ConvLSTM ---
    def _convlstm_forward(self, x):
        B, T, C, H, W = x.shape
        cells = self.backbone
        n = len(cells)
        h = [torch.zeros(B, 64, H, W) for _ in range(n)]
        c = [torch.zeros(B, 64, H, W) for _ in range(n)]
        frames = []
        for t in range(T):
            xt = x[:, t]
            for i in range(n):
                h[i], c[i] = cells[i](xt, h[i], c[i])
                xt = h[i]
            frames.append(xt)
        return torch.stack(frames, dim=1)

    # --- PredRNN / PredRNN++ / E3D_LSTM ---
    def _rnn_forward(self, x):
        B, T, C, H, W = x.shape
        ps = self.patch_size
        x_nhwc = x.permute(0, 1, 3, 4, 2)                          # (B,T,H,W,C)
        x_patched = x_nhwc.reshape(B, T, H // ps, W // ps, ps * ps * C)
        cat_input = torch.cat([x_patched, x_patched], dim=1)        # (B, 2T, ...)
        # mask_true: zero mask → use predicted frames (autoregressive)
        mask_true = torch.zeros_like(cat_input)
        next_frames, _ = self.backbone(cat_input, mask_true=mask_true, return_loss=False)
        return next_frames[:, :T]

    # --- swinLSTM ---
    def _swinlstm_forward(self, x):
        x_nhwc = x.permute(0, 1, 3, 4, 2).contiguous()             # (B,T,H,W,C)
        next_frames, _ = self.backbone(x_nhwc, return_loss=False)
        return next_frames


# --- SimVP / TAU ---
def _build_simvp(model_type):
    from openstl.models.simvp_model import SimVP_Model
    in_shape = (SEQ_LEN, 1, IMG_SIZE, IMG_SIZE)
    backbone = SimVP_Model(in_shape, hid_S=64, hid_T=256, N_S=4, N_T=4, model_type=model_type)
    reg_head = nn.Sequential(nn.Linear(SEQ_LEN, 64), nn.ReLU(), nn.Linear(64, SEQ_LEN))
    return _VideoWrapper(backbone, reg_head, 'simvp')


# --- ConvLSTM ---
def _build_convlstm():
    from openstl.models.convlstm_model import ConvLSTM_Model
    configs = SimpleNamespace(in_shape=(SEQ_LEN, 1, IMG_SIZE, IMG_SIZE), patch_size=1,
                              filter_size=5, stride=1, layer_norm=False,
                              pre_seq_length=SEQ_LEN, aft_seq_length=SEQ_LEN,
                              reverse_scheduled_sampling=0)
    backbone = ConvLSTM_Model(num_layers=2, num_hidden=[64, 64], configs=configs)
    wrapper = _VideoWrapper(backbone.cell_list, None, 'convlstm')
    wrapper.reg_head = nn.Sequential(nn.Linear(SEQ_LEN, 64), nn.ReLU(), nn.Linear(64, SEQ_LEN))
    return wrapper


# --- PredRNN / PredRNN++ / E3D_LSTM ---
def _build_rnn_model(model_name):
    module_map = {
        'PredRNN':    ('openstl.models.predrnn_model', 'PredRNN_Model'),
        'PredRNN++':  ('openstl.models.predrnnpp_model', 'PredRNNpp_Model'),
        'E3D_LSTM':   ('openstl.models.e3dlstm_model', 'E3DLSTM_Model'),
    }
    mod_path, cls_name = module_map[model_name]
    mod = importlib.import_module(mod_path)
    configs = SimpleNamespace(in_shape=(SEQ_LEN, 1, IMG_SIZE, IMG_SIZE), patch_size=4,
                              filter_size=5, stride=1, layer_norm=False,
                              pre_seq_length=SEQ_LEN, aft_seq_length=SEQ_LEN,
                              reverse_scheduled_sampling=0)
    backbone = getattr(mod, cls_name)(num_layers=4, num_hidden=[64, 64, 64, 64], configs=configs)
    reg_head = nn.Sequential(nn.Linear(SEQ_LEN, 64), nn.ReLU(), nn.Linear(64, SEQ_LEN))
    wrapper = _VideoWrapper(backbone, reg_head, 'rnn')
    wrapper.patch_size = 4
    return wrapper


# --- swinLSTM ---
def _build_swinlstm():
    from openstl.models.swinlstm_model import SwinLSTM_D_Model
    dd, du = [2, 6], [6, 2]
    # in_shape T=7 → first loop processes all 6 input frames, second loop empty
    configs = SimpleNamespace(in_shape=(7, 1, IMG_SIZE, IMG_SIZE), patch_size=2,
                              embed_dim=128, window_size=4, depths=dd, num_heads=[4, 8])
    backbone = SwinLSTM_D_Model(depths_downsample=dd, depths_upsample=du,
                                num_heads=[4, 8], configs=configs)
    reg_head = nn.Sequential(nn.Linear(SEQ_LEN, 64), nn.ReLU(), nn.Linear(64, SEQ_LEN))
    return _VideoWrapper(backbone, reg_head, 'swinlstm')


# --- SUNSET (standalone CNN, matches train.py architecture) ---
class _SunsetCNN(nn.Module):
    """SUNSET CNN 模型，与 train.py 中的定义完全一致。

    输入：
      - images: [B, 90, 64, 64]  (30张RGB云图，通道堆叠: 3*30=90)
      - power: [B, 30]           (30步功率序列)
    输出：
      - pred: [B, 6]             (6步功率预测)
    """

    def __init__(self, num_filters=24, dense_size=1024, drop_rate=0.4,
                 seq_len=30, pred_len=6):
        super().__init__()
        # 卷积块1
        self.conv1 = nn.Conv2d(3 * seq_len, num_filters, kernel_size=3, padding=1)
        self.bn1 = nn.BatchNorm2d(num_filters)
        self.pool1 = nn.MaxPool2d(kernel_size=2, stride=2)

        # 卷积块2
        self.conv2 = nn.Conv2d(num_filters, num_filters * 2, kernel_size=3, padding=1)
        self.bn2 = nn.BatchNorm2d(num_filters * 2)
        self.pool2 = nn.MaxPool2d(kernel_size=2, stride=2)

        # 展平后维度
        flat_dim = num_filters * 2 * 16 * 16

        # 全连接层
        self.fc1 = nn.Linear(flat_dim + seq_len, dense_size)
        self.drop1 = nn.Dropout(drop_rate)
        self.fc2 = nn.Linear(dense_size, dense_size)
        self.drop2 = nn.Dropout(drop_rate)
        self.fc_out = nn.Linear(dense_size, pred_len)

    def forward(self, images, power):
        # images: [B, 90, 64, 64], power: [B, 30]
        x = F.relu(self.conv1(images))
        x = self.bn1(x)
        x = self.pool1(x)  # [B, 24, 32, 32]

        x = F.relu(self.conv2(x))
        x = self.bn2(x)
        x = self.pool2(x)  # [B, 48, 16, 16]

        x = x.flatten(1)  # [B, 48*16*16]
        x = torch.cat([x, power], dim=1)  # [B, flat_dim+30]

        x = F.relu(self.fc1(x))
        x = self.drop1(x)
        x = F.relu(self.fc2(x))
        x = self.drop2(x)
        return self.fc_out(x)  # [B, 6]


def _build_sunset():
    obj = torch.load(os.path.join(CKPT_DIR, 'SUNSET.pth'), map_location='cpu', weights_only=False)
    # checkpoint 存的 key 是 'configs'（复数），兼容 'config'
    cfg = obj.get('configs', obj.get('config', {}))
    model = _SunsetCNN(
        num_filters=cfg.get('num_filters', 24) if isinstance(cfg, dict) else 24,
        dense_size=cfg.get('dense_size', 1024) if isinstance(cfg, dict) else 1024,
        drop_rate=cfg.get('drop_rate', 0.4) if isinstance(cfg, dict) else 0.4,
        seq_len=30,
        pred_len=6,
    )
    model.load_state_dict(obj['model_state_dict'])
    model.eval()
    return model


_VIDEO_BUILDERS = {
    'SimVP_gSTA': lambda: _build_simvp('gSTA'),
    'TAU':        lambda: _build_simvp('tau'),
    'ConvLSTM':   _build_convlstm,
    'PredRNN':    lambda: _build_rnn_model('PredRNN'),
    'PredRNN++':  lambda: _build_rnn_model('PredRNN++'),
    'E3D_LSTM':   lambda: _build_rnn_model('E3D_LSTM'),
    'swinLSTM':   _build_swinlstm,
    'SUNSET':     _build_sunset,
}


def _video_forward(model, name, request: PredictRequest):
    if name == 'SUNSET':
        images, power = _build_sunset_inputs(request)
        with torch.no_grad():
            out = model(images, power)       # (1, 6) 归一化值
        # 反归一化：×30.1 还原为 kW
        out = out * 30.1
        return out.squeeze(0).tolist()
    else:
        # Other video models consume 6 aggregated grayscale images from explicit or matched cloud images.
        x = _build_video_tensor(request)
        with torch.no_grad():
            out = model(x)                    # (1, 6)
        return out.squeeze(0).tolist()


# ===========================================================================
# 3. MULTIMODAL FUSION MODELS (4)
# ===========================================================================


class _Block2D(nn.Module):
    def __init__(self, cin, cout, stride=1):
        super().__init__()
        self.conv1 = nn.Conv2d(cin, cout, 3, stride=stride, padding=1, bias=True)
        self.bn1 = nn.BatchNorm2d(cout)
        self.conv2 = nn.Conv2d(cout, cout, 3, stride=1, padding=1, bias=True)
        self.bn2 = nn.BatchNorm2d(cout)
        self.down = nn.Conv2d(cin, cout, 1, stride=stride, bias=False) if (stride != 1 or cin != cout) else None
    def forward(self, x):
        idn = x if self.down is None else self.down(x)
        out = F.relu(self.bn1(self.conv1(x)))
        out = self.bn2(self.conv2(out))
        return F.relu(out + idn)


class _Block3D(nn.Module):
    def __init__(self, cin, cout, stride=1):
        super().__init__()
        s = (stride, 1, 1) if isinstance(stride, int) else stride
        self.conv1 = nn.Conv3d(cin, cout, 3, stride=s, padding=1, bias=True)
        self.bn1 = nn.BatchNorm3d(cout)
        self.conv2 = nn.Conv3d(cout, cout, 3, stride=1, padding=1, bias=True)
        self.bn2 = nn.BatchNorm3d(cout)
        self.down = nn.Conv3d(cin, cout, 1, stride=s, bias=False) if (s != (1,1,1) or cin != cout) else None
    def forward(self, x):
        idn = x if self.down is None else self.down(x)
        out = F.relu(self.bn1(self.conv1(x)))
        out = self.bn2(self.conv2(out))
        return F.relu(out + idn)


class _ConvLSTMCell(nn.Module):
    def __init__(self, input_dim, hidden_dim, ks=3):
        super().__init__()
        self.hidden_dim = hidden_dim
        self.conv = nn.Conv2d(input_dim + hidden_dim, 4 * hidden_dim, ks, padding=ks // 2)
    def forward(self, x, h, c):
        B, _, H, W = x.shape
        if h is None: h = torch.zeros(B, self.hidden_dim, H, W, device=x.device)
        if c is None: c = torch.zeros(B, self.hidden_dim, H, W, device=x.device)
        i, f, g, o = torch.split(self.conv(torch.cat([x, h], 1)), self.hidden_dim, 1)
        i, f, o = torch.sigmoid(i), torch.sigmoid(f), torch.sigmoid(o)
        g = torch.tanh(g)
        c = f * c + i * g
        return o * torch.tanh(c), c


# --- CNN_MLP: img(6ch) + aux(6 scalars) → fusion ---
class _CNNMLP(nn.Module):
    def __init__(self):
        super().__init__()
        self.img_backbone = nn.Sequential(
            nn.Conv2d(6, 16, 3, stride=2, padding=1), nn.ReLU(),
            nn.Conv2d(16, 16, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(16, 16),
            nn.Conv2d(16, 32, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(32, 32),
            nn.Conv2d(32, 32, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(32, 32),
            nn.Conv2d(32, 64, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(64, 64),
            nn.AdaptiveAvgPool2d(2), nn.Flatten(),
        )
        self.img_mlp = nn.Sequential(nn.Linear(256, 512), nn.ReLU(), nn.Linear(512, 64))
        self.aux_mlp = nn.Sequential(nn.Linear(6, 16), nn.ReLU(), nn.Linear(16, 16))
        self.fusion_head = nn.Sequential(nn.Linear(80, 64), nn.ReLU(), nn.Linear(64, 32), nn.ReLU(), nn.Linear(32, 6))
    def forward(self, img, aux):
        return self.fusion_head(torch.cat([self.img_mlp(self.img_backbone(img)), self.aux_mlp(aux)], -1))


# --- CNN_LSTM: img(T frames,1ch) + aux(T,1) → LSTM → head ---
class _CNNLSTM(nn.Module):
    def __init__(self):
        super().__init__()
        self.img_encoder = nn.Module()
        self.img_encoder.net = nn.Sequential(
            nn.Conv2d(1, 8, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(8, 8),
            nn.Conv2d(8, 16, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(16, 16),
            nn.Conv2d(16, 32, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(32, 32),
            nn.Conv2d(32, 64, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(64, 64),
            nn.AdaptiveAvgPool2d(2), nn.Flatten(),
            nn.Linear(256, 256), nn.ReLU(),
            nn.Linear(256, 128),
        )
        self.aux_encoder = nn.Module()
        self.aux_encoder.net = nn.Sequential(
            nn.Linear(1, 16), nn.ReLU(), nn.Linear(16, 16), nn.ReLU(), nn.Linear(16, 16),
        )
        self.lstm = nn.LSTM(input_size=144, hidden_size=128, batch_first=True)
        self.head = nn.Sequential(nn.Linear(128, 64), nn.ReLU(), nn.Linear(64, 32), nn.ReLU(), nn.Linear(32, 6))
    def forward(self, img, aux):
        B, T_, C, H, W = img.shape
        feats = [self.img_encoder.net(img[:, t]) for t in range(T_)]
        img_feat = torch.stack(feats, 1)
        aux_feat = self.aux_encoder.net(aux)
        out, _ = self.lstm(torch.cat([img_feat, aux_feat], -1))
        return self.head(out[:, -1])


# --- 3DCNN_LSTM: img(1ch,T,H,W) + aux(T,1) → LSTM → head ---
class _DCNNLSTM(nn.Module):
    def __init__(self):
        super().__init__()
        self.img_encoder = nn.Module()
        self.img_encoder.net = nn.Sequential(
            nn.Conv3d(1, 8, 3, stride=(1,2,2), padding=1), nn.ReLU(),
            _Block3D(8, 8),
            nn.Conv3d(8, 16, 3, stride=(1,2,2), padding=1), nn.ReLU(),
            _Block3D(16, 16),
            nn.Conv3d(16, 32, 3, stride=(1,2,2), padding=1), nn.ReLU(),
            _Block3D(32, 32),
            nn.Conv3d(32, 64, 3, stride=(1,2,2), padding=1), nn.ReLU(),
            _Block3D(64, 64),
            nn.Conv3d(64, 128, 3, stride=(1,2,2), padding=1), nn.ReLU(),
            nn.AdaptiveAvgPool3d((1, 2, 2)), nn.Flatten(),
            nn.Linear(512, 256), nn.ReLU(),
            nn.Linear(256, 128),
        )
        self.aux_encoder = nn.Module()
        self.aux_encoder.step_mlp = nn.Sequential(
            nn.Linear(1, 16), nn.ReLU(), nn.Linear(16, 16), nn.ReLU(), nn.Linear(16, 16),
        )
        self.aux_encoder.lstm = nn.LSTM(input_size=16, hidden_size=16, batch_first=True)
        self.head = nn.Sequential(nn.Linear(144, 64), nn.ReLU(), nn.Linear(64, 32), nn.ReLU(), nn.Linear(32, 6))
    def forward(self, img, aux):
        img_feat = self.img_encoder.net(img)
        aux_feat, _ = self.aux_encoder.lstm(self.aux_encoder.step_mlp(aux))
        return self.head(torch.cat([img_feat, aux_feat[:, -1]], -1))


# --- ConvLSTM_LSTM: img(T,1,H,W) spatial+convlstm + aux(T,1) LSTM ---
class _ConvLSTMLSTM(nn.Module):
    def __init__(self):
        super().__init__()
        self.img_branch = nn.Module()
        self.img_branch.spatial = nn.Sequential(
            nn.Conv2d(1, 8, 3, stride=2, padding=1), nn.ReLU(),
            nn.Conv2d(8, 16, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(16, 16),
            _Block2D(16, 16),
            nn.Conv2d(16, 32, 3, stride=2, padding=1), nn.ReLU(),
            _Block2D(32, 32),
            _Block2D(32, 32),
        )
        self.img_branch.convlstm_cells = nn.ModuleList([
            _ConvLSTMCell(32, 32), _ConvLSTMCell(32, 64),
            _ConvLSTMCell(64, 128), _ConvLSTMCell(128, 64),
        ])
        self.img_branch.vec = nn.Sequential(
            nn.AdaptiveAvgPool2d(2), nn.Flatten(),
            nn.Linear(256, 256), nn.ReLU(), nn.Linear(256, 128),
        )
        self.aux_branch = nn.Module()
        self.aux_branch.step_mlp = nn.Sequential(
            nn.Linear(1, 16), nn.ReLU(), nn.Linear(16, 16), nn.ReLU(), nn.Linear(16, 16),
        )
        self.aux_branch.lstm = nn.LSTM(input_size=16, hidden_size=16, batch_first=True)
        self.head = nn.Sequential(nn.Linear(144, 512), nn.ReLU(), nn.Linear(512, 64), nn.ReLU(), nn.Linear(64, 6))
    def forward(self, img, aux):
        B, T_, C, H, W = img.shape
        hs, cs = [None]*4, [None]*4
        for t in range(T_):
            x = self.img_branch.spatial(img[:, t])
            for i, cell in enumerate(self.img_branch.convlstm_cells):
                hs[i], cs[i] = cell(x, hs[i], cs[i])
                x = hs[i]
        img_feat = self.img_branch.vec(x)
        aux_feat, _ = self.aux_branch.lstm(self.aux_branch.step_mlp(aux))
        return self.head(torch.cat([img_feat, aux_feat[:, -1]], -1))


_MULTIMODAL_BUILDERS = {
    'CNN_MLP':       _CNNMLP,
    'CNN_LSTM':      _CNNLSTM,
    '3DCNN_LSTM':    _DCNNLSTM,
    'ConvLSTM_LSTM': _ConvLSTMLSTM,
}


def _build_multimodal(name):
    cls = _MULTIMODAL_BUILDERS[name]
    model = cls()
    sd = torch.load(os.path.join(CKPT_DIR, f'{name}.pth'), map_location='cpu', weights_only=False)
    model.load_state_dict(sd)
    model.eval()
    return model


def _build_aux_tensor(input_frames):
    """Build auxiliary input from request: (1, T, 1) with power values."""
    frames, powers = _build_aggregated_series(input_frames, target_steps=SEQ_LEN)
    return torch.tensor(powers, dtype=torch.float32).view(1, len(frames), 1)


def _multimodal_forward(model, name, request: PredictRequest):
    aux = _build_aux_tensor(request.input)
    img = _build_multimodal_images(request, name)
    with torch.no_grad():
        if name == 'CNN_MLP':
            # img: (1, 6, H, W) from aggregated real cloud images; aux: (1, 6)
            out = model(img, aux.view(1, 6))
        elif name in ('CNN_LSTM', 'ConvLSTM_LSTM'):
            # img: (1, T, 1, H, W); aux: (1, T, 1)
            out = model(img, aux)
        else:  # 3DCNN_LSTM
            # img: (1, 1, T, H, W); aux: (1, T, 1)
            out = model(img, aux)
    return out.squeeze(0).tolist()


# ===========================================================================
# 4. REGISTRY & PREDICTOR
# ===========================================================================
# model_name → (category, builder)
MODEL_REGISTRY = {}
for _n in _TSL_SPECS:
    MODEL_REGISTRY[_n] = ('ts', lambda n=_n: _build_tsl_model(n))
for _n, _b in _VIDEO_BUILDERS.items():
    MODEL_REGISTRY[_n] = ('video', _b)
for _n in _MULTIMODAL_BUILDERS:
    MODEL_REGISTRY[_n] = ('multimodal', lambda n=_n: _build_multimodal(n))


class RealPredictor:
    """Loads models lazily and runs real inference."""

    def __init__(self):
        self._cache: Dict[str, nn.Module] = {}

    def _get_model(self, name: str):
        if name not in self._cache:
            if name not in MODEL_REGISTRY:
                raise ValueError(f"Unknown model: {name}")
            category, builder = MODEL_REGISTRY[name]
            model = builder()
            self._cache[name] = model
        return self._cache[name]

    def predict(self, request: PredictRequest) -> PredictData:
        started_at = perf_counter()
        name = request.modelName
        category, _ = MODEL_REGISTRY.get(name, (None, None))
        if category is None:
            raise ValueError(f"Unknown model: {name}")

        model = self._get_model(name)
        if category == 'ts':
            raw = _tsl_forward(model, name, request.input)
        elif category == 'multimodal':
            raw = _multimodal_forward(model, name, request)
        else:
            raw = _video_forward(model, name, request)

        predictions = [
            Prediction(timeOffset=offset, predictPower=round(max(0.0, float(v)), 2))
            for offset, v in zip(TIME_OFFSETS, raw)
        ]
        elapsed_ms = max(1, int((perf_counter() - started_at) * 1000))
        return PredictData(modelName=name, predictions=predictions, costTime=elapsed_ms)

    def list_models(self):
        return list(MODEL_REGISTRY.keys())
