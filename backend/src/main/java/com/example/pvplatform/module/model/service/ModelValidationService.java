package com.example.pvplatform.module.model.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ModelValidationService {

    private static final Set<String> VALID_STATUSES = Set.of("ONLINE", "OFFLINE", "TESTING");
    private static final Set<String> VALID_TYPES = Set.of("NUMERIC", "MULTIMODAL", "IMAGE_TO_NUMERIC");
    private static final Set<String> ALLOWED_TRANSITIONS = Set.of(
        "OFFLINE->TESTING", "TESTING->ONLINE", "TESTING->OFFLINE",
        "ONLINE->OFFLINE", "OFFLINE->ONLINE"
    );

    private final ModelInfoMapper modelInfoMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelValidationService(ModelInfoMapper modelInfoMapper) {
        this.modelInfoMapper = modelInfoMapper;
    }

    public void validateCreate(String modelCode, String modelType, String status,
                                String serviceModelName, String inputSchema, String outputSchema,
                                Integer inputWindowMinutes, Integer inputFrameIntervalSeconds,
                                Integer outputSteps, Integer outputStepMinutes) {
        // modelCode 唯一性
        if (modelInfoMapper.exists(Wrappers.<ModelInfoDO>lambdaQuery()
                .eq(ModelInfoDO::getModelCode, modelCode))) {
            throw new BusinessException(400, "模型编码已存在");
        }
        // 类型校验
        if (!VALID_TYPES.contains(modelType)) {
            throw new BusinessException(400, "模型类型无效，允许: NUMERIC/MULTIMODAL/IMAGE_TO_NUMERIC");
        }
        // 状态校验
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new BusinessException(400, "模型状态无效，允许: ONLINE/OFFLINE/TESTING");
        }
        // serviceModelName 非空
        if (serviceModelName == null || serviceModelName.isBlank()) {
            throw new BusinessException(400, "服务模型名称不能为空");
        }
        // 数值参数必须为正
        if (inputWindowMinutes != null && inputWindowMinutes <= 0) {
            throw new BusinessException(400, "输入窗口必须为正数");
        }
        if (inputFrameIntervalSeconds != null && inputFrameIntervalSeconds <= 0) {
            throw new BusinessException(400, "输入帧间隔必须为正数");
        }
        if (outputSteps != null && outputSteps <= 0) {
            throw new BusinessException(400, "输出步数必须为正数");
        }
        if (outputStepMinutes != null && outputStepMinutes <= 0) {
            throw new BusinessException(400, "输出步长必须为正数");
        }
        // JSON Schema 合法性
        if (inputSchema != null && !inputSchema.isBlank()) {
            validateJson(inputSchema, "inputSchema");
        }
        if (outputSchema != null && !outputSchema.isBlank()) {
            validateJson(outputSchema, "outputSchema");
        }
    }

    public void validateStatusTransition(String currentStatus, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new BusinessException(400, "无效的模型状态: " + newStatus);
        }
        String transition = currentStatus + "->" + newStatus;
        if (!ALLOWED_TRANSITIONS.contains(transition)) {
            throw new BusinessException(400,
                "不允许从 " + currentStatus + " 转换为 " + newStatus);
        }
    }

    public void validateUpdate(String modelType, String serviceModelName,
                                String inputSchema, String outputSchema,
                                Integer inputWindowMinutes, Integer inputFrameIntervalSeconds,
                                Integer outputSteps, Integer outputStepMinutes) {
        if (modelType != null && !VALID_TYPES.contains(modelType)) {
            throw new BusinessException(400, "模型类型无效，允许: NUMERIC/MULTIMODAL/IMAGE_TO_NUMERIC");
        }
        if (serviceModelName != null && serviceModelName.isBlank()) {
            throw new BusinessException(400, "服务模型名称不能为空");
        }
        if (inputWindowMinutes != null && inputWindowMinutes <= 0) {
            throw new BusinessException(400, "输入窗口必须为正数");
        }
        if (inputFrameIntervalSeconds != null && inputFrameIntervalSeconds <= 0) {
            throw new BusinessException(400, "输入帧间隔必须为正数");
        }
        if (outputSteps != null && outputSteps <= 0) {
            throw new BusinessException(400, "输出步数必须为正数");
        }
        if (outputStepMinutes != null && outputStepMinutes <= 0) {
            throw new BusinessException(400, "输出步长必须为正数");
        }
        if (inputSchema != null && !inputSchema.isBlank()) {
            validateJson(inputSchema, "inputSchema");
        }
        if (outputSchema != null && !outputSchema.isBlank()) {
            validateJson(outputSchema, "outputSchema");
        }
    }

    private void validateJson(String json, String fieldName) {
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(400, fieldName + " 不是合法的 JSON");
        }
    }
}
