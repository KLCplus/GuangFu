"""TimeXer 模型训练+测试+SHAP 脚本

从 Time-Series-Library 改造迁移，使用 TSL 的 TimeXer 模型架构。

TimeXer 设计为需要 endogenous(目标) + exogenous(协变量) 的交叉注意力。
单变量场景下，将功率序列复制为2列（协变量=目标），使用 features='M' 模式。

输入：
  - 30步功率序列 [B, 30, 1]（单变量，已÷30.1归一化）
  - 模型内部复制为 [B, 30, 2]（1列协变量 + 1列目标）
输出：
  - 6步功率预测 [B, 6, 2] → 取最后一列 → [B, 6]

全量训练和评测。
数据以 30.1 为归一化参数归一化。
"""

import os
import sys
import time
import json
import random
import subprocess
from pathlib import Path
from argparse import Namespace

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
from tqdm import tqdm

# 添加 Time-Series-Library 到路径
TSL_PATH = "/root/Time-Series-Library"
sys.path.insert(0, TSL_PATH)

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------
SEED = 42
SEQ_LEN = 30
PRED_LEN = 6
NORM_FACTOR = 30.1
EPOCHS = 50
LR = 1e-3
BATCH_SIZE = 512

# TimeXer 模型参数
D_MODEL = 128
N_HEADS = 4
E_LAYERS = 2
D_FF = 256
DROPOUT = 0.1
ENC_IN = 2  # 复制单变量为2列（协变量+目标）
PATCH_LEN = 5  # 30/5=6 个patch

# 路径
DATA_BASE = Path("/root/data_using/Stanford/model")
TRAIN_DIR = DATA_BASE / "train"
VAL_DIR = DATA_BASE / "val"
TEST_DIR = DATA_BASE / "test"

CKPT_DIR = Path("/root/code_compare/TSL/TimeXer/checkpoints")
RESULT_DIR = Path("/root/code_compare/TSL/TimeXer/result")
SHAP_DIR = RESULT_DIR / "SHAP"
RESULT_MD = RESULT_DIR / "result.md"

CKPT_DIR.mkdir(parents=True, exist_ok=True)
RESULT_DIR.mkdir(parents=True, exist_ok=True)
SHAP_DIR.mkdir(parents=True, exist_ok=True)


# ---------------------------------------------------------------------------
# 固定随机种子
# ---------------------------------------------------------------------------
def set_seed(seed=42):
    torch.manual_seed(seed)
    np.random.seed(seed)
    random.seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


# ---------------------------------------------------------------------------
# 数据集
# ---------------------------------------------------------------------------
class PowerDataset(Dataset):
    """读取时间窗口：30步功率输入 → 6步功率输出。

    全量加载该 split 下所有窗口。
    仅读取 pv.csv，不读取云图。
    """

    def __init__(self, data_dir: Path, split: str):
        all_dirs = sorted([d.name for d in data_dir.iterdir() if d.is_dir()])
        self.window_names = all_dirs
        self.data_dir = data_dir
        print(f"  {split} 全量窗口数: {len(self.window_names)}")

    def __len__(self):
        return len(self.window_names)

    def __getitem__(self, idx):
        wd = self.data_dir / self.window_names[idx]

        # 读取功率
        pv_path = wd / "pv.csv"
        df = pd.read_csv(pv_path)
        power = df["PV_output_kw"].values.astype(np.float32)
        power_in = power[:SEQ_LEN]   # 30 步输入
        power_out = power[SEQ_LEN:]  # 6 步输出

        # [30] → [30, 1] for TimeXer input format
        power_in = power_in.reshape(-1, 1)

        return (
            torch.from_numpy(power_in).float(),   # [30, 1]
            torch.from_numpy(power_out).float(),   # [6]
        )


def get_loader(data_dir, split, batch_size, shuffle):
    ds = PowerDataset(data_dir, split)
    loader = DataLoader(
        ds, batch_size=batch_size, shuffle=shuffle,
        num_workers=4, pin_memory=True, drop_last=False,
        persistent_workers=True, prefetch_factor=2,
    )
    return ds, loader


# ---------------------------------------------------------------------------
# TimeXer 模型构建
# ---------------------------------------------------------------------------
def build_model():
    """构建 TSL TimeXer 模型。"""
    from models.TimeXer import Model as TimeXerModel

    configs = Namespace(
        task_name='long_term_forecast',
        features='M',  # 多变量模式（协变量+目标）
        seq_len=SEQ_LEN,
        pred_len=PRED_LEN,
        d_model=D_MODEL,
        n_heads=N_HEADS,
        e_layers=E_LAYERS,
        d_ff=D_FF,
        factor=1,
        dropout=DROPOUT,
        activation='gelu',
        enc_in=ENC_IN,
        patch_len=PATCH_LEN,
        embed='timeF',
        freq='t',
        use_norm=1,
    )
    model = TimeXerModel(configs)
    return model


