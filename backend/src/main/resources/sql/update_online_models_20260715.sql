-- 2026-07-15 模型上线状态调整（可重复执行）
-- 本次仅允许以下 8 个 model_code 在线且可调用：
-- PatchTST、DLinear、iTransformer、TimeXer、TimeMixer、TSMixer、Transformer、CNN_MLP（CNN+MLP）
-- 只更新状态字段，不删除模型，也不改变模型广场展示元数据。

START TRANSACTION;

UPDATE model_info
SET status = CASE
        WHEN model_code IN (
            'PatchTST', 'DLinear', 'iTransformer', 'TimeXer',
            'TimeMixer', 'TSMixer', 'Transformer', 'CNN_MLP'
        ) THEN 'ONLINE'
        ELSE 'OFFLINE'
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND status <> CASE
        WHEN model_code IN (
            'PatchTST', 'DLinear', 'iTransformer', 'TimeXer',
            'TimeMixer', 'TSMixer', 'Transformer', 'CNN_MLP'
        ) THEN 'ONLINE'
        ELSE 'OFFLINE'
    END;

COMMIT;

-- 执行后核对：应只返回上述 8 个稳定 model_code。
SELECT model_id, model_code, model_name, status, marketplace_visible
FROM model_info
WHERE deleted = 0 AND status = 'ONLINE'
ORDER BY sort_order, model_id;

