package com.example.pvplatform.module.model.service;

import com.example.pvplatform.module.model.entity.ModelInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelService {
    public List<ModelInfo> list() {
        return List.of(
            new ModelInfo(1L, "LSTM 光伏预测模型", "lstm_v1", "NUMERIC", "v1.0", "ONLINE",
                "基于历史功率数据的短期预测模型"),
            new ModelInfo(2L, "Transformer 光伏预测模型", "transformer_v1", "NUMERIC", "v1.0", "ONLINE",
                "基于 Transformer 的光伏功率预测模型"),
            new ModelInfo(3L, "多模态光伏预测模型", "multimodal_v1", "MULTIMODAL", "v1.0", "OFFLINE",
                "多模态光伏预测模型")
        );
    }

    public ModelInfo detail(Long modelId) {
        return list().stream().filter(item -> item.modelId().equals(modelId)).findFirst().orElse(list().get(0));
    }
}