# ---------------------------------------------------------------------------
# 指标计算
# ---------------------------------------------------------------------------
def calc_metrics(pred, true):
    """计算6步逐步和平均指标。pred/true: [N, 6] numpy。"""
    results = {}
    steps = pred.shape[1]

    for s in range(steps):
        p, t = pred[:, s], true[:, s]
        mae = np.mean(np.abs(p - t))
        mse = np.mean((p - t) ** 2)
        rmse = np.sqrt(mse)
        data_range = np.ptp(t) if np.ptp(t) > 0 else 1e-8
        nrmse_range = rmse / data_range
        nrmse_capacity = rmse  # 数据已归一化(÷30.1)
        ss_res = np.sum((t - p) ** 2)
        ss_tot = np.sum((t - np.mean(t)) ** 2) if np.sum((t - np.mean(t)) ** 2) > 0 else 1e-8
        r2 = 1 - ss_res / ss_tot
        non_zero = np.abs(t) > 1e-8
        mape = np.mean(np.abs((t[non_zero] - p[non_zero]) / t[non_zero]) * 100) if non_zero.sum() > 0 else 0
        smape = np.mean(2 * np.abs(p - t) / (np.abs(p) + np.abs(t) + 1e-8) * 100)
        mbe = np.mean(p - t)
        mean_abs_t = np.mean(np.abs(t)) if np.mean(np.abs(t)) > 0 else 1e-8
        nmae_mean = mae / mean_abs_t
        nmae_capacity = mae  # 数据已归一化(÷30.1)

        results[f"step_{s}"] = {
            "mae": float(mae), "mse": float(mse), "rmse": float(rmse),
            "nrmse_range": float(nrmse_range), "nrmse_capacity": float(nrmse_capacity),
            "r2": float(r2), "mape": float(mape),
            "smape": float(smape), "mbe": float(mbe),
            "nmae_mean": float(nmae_mean), "nmae_capacity": float(nmae_capacity),
        }

    for metric in ["mae", "mse", "rmse", "nrmse_range", "nrmse_capacity", "r2",
                   "mape", "smape", "mbe", "nmae_mean", "nmae_capacity"]:
        vals = [results[f"step_{s}"][metric] for s in range(steps)]
        results[f"avg_{metric}"] = float(np.mean(vals))

    return results


