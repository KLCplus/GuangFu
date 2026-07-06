package com.example.pvplatform.module.model.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.model.dto.ModelRequest;
import com.example.pvplatform.module.model.entity.ModelInfo;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelService {
    private final ModelInfoMapper modelInfoMapper;

    public ModelService(ModelInfoMapper modelInfoMapper) {
        this.modelInfoMapper = modelInfoMapper;
    }

    public List<ModelInfo> list() {
        return modelInfoMapper.selectList(Wrappers.<ModelInfoDO>lambdaQuery()
                .orderByAsc(ModelInfoDO::getModelId))
            .stream().map(this::toEntity).toList();
    }

    public ModelInfo detail(Long modelId) {
        ModelInfoDO model = modelInfoMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(404, "模型不存在");
        }
        return toEntity(model);
    }

    public Long create(ModelRequest request) {
        ModelInfoDO model = new ModelInfoDO();
        model.setModelName(request.modelName());
        model.setModelCode(request.modelCode());
        model.setModelType(request.modelType());
        model.setModelVersion(request.modelVersion());
        model.setServiceModelName(request.modelCode());
        model.setStatus(request.modelStatus() == null ? "OFFLINE" : request.modelStatus());
        model.setDescription(request.description());
        modelInfoMapper.insert(model);
        return model.getModelId();
    }

    public void updateStatus(Long modelId, String status) {
        ModelInfoDO model = new ModelInfoDO();
        model.setModelId(modelId);
        model.setStatus(status);
        if (modelInfoMapper.updateById(model) == 0) {
            throw new BusinessException(404, "模型不存在");
        }
    }

    private ModelInfo toEntity(ModelInfoDO model) {
        return new ModelInfo(model.getModelId(), model.getModelName(), model.getModelCode(),
            model.getModelType(), model.getModelVersion(), model.getStatus(), model.getDescription());
    }
}
