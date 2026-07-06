package com.example.pvplatform.module.model.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.client.ModelServiceHealthClient;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.model.dto.CreateModelRequest;
import com.example.pvplatform.module.model.dto.UpdateModelRequest;
import com.example.pvplatform.module.model.vo.ModelDetailVO;
import com.example.pvplatform.module.model.vo.ModelListItemVO;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ModelService {

    private final ModelInfoMapper modelInfoMapper;
    private final ModelValidationService validationService;
    private final ModelServiceHealthClient healthClient;

    public ModelService(ModelInfoMapper modelInfoMapper,
                        ModelValidationService validationService,
                        ModelServiceHealthClient healthClient) {
        this.modelInfoMapper = modelInfoMapper;
        this.validationService = validationService;
        this.healthClient = healthClient;
    }

    // ── 用户接口 ──────────────────────────────────────────────

    public List<ModelListItemVO> list(String type) {
        var query = Wrappers.<ModelInfoDO>lambdaQuery()
                .eq(ModelInfoDO::getStatus, "ONLINE")
                .orderByAsc(ModelInfoDO::getModelId);
        if (type != null && !type.isBlank()) {
            query.eq(ModelInfoDO::getModelType, type);
        }
        return modelInfoMapper.selectList(query).stream()
                .map(this::toListItemVO)
                .toList();
    }

    public List<ModelListItemVO> adminList() {
        return modelInfoMapper.selectList(Wrappers.<ModelInfoDO>lambdaQuery()
                        .orderByAsc(ModelInfoDO::getModelId))
                .stream()
                .map(this::toAdminListItemVO)
                .toList();
    }

    public ModelDetailVO detail(Long modelId) {
        ModelInfoDO model = requireModel(modelId);
        return toDetailVO(model);
    }

    // ── 管理员接口 ──────────────────────────────────────────────

    public Long create(CreateModelRequest request) {
        validationService.validateCreate(
            request.modelCode(), request.modelType(), null,
            request.serviceModelName(), request.inputSchema(), request.outputSchema(),
            request.inputWindowMinutes(), request.inputFrameIntervalSeconds(),
            request.outputSteps(), request.outputStepMinutes());

        ModelInfoDO model = new ModelInfoDO();
        model.setModelCode(request.modelCode());
        model.setModelName(request.modelName());
        model.setModelType(request.modelType());
        model.setModelVersion(nullToDefault(request.modelVersion(), "v1.0"));
        model.setInputWindowMinutes(nullToDefault(request.inputWindowMinutes(), 30));
        model.setInputFrameIntervalSeconds(nullToDefault(request.inputFrameIntervalSeconds(), 60));
        model.setOutputSteps(nullToDefault(request.outputSteps(), 6));
        model.setOutputStepMinutes(nullToDefault(request.outputStepMinutes(), 5));
        model.setServiceModelName(request.serviceModelName());
        model.setApiPath(nullToDefault(request.apiPath(), "/model-api/predict"));
        model.setInputSchema(request.inputSchema());
        model.setOutputSchema(request.outputSchema());
        model.setStatus("OFFLINE"); // 新增默认 OFFLINE
        model.setDescription(request.description());
        model.setCreatedBy(SecurityUtils.requireCurrentUserId());
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        modelInfoMapper.insert(model);
        return model.getModelId();
    }

    public void update(Long modelId, UpdateModelRequest request) {
        ModelInfoDO existing = requireModel(modelId);

        validationService.validateUpdate(
            request.modelType(), request.serviceModelName(),
            request.inputSchema(), request.outputSchema(),
            request.inputWindowMinutes(), request.inputFrameIntervalSeconds(),
            request.outputSteps(), request.outputStepMinutes());

        ModelInfoDO model = new ModelInfoDO();
        model.setModelId(modelId);
        if (request.modelName() != null) model.setModelName(request.modelName());
        if (request.modelType() != null) model.setModelType(request.modelType());
        if (request.modelVersion() != null) model.setModelVersion(request.modelVersion());
        if (request.inputWindowMinutes() != null) model.setInputWindowMinutes(request.inputWindowMinutes());
        if (request.inputFrameIntervalSeconds() != null) model.setInputFrameIntervalSeconds(request.inputFrameIntervalSeconds());
        if (request.outputSteps() != null) model.setOutputSteps(request.outputSteps());
        if (request.outputStepMinutes() != null) model.setOutputStepMinutes(request.outputStepMinutes());
        if (request.serviceModelName() != null) model.setServiceModelName(request.serviceModelName());
        if (request.apiPath() != null) model.setApiPath(request.apiPath());
        if (request.inputSchema() != null) model.setInputSchema(request.inputSchema());
        if (request.outputSchema() != null) model.setOutputSchema(request.outputSchema());
        if (request.description() != null) model.setDescription(request.description());
        model.setUpdatedAt(LocalDateTime.now());
        // model_code 不更新
        modelInfoMapper.updateById(model);
    }

    public void updateStatus(Long modelId, String newStatus) {
        ModelInfoDO existing = requireModel(modelId);
        validationService.validateStatusTransition(existing.getStatus(), newStatus);

        // 上线前检查
        if ("ONLINE".equals(newStatus)) {
            validateOnlineReadiness(existing);
        }

        ModelInfoDO update = new ModelInfoDO();
        update.setModelId(modelId);
        update.setStatus(newStatus);
        update.setUpdatedAt(LocalDateTime.now());
        modelInfoMapper.updateById(update);
    }

    // ── 内部工具 ──────────────────────────────────────────────

    public ModelInfoDO requireOnlineModel(Long modelId) {
        ModelInfoDO model = requireModel(modelId);
        if (!"ONLINE".equals(model.getStatus())) {
            throw new BusinessException(400, "模型当前不可用");
        }
        return model;
    }

    public ModelInfoDO requireModel(Long modelId) {
        ModelInfoDO model = modelInfoMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(404, "模型不存在");
        }
        return model;
    }

    private void validateOnlineReadiness(ModelInfoDO model) {
        // 1. service_model_name 存在
        if (model.getServiceModelName() == null || model.getServiceModelName().isBlank()) {
            throw new BusinessException(400, "上线前必须设置服务模型名称");
        }
        // 2. FastAPI 健康检查
        if (!healthClient.isHealthy()) {
            throw new BusinessException(502, "模型服务不可用，无法上线模型");
        }
        // 3. 模型出现在 FastAPI 模型列表
        if (!healthClient.hasModel(model.getServiceModelName())) {
            throw new BusinessException(400, "模型服务中未找到 " + model.getServiceModelName() + "，无法上线");
        }
        // 4. 输入输出配置合法（已由校验保证）
    }

    private ModelListItemVO toListItemVO(ModelInfoDO m) {
        return new ModelListItemVO(m.getModelId(), m.getModelName(), m.getModelCode(),
            m.getModelType(), m.getModelVersion(), m.getStatus(), m.getDescription());
    }

    private ModelListItemVO toAdminListItemVO(ModelInfoDO m) {
        return new ModelListItemVO(m.getModelId(), m.getModelName(), m.getModelCode(),
            m.getModelType(), m.getModelVersion(), m.getStatus(), m.getDescription());
    }

    private ModelDetailVO toDetailVO(ModelInfoDO m) {
        return new ModelDetailVO(m.getModelId(), m.getModelCode(), m.getModelName(),
            m.getModelType(), m.getModelVersion(), m.getInputWindowMinutes(),
            m.getInputFrameIntervalSeconds(), m.getOutputSteps(), m.getOutputStepMinutes(),
            m.getServiceModelName(), m.getApiPath(), m.getInputSchema(), m.getOutputSchema(),
            m.getStatus(), m.getDescription(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private <T> T nullToDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