# ---------------------------------------------------------------------------
# 能耗估算
# ---------------------------------------------------------------------------
def get_gpu_power_w():
    try:
        result = subprocess.run(
            ["nvidia-smi", "--query-gpu=power.draw", "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=5,
        )
        return float(result.stdout.strip())
    except Exception:
        return 250.0


def estimate_energy_wh(power_watts, duration_s):
    return power_watts * duration_s / 3600


# ---------------------------------------------------------------------------
# 模型前向辅助
# ---------------------------------------------------------------------------
def model_forward(model, x_enc, device):
    """TimeXer 前向：x_enc [B, 30, 1] → [B, 6]

    TimeXer 需要协变量+目标，单变量下复制为2列。
    features='M' 调用 forecast_multi，输出 [B, pred_len, enc_in]。
    取最后一列（目标变量）。
    """
    # [B, 30, 1] → [B, 30, 2] (协变量=目标)
    x_enc_2var = x_enc.repeat(1, 1, 2)
    out = model(x_enc_2var, None, None, None)  # [B, pred_len, enc_in=2]
    return out[:, :, -1]  # [B, 6] 取目标列


# ---------------------------------------------------------------------------
# 训练
# ---------------------------------------------------------------------------
def train(device):
    print("=" * 60)
    print("TimeXer 训练")
    print("=" * 60)

    set_seed(SEED)

    print("加载数据...")
    train_ds, train_loader = get_loader(TRAIN_DIR, "train", BATCH_SIZE, shuffle=True)
    val_ds, val_loader = get_loader(VAL_DIR, "val", BATCH_SIZE, shuffle=False)
    print(f"训练集: {len(train_ds)}, 验证集: {len(val_ds)}")

    model = build_model().to(device)
    total_params = sum(p.numel() for p in model.parameters())
    trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"总参数: {total_params:,}, 可训练参数: {trainable_params:,}")

    optimizer = torch.optim.Adam(model.parameters(), lr=LR, betas=(0.9, 0.999), eps=1e-8, weight_decay=0)
    criterion = nn.MSELoss(reduction="mean")
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS, eta_min=0)
    scaler = torch.amp.GradScaler('cuda', enabled=torch.cuda.is_available())

    train_info = {
        "batch_size": BATCH_SIZE,
        "epochs": EPOCHS,
        "optimizer": "Adam",
        "optimizer_params": {"lr": LR, "betas": (0.9, 0.999), "eps": 1e-8, "weight_decay": 0},
        "lr_scheduler": "CosineAnnealingLR",
        "scheduler_params": {"T_max": EPOCHS, "eta_min": 0},
        "amp": True,
        "lr": LR,
        "loss_fn": "MSELoss(reduction=mean)",
        "seq_len": SEQ_LEN,
        "pred_len": PRED_LEN,
        "d_model": D_MODEL,
        "n_heads": N_HEADS,
        "e_layers": E_LAYERS,
        "d_ff": D_FF,
        "dropout": DROPOUT,
        "enc_in": ENC_IN,
        "patch_len": PATCH_LEN,
        "features": "M",
        "use_norm": 1,
        "total_params": total_params,
        "trainable_params": trainable_params,
    }

    best_val_loss = float("inf")
    best_epoch = 0
    epoch_times = []
    gpu_mem_usage = []
    train_losses = []
    val_losses = []
    gpu_power_samples = []

    train_start = time.time()

    for epoch in range(1, EPOCHS + 1):
        model.train()
        epoch_start = time.time()
        epoch_loss = 0.0
        n_batches = 0

        for batch_x, batch_y in tqdm(train_loader, desc=f"Epoch {epoch}/{EPOCHS} train", leave=False):
            batch_x = batch_x.to(device)    # [B, 30, 1]
            batch_y = batch_y.to(device)     # [B, 6]

            optimizer.zero_grad()
            with torch.amp.autocast('cuda', enabled=torch.cuda.is_available()):
                pred = model_forward(model, batch_x, device)
                loss = criterion(pred, batch_y)
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()

            epoch_loss += loss.item()
            n_batches += 1

            if n_batches % 10 == 0:
                gpu_power_samples.append(get_gpu_power_w())

        epoch_time = time.time() - epoch_start
        epoch_times.append(epoch_time)
        avg_train_loss = epoch_loss / n_batches
        train_losses.append(avg_train_loss)

        gpu_mem_mb = torch.cuda.max_memory_allocated() / 1024 / 1024 if torch.cuda.is_available() else 0
        gpu_mem_usage.append(gpu_mem_mb)
        if torch.cuda.is_available():
            torch.cuda.reset_peak_memory_stats()

        # 验证
        model.eval()
        val_loss = 0.0
        val_batches = 0
        with torch.no_grad():
            for batch_x, batch_y in tqdm(val_loader, desc=f"Epoch {epoch}/{EPOCHS} val", leave=False):
                batch_x = batch_x.to(device)
                batch_y = batch_y.to(device)
                with torch.amp.autocast('cuda', enabled=torch.cuda.is_available()):
                    pred = model_forward(model, batch_x, device)
                    loss = criterion(pred, batch_y)
                val_loss += loss.item()
                val_batches += 1
        avg_val_loss = val_loss / val_batches
        val_losses.append(avg_val_loss)

        scheduler.step()

        print(f"Epoch {epoch:3d}/{EPOCHS} | train_loss={avg_train_loss:.6f} | "
              f"val_loss={avg_val_loss:.6f} | time={epoch_time:.1f}s | "
              f"gpu_mem={gpu_mem_mb:.0f}MB")

        is_best = avg_val_loss < best_val_loss
        if is_best:
            best_val_loss = avg_val_loss
            best_epoch = epoch

        ckpt = {
            "epoch": epoch,
            "model_state_dict": model.state_dict(),
            "optimizer_state_dict": optimizer.state_dict(),
            "scheduler_state_dict": scheduler.state_dict(),
            "scaler_state_dict": scaler.state_dict() if torch.cuda.is_available() else None,
            "best_val_loss": best_val_loss,
            "best_epoch": best_epoch,
            "train_losses": train_losses,
            "val_losses": val_losses,
            "configs": train_info,
        }

        if is_best:
            torch.save(ckpt, CKPT_DIR / "best_epoch.pth")
        torch.save(ckpt, CKPT_DIR / "last_epoch.pth")

        if epoch % 10 == 0:
            torch.save(ckpt, CKPT_DIR / f"epoch_{epoch:03d}.pth")

    total_train_time = time.time() - train_start

    avg_gpu_power = np.mean(gpu_power_samples) if gpu_power_samples else 250.0
    train_energy_wh = estimate_energy_wh(avg_gpu_power, total_train_time)
    peak_gpu_mem = max(gpu_mem_usage) if gpu_mem_usage else 0

    train_info.update({
        "total_train_time_s": total_train_time,
        "avg_gpu_mem_mb": float(np.mean(gpu_mem_usage)),
        "peak_gpu_mem_mb": float(peak_gpu_mem),
        "avg_epoch_time_s": float(np.mean(epoch_times)),
        "best_epoch": best_epoch,
        "best_val_loss": best_val_loss,
        "convergence_epoch": best_epoch,
        "train_energy_wh": float(train_energy_wh),
        "avg_gpu_power_w": float(avg_gpu_power),
        "train_losses": train_losses,
        "val_losses": val_losses,
    })

    print(f"\n训练完成: 总时长={total_train_time:.1f}s, best_epoch={best_epoch}, "
          f"best_val_loss={best_val_loss:.6f}, 能耗={train_energy_wh:.2f}Wh")

    return model, train_info, device


