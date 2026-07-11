package com.example.pvplatform.module.model.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.client.ModelServiceHealthClient;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.model.dto.CreateModelRequest;
import com.example.pvplatform.module.model.dto.UpdateModelRequest;
import com.example.pvplatform.module.model.vo.ModelDetailVO;
import com.example.pvplatform.module.model.vo.ModelListItemVO;
import com.example.pvplatform.module.model.vo.ModelMetricVO;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.entity.ModelMetricDO;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.example.pvplatform.persistence.mapper.ModelMetricMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModelService {

    private final ModelInfoMapper modelInfoMapper;
    private final ModelMetricMapper modelMetricMapper;
    private final ModelValidationService validationService;
    private final ModelServiceHealthClient healthClient;
    private final ObjectMapper objectMapper;

    public ModelService(ModelInfoMapper modelInfoMapper,
                        ModelMetricMapper modelMetricMapper,
                        ModelValidationService validationService,
                        ModelServiceHealthClient healthClient,
                        ObjectMapper objectMapper) {
        this.modelInfoMapper = modelInfoMapper;
        this.modelMetricMapper = modelMetricMapper;
        this.validationService = validationService;
        this.healthClient = healthClient;
        this.objectMapper = objectMapper;
    }

    // ── 用户接口 ──────────────────────────────────────────────

    @Cacheable(cacheNames = "model:list", key = "#type == null ? 'all' : #type")
    public List<ModelListItemVO> list(String type) {
        var query = Wrappers.<ModelInfoDO>lambdaQuery()
                .eq(ModelInfoDO::getMarketplaceVisible, true)
                .orderByDesc(ModelInfoDO::getIsFeatured)
                .orderByAsc(ModelInfoDO::getSortOrder)
                .orderByAsc(ModelInfoDO::getModelId);
        if (type != null && !type.isBlank()) {
            query.eq(ModelInfoDO::getModelType, type);
        }
        return modelInfoMapper.selectList(query).stream()
                .map(this::toListItemVO)
                .toList();
    }

    @Cacheable(cacheNames = "model:admin-list", key = "'all'")
    public List<ModelListItemVO> adminList() {
        return modelInfoMapper.selectList(Wrappers.<ModelInfoDO>lambdaQuery()
                        .orderByAsc(ModelInfoDO::getSortOrder)
                        .orderByAsc(ModelInfoDO::getModelId))
                .stream()
                .map(this::toAdminListItemVO)
                .toList();
    }

    @Cacheable(cacheNames = "model:detail", key = "#modelId")
    public ModelDetailVO detail(Long modelId) {
        ModelInfoDO model = requireModel(modelId);
        return toDetailVO(model);
    }

    // ── 管理员接口 ──────────────────────────────────────────────

    @CacheEvict(cacheNames = {"model:list", "model:admin-list", "model:detail"}, allEntries = true)
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

    @CacheEvict(cacheNames = {"model:list", "model:admin-list", "model:detail"}, allEntries = true)
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

    @CacheEvict(cacheNames = {"model:list", "model:admin-list", "model:detail"}, allEntries = true)
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
            m.getModelType(), m.getModelVersion(), m.getStatus(), m.getDescription(),
            firstText(m.getShortDescription(), m.getDescription()), parseStringList(m.getTags()),
            m.getModelFamily(), m.getProvider(), m.getReleaseYear(), visible(m),
            Boolean.TRUE.equals(m.getIsFeatured()), sortOrder(m));
    }

    private ModelListItemVO toAdminListItemVO(ModelInfoDO m) {
        return toListItemVO(m);
    }

    private ModelDetailVO toDetailVO(ModelInfoDO m) {
        return new ModelDetailVO(m.getModelId(), m.getModelCode(), m.getModelName(),
            m.getModelType(), m.getModelVersion(), m.getInputWindowMinutes(),
            m.getInputFrameIntervalSeconds(), m.getOutputSteps(), m.getOutputStepMinutes(),
            m.getServiceModelName(), m.getApiPath(), parseObject(m.getInputSchema()), parseObject(m.getOutputSchema()),
            m.getStatus(), m.getDescription(), firstText(m.getShortDescription(), m.getDescription()),
            parseStringList(m.getTags()), m.getModelFamily(), m.getProvider(), m.getReleaseYear(),
            m.getPaperTitle(), m.getPaperUrl(), m.getSourceUrl(), parseStringList(m.getCapabilities()),
            parseStringList(m.getApplicableScenarios()), parseStringList(m.getAdvantages()),
            parseStringList(m.getLimitations()), parseStringList(m.getSupportedInputModes()),
            parseObject(m.getReferenceInfo()), visible(m), Boolean.TRUE.equals(m.getIsFeatured()),
            sortOrder(m), metrics(m.getModelId()), m.getCreatedAt(), m.getUpdatedAt());
    }

    private List<ModelMetricVO> metrics(Long modelId) {
        return modelMetricMapper.selectList(Wrappers.<ModelMetricDO>lambdaQuery()
                .eq(ModelMetricDO::getModelId, modelId)
                .orderByDesc(ModelMetricDO::getEvaluatedAt)
                .orderByDesc(ModelMetricDO::getMetricId))
            .stream()
            .map(metric -> new ModelMetricVO(metric.getMetricId(), metric.getDatasetName(), metric.getMae(),
                metric.getRmse(), metric.getMape(), metric.getR2Score(), parseObject(metric.getMetricJson()),
                metric.getEvaluatedAt(), metric.getCreatedAt()))
            .toList();
    }

    private Boolean visible(ModelInfoDO m) {
        return m.getMarketplaceVisible() == null || Boolean.TRUE.equals(m.getMarketplaceVisible());
    }

    private Integer sortOrder(ModelInfoDO m) {
        return m.getSortOrder() == null ? Math.toIntExact(m.getModelId() == null ? 0 : m.getModelId()) : m.getSortOrder();
    }

    private String firstText(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            if (value.trim().startsWith("[")) {
                return objectMapper.readValue(value, new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
            // Fall through to comma-separated parsing for legacy rows.
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",")) {
            if (!item.isBlank()) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private Map<String, Object> parseObject(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of("raw", value);
        }
    }

    private <T> T nullToDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