# ---------------------------------------------------------------------------
# 测试
# ---------------------------------------------------------------------------
def test(model, device):
    print("\n" + "=" * 60)
    print("TimeXer 测试")
    print("=" * 60)

    # 加载 best 模型
    ckpt = torch.load(CKPT_DIR / "best_epoch.pth", map_location=device)
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()

    # 模型大小
    model_size_mb = os.path.getsize(CKPT_DIR / "best_epoch.pth") / 1024 / 1024
    total_params = sum(p.numel() for p in model.parameters())
    params_m = total_params / 1e6

    # MACs 计算
    try:
        from thop import profile
        dummy_x = torch.randn(1, SEQ_LEN, 1).to(device)
        class MacWrapper(nn.Module):
            def __init__(self, model):
                super().__init__()
                self.model = model
            def forward(self, x_enc):
                x_enc_2var = x_enc.repeat(1, 1, 2)
                out = self.model(x_enc_2var, None, None, None)
                return out[:, :, -1]
        macs, _ = profile(MacWrapper(model).to(device), inputs=(dummy_x,), verbose=False)
        if macs == 0:
            raise ValueError("thop returned 0")
    except Exception as e:
        print(f"  thop 不可用({e})，手动估算 MACs")
        # TimeXer 手动估算
        patch_num = SEQ_LEN // PATCH_LEN  # 6
        # EnEmbedding: patch_len -> d_model (per patch, per variable)
        macs = ENC_IN * patch_num * PATCH_LEN * D_MODEL
        # ex_embedding (DataEmbedding_inverted): seq_len -> d_model (per variable)
        macs += (ENC_IN - 1) * SEQ_LEN * D_MODEL
        # encoder: e_layers * (self_attn + cross_attn + FFN)
        # self_attn on (patch_num+1) tokens
        n_tokens = patch_num + 1
        attn_macs = 4 * n_tokens * D_MODEL * D_MODEL
        # cross_attn: n_tokens queries x 1 key
        cross_macs = 2 * n_tokens * D_MODEL * D_MODEL + n_tokens * D_MODEL * D_MODEL
        # FFN: d_model -> d_ff -> d_model (per token, per variable)
        ffn_macs = ENC_IN * n_tokens * (D_MODEL * D_FF + D_FF * D_MODEL)
        macs += E_LAYERS * ENC_IN * (attn_macs + cross_macs) + E_LAYERS * ffn_macs
        # FlattenHead: d_model*(patch_num+1) -> pred_len (per variable)
        macs += ENC_IN * D_MODEL * n_tokens * PRED_LEN

    macs_kmacs = macs / 1e3
    macs_mmacs = macs / 1e6
    macs_gmacs = macs / 1e9

    # 测试数据
    test_ds, test_loader = get_loader(TEST_DIR, "test", BATCH_SIZE, shuffle=False)
    print(f"测试集: {len(test_ds)} 样本")

    all_preds = []
    all_trues = []
    test_start = time.time()
    gpu_power_samples = []
    latency_list = []

    with torch.no_grad():
        for batch_x, batch_y in tqdm(test_loader, desc="测试推理"):
            batch_x = batch_x.to(device)  # [B, 30, 1]
            batch_y = batch_y.to(device)  # [B, 6]

            t0 = time.perf_counter()
            pred = model_forward(model, batch_x, device)
            t1 = time.perf_counter()
            latency_list.append((t1 - t0) * 1000)

            all_preds.append(pred.cpu().numpy())
            all_trues.append(batch_y.cpu().numpy())

            if len(all_preds) % 5 == 0:
                gpu_power_samples.append(get_gpu_power_w())

    test_time = time.time() - test_start

    preds = np.concatenate(all_preds, axis=0)  # [N, 6]
    trues = np.concatenate(all_trues, axis=0)

    metrics = calc_metrics(preds, trues)
    throughput = len(preds) / test_time
    avg_gpu_power = np.mean(gpu_power_samples) if gpu_power_samples else 250.0
    test_energy_wh = estimate_energy_wh(avg_gpu_power, test_time)
    energy_per_sample = test_energy_wh / len(preds)

    # batch-level latency
    latency_arr = np.array(latency_list)

    # GPU 单样本 latency (warmup=50, repeat=500, cuda.synchronize)
    model.eval()
    dummy_x = torch.randn(1, SEQ_LEN, 1).to(device)
    with torch.no_grad():
        for _ in range(50):
            model_forward(model, dummy_x, device)
    if torch.cuda.is_available():
        torch.cuda.synchronize()
    gpu_single_latencies = []
    for _ in tqdm(range(500), desc="GPU单样本latency"):
        if torch.cuda.is_available():
            torch.cuda.synchronize()
        t0 = time.perf_counter()
        with torch.no_grad():
            model_forward(model, dummy_x, device)
        if torch.cuda.is_available():
            torch.cuda.synchronize()
        gpu_single_latencies.append((time.perf_counter() - t0) * 1000)
    gpu_single_arr = np.array(gpu_single_latencies)

    # CPU latency (单样本, warmup=50, repeat=500)
    cpu_model = model.cpu()
    dummy_x_cpu = torch.randn(1, SEQ_LEN, 1)
    with torch.no_grad():
        for _ in range(50):
            model_forward(cpu_model, dummy_x_cpu, "cpu")
    cpu_latencies = []
    for _ in tqdm(range(500), desc="CPU单样本latency"):
        t0 = time.perf_counter()
        with torch.no_grad():
            model_forward(cpu_model, dummy_x_cpu, "cpu")
        cpu_latencies.append((time.perf_counter() - t0) * 1000)
    cpu_lat_arr = np.array(cpu_latencies)
    model.to(device)

    # GPU peak memory
    if torch.cuda.is_available():
        torch.cuda.reset_peak_memory_stats()
    with torch.no_grad():
        for batch_x, batch_y in test_loader:
            batch_x = batch_x.to(device)
            model_forward(model, batch_x, device)
            break
    gpu_peak_mem = torch.cuda.max_memory_allocated() / 1024 / 1024 if torch.cuda.is_available() else 0

    test_metrics = {
        "model_size_mb": model_size_mb,
        "params_m": params_m,
        "macs": macs,
        "macs_kmacs": macs_kmacs,
        "macs_mmacs": macs_mmacs,
        "macs_gmacs": macs_gmacs,
        "avg_latency_ms": float(np.mean(latency_arr)),
        "avg_gpu_mem_mb": torch.cuda.memory_allocated() / 1024 / 1024 if torch.cuda.is_available() else 0,
        "metrics": metrics,
        "throughput_samples_s": throughput,
        "test_energy_wh": test_energy_wh,
        "energy_per_sample_wh": energy_per_sample,
        "cpu_latency_p50_ms": float(np.percentile(cpu_lat_arr, 50)),
        "cpu_latency_p95_ms": float(np.percentile(cpu_lat_arr, 95)),
        "cpu_latency_p99_ms": float(np.percentile(cpu_lat_arr, 99)),
        "gpu_batch_latency_p50_ms": float(np.percentile(latency_arr, 50)),
        "gpu_batch_latency_p95_ms": float(np.percentile(latency_arr, 95)),
        "gpu_batch_latency_p99_ms": float(np.percentile(latency_arr, 99)),
        "gpu_single_latency_p50_ms": float(np.percentile(gpu_single_arr, 50)),
        "gpu_single_latency_p95_ms": float(np.percentile(gpu_single_arr, 95)),
        "gpu_single_latency_p99_ms": float(np.percentile(gpu_single_arr, 99)),
        "gpu_peak_mem_mb": gpu_peak_mem,
        "test_total_time_s": test_time,
        "n_test_samples": len(preds),
    }

    print(f"测试完成: {len(preds)} 样本, 耗时={test_time:.1f}s, "
          f"avg_MAE={metrics['avg_mae']:.6f}, avg_MSE={metrics['avg_mse']:.6f}")

    return test_metrics, preds, trues


# ---------------------------------------------------------------------------
# SHAP
# ---------------------------------------------------------------------------
def run_shap(model, device):
    print("\n" + "=" * 60)
    print("SHAP 分析")
    print("=" * 60)

    import shap

    model.eval()

    # 从测试集随机抽取2000→固定2000，但explain用1000
    test_ds, _ = get_loader(TEST_DIR, "test", BATCH_SIZE, shuffle=False)
    rng = np.random.RandomState(SEED)
    n_test = len(test_ds)
    n_explain = min(2000, n_test)
    sample_indices = rng.choice(n_test, size=n_explain, replace=False)
    sample_indices = sorted(sample_indices)
    np.save(SHAP_DIR / "sample_indices.npy", np.array(sample_indices))

    # 加载测试样本
    test_inputs_power = []
    for idx in sample_indices:
        power_in, _ = test_ds[idx]
        test_inputs_power.append(power_in)  # [30, 1]
    test_power = torch.stack(test_inputs_power).to(device)  # [n_explain, 30, 1]

    # background 从训练集采样200个
    train_ds, _ = get_loader(TRAIN_DIR, "train", BATCH_SIZE, shuffle=False)
    rng_bg = np.random.RandomState(SEED)
    bg_indices = rng_bg.choice(len(train_ds), size=200, replace=False)
    bg_power_list = []
    for idx in bg_indices:
        power_in, _ = train_ds[int(idx)]
        bg_power_list.append(power_in)
    bg_power = torch.stack(bg_power_list).to(device)  # [200, 30, 1]

    bg_size = 200
    print(f"  background: {bg_size} (训练集采样)")
    print(f"  explain: {n_explain} (测试集采样)")

    # 使用前1000个explain样本
    n_shap = min(1000, n_explain)
    test_power = test_power[:n_shap]

    # 包装模型：SHAP 对功率输入(30维)做归因
    class PowerWrapper(nn.Module):
        def __init__(self, model):
            super().__init__()
            self.model = model

        def forward(self, x):
            # x: [B, 30, 1]
            x_2var = x.repeat(1, 1, 2)  # [B, 30, 2]
            out = self.model(x_2var, None, None, None)  # [B, 6, 2]
            return out[:, :, -1]  # [B, 6]

    wrapper = PowerWrapper(model).to(device)
    wrapper.eval()

    print("正在计算 SHAP 值（可能需要较长时间）...")
    try:
        explainer = shap.GradientExplainer(wrapper, bg_power)
        shap_values = explainer.shap_values(test_power)
    except Exception as e:
        print(f"  GradientExplainer 失败({e})，切换到 DeepExplainer")
        try:
            explainer = shap.DeepExplainer(wrapper, bg_power)
            shap_values = explainer.shap_values(test_power)
        except Exception as e2:
            print(f"  DeepExplainer 失败({e2})，切换到 KernelExplainer")
            # KernelExplainer: 不需要梯度，用numpy接口
            def kernel_forward(x_np):
                x_tensor = torch.from_numpy(x_np.astype(np.float32)).to(device)
                x_tensor = x_tensor.reshape(-1, SEQ_LEN, 1)
                with torch.no_grad():
                    out = wrapper(x_tensor)
                return out.cpu().numpy()

            bg_np = bg_power.cpu().numpy().reshape(bg_power.shape[0], -1)
            test_np = test_power.cpu().numpy().reshape(test_power.shape[0], -1)
            explainer = shap.KernelExplainer(kernel_forward, bg_np)
            shap_values = explainer.shap_values(test_np, nsamples=100)

    # 统一处理为 list of [n, 30]
    if isinstance(shap_values, list):
        # list 可能是 6 个 [n, 30, 1] 或 6 个 [n, 30]
        shap_list = []
        for sv in shap_values:
            sv = np.array(sv)
            if sv.ndim == 3 and sv.shape[-1] == 1:
                sv = sv.squeeze(-1)  # [n, 30, 1] → [n, 30]
            shap_list.append(sv)
    elif isinstance(shap_values, np.ndarray):
        sv = shap_values
        if sv.ndim == 4:
            # [n, 30, 1, 6] → squeeze channel → [n, 30, 6] → split
            sv = sv.squeeze(2)  # [n, 30, 6]
            shap_list = [sv[:, :, i] for i in range(sv.shape[2])]
        elif sv.ndim == 3:
            if sv.shape[-1] == 6:
                # [n, 30, 6]
                shap_list = [sv[:, :, i] for i in range(sv.shape[2])]
            elif sv.shape[-1] == 1:
                # [n, 30, 1]
                shap_list = [sv.squeeze(-1)]
            else:
                shap_list = [sv]
        elif sv.ndim == 2:
            shap_list = [sv]
        else:
            shap_list = [sv]
    else:
        shap_list = [np.array(shap_values)]

    print(f"SHAP values: {len(shap_list)} steps, each shape: {shap_list[0].shape}")

    # 保存每步 SHAP 值
    for step_idx, sv in enumerate(shap_list):
        np.save(SHAP_DIR / f"shap_values_step{step_idx}.npy", sv)

    # 6×30 逐步长 SHAP 矩阵
    shap_matrix_6x30 = np.zeros((6, 30))
    for step_idx, sv in enumerate(shap_list):
        shap_matrix_6x30[step_idx] = np.mean(np.abs(sv), axis=0)
    np.save(SHAP_DIR / "shap_matrix_6x30.npy", shap_matrix_6x30)

    # 平均特征重要性
    all_shap = np.stack(shap_list, axis=-1)  # [n, 30, 6]
    feature_importance = np.mean(np.abs(all_shap), axis=(0, 2))  # [30]
    np.save(SHAP_DIR / "feature_importance.npy", feature_importance)

    print(f"SHAP 分析完成, 保存到 {SHAP_DIR}")
    print(f"  - background_size: {bg_size}")
    print(f"  - explain_samples: {n_shap}")
    print(f"  - shap_values_step0~5.npy: 每步 SHAP 值")
    print(f"  - shap_matrix_6x30.npy: 6×30 逐步长特征重要性矩阵")
    print(f"  - feature_importance.npy: 30维平均特征重要性")


# ---------------------------------------------------------------------------
# 写 result.md
# ---------------------------------------------------------------------------
def write_result_md(train_m, test_m):
    lines = []
    lines.append("# TimeXer 训练与测试结果\n")
    lines.append(
        "**注意：以下所有指标均基于以 30.1 为归一化参数归一化后的数据集**\n"
    )
    lines.append(
        "**注意：全量训练和评测，无选择器筛选**\n"
    )

    # 训练指标
    lines.append("## 训练指标 (train)\n")
    lines.append("| 指标 | 值 |")
    lines.append("|------|-----|")
    lines.append(f"| train_total_time / s | {train_m['total_train_time_s']:.2f} |")
    lines.append(f"| train_avg_gpu_mem / MB | {train_m['avg_gpu_mem_mb']:.2f} |")
    lines.append(f"| train_peak_gpu_mem / MB | {train_m['peak_gpu_mem_mb']:.2f} |")
    lines.append(f"| train_avg_epoch_time / s | {train_m['avg_epoch_time_s']:.2f} |")
    lines.append(f"| train_convergence_epoch | {train_m['convergence_epoch']} |")
    lines.append(f"| train_energy_consumption / Wh | {train_m['train_energy_wh']:.2f} |")
    lines.append(f"| train_avg_gpu_power / W | {train_m['avg_gpu_power_w']:.1f} |")
    lines.append(f"| train_batch_size | {train_m['batch_size']} |")
    lines.append(f"| train_epochs | {train_m['epochs']} |")
    lines.append(f"| train_seq_len | {train_m['seq_len']} |")
    lines.append(f"| train_pred_len | {train_m['pred_len']} |")
    lines.append(f"| train_d_model | {train_m['d_model']} |")
    lines.append(f"| train_n_heads | {train_m['n_heads']} |")
    lines.append(f"| train_e_layers | {train_m['e_layers']} |")
    lines.append(f"| train_d_ff | {train_m['d_ff']} |")
    lines.append(f"| train_dropout | {train_m['dropout']} |")
    lines.append(f"| train_enc_in | {train_m['enc_in']} |")
    lines.append(f"| train_patch_len | {train_m['patch_len']} |")
    lines.append(f"| train_features | {train_m['features']} |")
    lines.append(f"| train_use_norm | {train_m['use_norm']} |")
    lines.append(f"| train_total_params | {train_m['total_params']:,} |")
    lines.append(f"| train_trainable_params | {train_m['trainable_params']:,} |")
    lines.append(f"| train_loss_fn | {train_m['loss_fn']} |")
    lines.append(f"| train_optimizer | {train_m['optimizer']} |")
    lines.append(f"| train_lr | {train_m['lr']} |")
    lines.append(f"| train_best_val_loss | {train_m['best_val_loss']:.6f} |")
    lines.append("")

    # Best 模型
    lines.append("## Best 模型\n")
    lines.append("| 指标 | 值 |")
    lines.append("|------|-----|")
    lines.append(f"| best_model_size / MB | {test_m['model_size_mb']:.2f} |")
    lines.append(f"| best_Params / M | {test_m['params_m']:.4f} |")
    lines.append("")

    # 测试指标
    lines.append("## 测试指标 (test)\n")
    lines.append("| 指标 | 值 |")
    lines.append("|------|-----|")
    lines.append(f"| test_MACs / KMACs | {test_m['macs_kmacs']:.4f} |")
    lines.append(f"| test_MACs / MMACs (scientific) | {test_m['macs_mmacs']:.6e} |")
    lines.append(f"| test_MACs / GMACs | {test_m['macs_gmacs']:.6e} |")
    lines.append(
        f"| test_avg_batch_latency / ms (batch_size={BATCH_SIZE}, "
        f"全测试集{test_m['n_test_samples']}样本) | {test_m['avg_latency_ms']:.4f} |"
    )
    lines.append(f"| test_avg_gpu_mem / MB | {test_m['avg_gpu_mem_mb']:.2f} |")
    lines.append(f"| test_throughput / samples/s | {test_m['throughput_samples_s']:.2f} |")
    lines.append(f"| test_energy / Wh | {test_m['test_energy_wh']:.2f} |")
    lines.append(
        f"| test_cpu_single_latency_p50 / ms (batch_size=1, warmup=50, repeat=500) "
        f"| {test_m['cpu_latency_p50_ms']:.4f} |"
    )
    lines.append(
        f"| test_cpu_single_latency_p95 / ms (batch_size=1, warmup=50, repeat=500) "
        f"| {test_m['cpu_latency_p95_ms']:.4f} |"
    )
    lines.append(
        f"| test_cpu_single_latency_p99 / ms (batch_size=1, warmup=50, repeat=500) "
        f"| {test_m['cpu_latency_p99_ms']:.4f} |"
    )
    lines.append(
        f"| test_gpu_batch_latency_p50 / ms (batch_size={BATCH_SIZE}, "
        f"全测试集{test_m['n_test_samples']}样本) | {test_m['gpu_batch_latency_p50_ms']:.4f} |"
    )
    lines.append(
        f"| test_gpu_batch_latency_p95 / ms (batch_size={BATCH_SIZE}, "
        f"全测试集{test_m['n_test_samples']}样本) | {test_m['gpu_batch_latency_p95_ms']:.4f} |"
    )
    lines.append(
        f"| test_gpu_batch_latency_p99 / ms (batch_size={BATCH_SIZE}, "
        f"全测试集{test_m['n_test_samples']}样本) | {test_m['gpu_batch_latency_p99_ms']:.4f} |"
    )
    lines.append(
        f"| test_gpu_single_latency_p50 / ms (batch_size=1, warmup=50, repeat=500, cuda.synchronize) "
        f"| {test_m['gpu_single_latency_p50_ms']:.4f} |"
    )
    lines.append(
        f"| test_gpu_single_latency_p95 / ms (batch_size=1, warmup=50, repeat=500, cuda.synchronize) "
        f"| {test_m['gpu_single_latency_p95_ms']:.4f} |"
    )
    lines.append(
        f"| test_gpu_single_latency_p99 / ms (batch_size=1, warmup=50, repeat=500, cuda.synchronize) "
        f"| {test_m['gpu_single_latency_p99_ms']:.4f} |"
    )
    lines.append(f"| test_gpu_peak_mem / MB | {test_m['gpu_peak_mem_mb']:.2f} |")
    lines.append(f"| test_total_time / s | {test_m['test_total_time_s']:.2f} |")
    lines.append(f"| test_energy_per_sample / Wh/sample | {test_m['energy_per_sample_wh']:.6f} |")
    lines.append("")

    # 逐步指标
    m = test_m["metrics"]
    steps = PRED_LEN
    for metric_name in ["mae", "mse", "rmse", "nrmse_range", "nrmse_capacity", "r2",
                        "mape", "smape", "mbe", "nmae_mean", "nmae_capacity"]:
        lines.append(f"### test_{metric_name}\n")
        lines.append(f"| 步长 | {metric_name} |")
        lines.append(f"|------|----------|")
        for s in range(steps):
            lines.append(f"| step {s + 1} | {m[f'step_{s}'][metric_name]:.6f} |")
        lines.append(f"| **平均** | {m[f'avg_{metric_name}']:.6f} |")
        lines.append("")

    # 指标公式说明
    lines.append("## 指标计算公式\n")
    lines.append("| 指标 | 公式 |")
    lines.append("|------|------|")
    lines.append("| MAE | mean(\\|pred - true\\|) |")
    lines.append("| MSE | mean((pred - true)^2) |")
    lines.append("| RMSE | sqrt(MSE) |")
    lines.append("| nRMSE_range | RMSE / (max(true) - min(true)) |")
    lines.append("| nRMSE_capacity | RMSE (数据已÷30.1归一化，即容量归一化RMSE) |")
    lines.append("| R^2 | 1 - SS_res / SS_tot |")
    lines.append("| MAPE | mean(\\|pred - true\\| / \\|true\\|) * 100 |")
    lines.append("| sMAPE | mean(2 * \\|pred - true\\| / (\\|pred\\| + \\|true\\|)) * 100 |")
    lines.append("| MBE | mean(pred - true) |")
    lines.append("| nMAE_mean | MAE / mean(\\|true\\|) |")
    lines.append("| nMAE_capacity | MAE (数据已÷30.1归一化，即容量归一化MAE) |")
    lines.append("| MACs | 模型乘加运算数 |")
    lines.append("| Throughput | n_samples / test_time |")
    lines.append("| Energy | avg_gpu_power(W) * time(s) / 3600 |")
    lines.append("")

    # SHAP
    lines.append("## SHAP 分析\n")
    lines.append(f"- SHAP random seed: {SEED}")
    lines.append(f"- SHAP background samples: 200 (从训练集采样)")
    lines.append(f"- SHAP explain samples: 1000 (从测试集采样)")
    lines.append(f"- 抽样索引保存: {SHAP_DIR}/sample_indices.npy")
    lines.append(f"- SHAP 值保存: {SHAP_DIR}/shap_values_step*.npy（每步 [1000, 30]）")
    lines.append(f"- 6×30 逐步长 SHAP 矩阵: {SHAP_DIR}/shap_matrix_6x30.npy")
    lines.append(f"- 特征重要性保存: {SHAP_DIR}/feature_importance.npy（30维平均）")
    lines.append("")
    lines.append("### 6×30 逐步长 SHAP 特征重要性矩阵\n")
    lines.append(
        "> 行=输出步(step 1~6)，列=输入时间步(t-29~t0)，"
        "值为平均绝对SHAP贡献度。\n"
    )
    lines.append("> SHAP 对功率序列(30维)做归因。\n")
    lines.append("")

    with open(RESULT_MD, "w") as f:
        f.write("\n".join(lines))

    print(f"结果已保存到: {RESULT_MD}")


# ---------------------------------------------------------------------------
# 主函数
# ---------------------------------------------------------------------------
def main():
    set_seed(SEED)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"设备: {device}")

    # 检查是否已有 best checkpoint
    best_ckpt = CKPT_DIR / "best_epoch.pth"
    if best_ckpt.exists():
        print("检测到已有 checkpoint，跳过训练，直接加载 best 模型")
        model = build_model().to(device)
        ckpt = torch.load(best_ckpt, map_location=device)
        model.load_state_dict(ckpt["model_state_dict"])
        train_metrics = ckpt.get("configs", {})
        train_metrics.update({
            "total_train_time_s": ckpt.get("configs", {}).get("total_train_time_s", 0),
            "avg_gpu_mem_mb": ckpt.get("configs", {}).get("avg_gpu_mem_mb", 0),
            "peak_gpu_mem_mb": ckpt.get("configs", {}).get("peak_gpu_mem_mb", 0),
            "avg_epoch_time_s": ckpt.get("configs", {}).get("avg_epoch_time_s", 0),
            "convergence_epoch": ckpt.get("best_epoch", 0),
            "train_energy_wh": ckpt.get("configs", {}).get("train_energy_wh", 0),
            "avg_gpu_power_w": ckpt.get("configs", {}).get("avg_gpu_power_w", 0),
            "best_val_loss": ckpt.get("best_val_loss", 0),
        })
    else:
        model, train_metrics, device = train(device)

    test_metrics, preds, trues = test(model, device)
    run_shap(model, device)
    write_result_md(train_metrics, test_metrics)
    print("\n全部完成!")


if __name__ == "__main__":
    main()
