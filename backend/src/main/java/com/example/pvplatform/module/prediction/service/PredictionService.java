package com.example.pvplatform.module.prediction.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.model.service.ModelService;
import com.example.pvplatform.module.openapi.service.ApiCallLogService;
import com.example.pvplatform.module.openapi.service.ApiKeyService;
import com.example.pvplatform.module.openapi.service.ApiQuotaService;
import com.example.pvplatform.module.openapi.service.OpenAccountService;
import com.example.pvplatform.module.prediction.dto.ModelInputFrame;
import com.example.pvplatform.module.prediction.dto.PredictionRequest;
import com.example.pvplatform.module.prediction.vo.PredictionCreateVO;
import com.example.pvplatform.module.prediction.vo.PredictionDetailVO;
import com.example.pvplatform.module.prediction.vo.PredictionResultVO;
import com.example.pvplatform.module.prediction.vo.PredictionTaskVO;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationService;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.entity.ApiKeyDO;
import com.example.pvplatform.persistence.entity.PredictionResultDO;
import com.example.pvplatform.persistence.entity.PredictionTaskDO;
import com.example.pvplatform.persistence.mapper.PredictionTaskMapper;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ModelService modelService;
    private final StationService stationService;
    private final PredictionInputService inputService;
    private final PredictionPersistenceService persistenceService;
    private final PredictionExecutionService executionService;
    private final PredictionTaskMapper taskMapper;
    private final ApiKeyService apiKeyService;
    private final ApiQuotaService apiQuotaService;
    private final OpenAccountService openAccountService;
    private final ApiCallLogService apiCallLogService;

    public PredictionService(ModelService modelService,
                              StationService stationService,
                              PredictionInputService inputService,
                              PredictionPersistenceService persistenceService,
                              PredictionExecutionService executionService,
                              PredictionTaskMapper taskMapper,
                              ApiKeyService apiKeyService,
                              ApiQuotaService apiQuotaService,
                              OpenAccountService openAccountService,
                              ApiCallLogService apiCallLogService) {
        this.modelService = modelService;
        this.stationService = stationService;
        this.inputService = inputService;
        this.persistenceService = persistenceService;
        this.executionService = executionService;
        this.taskMapper = taskMapper;
        this.apiKeyService = apiKeyService;
        this.apiQuotaService = apiQuotaService;
        this.openAccountService = openAccountService;
        this.apiCallLogService = apiCallLogService;
    }

    // ── 创建预测任务 ──────────────────────────────────────────

    public PredictionCreateVO create(PredictionRequest request) {
        LocalDateTime apiStartedAt = LocalDateTime.now();
        Long currentUserId = SecurityUtils.requireCurrentUserId();
        ApiKeyDO selectedKey = null;
        if (request.apiKeyId() != null) {
            selectedKey = apiKeyService.requireOwnActive(request.apiKeyId());
            apiQuotaService.checkAndConsume(selectedKey);
            openAccountService.requireApiCallBalance(currentUserId);
        }
        // 1. 校验电站存在和权限
        PowerStation station = stationService.detail(request.stationId());
        validateStationAccess(station);

        // 2. 查询模型并要求 ONLINE
        ModelInfoDO model = modelService.requireOnlineModel(request.modelId());

        // 3. 校验 inputMode
        String inputMode = request.inputMode() == null ? "MANUAL_MULTIMODAL" : request.inputMode();
        if (!"MANUAL_MULTIMODAL".equals(inputMode)) {
            throw new BusinessException(400, "当前仅支持 MANUAL_MULTIMODAL 输入模式");
        }

        // 4. 获取并校验输入（按模型类型决定需要哪些输入）
        String modelType = model.getModelType() != null ? model.getModelType().toUpperCase() : "";
        List<ModelInputFrame> frames = convertNumericValues(request.numericValues(), modelType);
        List<ModelPredictRequest.ImageFrame> imageFrames = validateImages(request.inputImages(), frames, modelType);

        // 5. 创建 PENDING 任务 + 保存快照（短事务）
        String inputStartTime = request.inputStartTime() != null
                ? request.inputStartTime() : frames.get(0).time().format(FORMATTER);
        String inputEndTime = request.inputEndTime() != null
                ? request.inputEndTime() : frames.get(frames.size() - 1).time().format(FORMATTER);
        PredictionTaskDO task = persistenceService.createTaskWithSnapshots(
                request.stationId(), request.modelId(), inputMode,
                inputStartTime, inputEndTime, frames);

        Long taskId = task.getTaskId();
        log.info("预测任务已创建: taskId={}, taskNo={}, modelCode={}", taskId, task.getTaskNo(), model.getModelCode());

        try {
            // 6. 更新 RUNNING
            persistenceService.markRunning(taskId);

            // 7. 调用 FastAPI（无事务）
            ModelPredictResponse.Data responseData = executionService.execute(model, frames, imageFrames);
            LocalDateTime lastInputTime = executionService.getLastInputTime(frames);

            // 8. 保存结果 + 更新 SUCCESS（短事务）
            persistenceService.saveResultsAndMarkSuccess(taskId, responseData, lastInputTime);

            if (selectedKey != null) {
                openAccountService.chargeApiCall(currentUserId, selectedKey.getApiKeyId(), model.getModelId());
                long inputUnits = frames.size();
                long outputUnits = responseData.predictions() == null ? 0L : responseData.predictions().size();
                apiCallLogService.save(currentUserId, selectedKey.getApiKeyId(), model.getModelId(),
                    "/api/predictions", "POST", "CONSOLE", apiStartedAt, 200, null,
                    "{\"source\":\"MODEL_CONSOLE\",\"inputFrames\":" + inputUnits + "}",
                    "{\"taskId\":" + taskId + "}", inputUnits, outputUnits, inputUnits + outputUnits);
            }

            log.info("预测任务成功: taskId={}, costTimeMs={}", taskId, responseData.costTime());

            return new PredictionCreateVO(taskId);

        } catch (BusinessException e) {
            // 9. 异常时更新 FAILED（独立事务）
            persistenceService.markFailed(taskId, e.getMessage());
            log.error("预测任务失败: taskId={}, error={}", taskId, e.getMessage());
            throw e;
        } catch (Exception e) {
            String safeMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            persistenceService.markFailed(taskId, safeMessage);
            log.error("预测任务异常: taskId={}", taskId, e);
            throw new BusinessException(500, "预测执行失败");
        }
    }

    // ── 任务详情 ──────────────────────────────────────────────

    public PredictionDetailVO detail(Long taskId) {
        PredictionTaskDO task = persistenceService.requireUserTask(taskId);
        ModelInfoDO model = modelService.requireModel(task.getModelId());
        PowerStation station = stationService.detail(task.getStationId());
        return new PredictionDetailVO(
                task.getTaskId(), task.getTaskNo(),
                task.getStationId(), station.stationName(),
                task.getModelId(), model.getModelName(), model.getModelCode(),
                task.getInputMode(), task.getStatus(),
                task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt(),
                task.getCostTimeMs(), task.getErrorMessage());
    }

    // ── 预测结果 ──────────────────────────────────────────────

    public List<PredictionResultVO> results(Long taskId) {
        persistenceService.requireUserTask(taskId);
        return persistenceService.listResults(taskId).stream()
                .map(this::toResultVO)
                .toList();
    }

    // ── 预测历史 ──────────────────────────────────────────────

    public PageResult<PredictionTaskVO> history(int pageNum, int pageSize,
                                                  Long stationId, Long modelId, String status) {
        Long userId = SecurityUtils.requireCurrentUserId();
        SecurityUser currentUser = SecurityUtils.getCurrentUser();
        boolean isAdmin = currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        var query = Wrappers.<PredictionTaskDO>lambdaQuery();
        if (!isAdmin) {
            query.eq(PredictionTaskDO::getUserId, userId);
        }
        if (stationId != null) {
            query.eq(PredictionTaskDO::getStationId, stationId);
        }
        if (modelId != null) {
            query.eq(PredictionTaskDO::getModelId, modelId);
        }
        if (status != null && !status.isBlank()) {
            query.eq(PredictionTaskDO::getStatus, status);
        }
        query.orderByDesc(PredictionTaskDO::getCreatedAt);

        Page<PredictionTaskDO> page = taskMapper.selectPage(
                new Page<>(pageNum, pageSize), query);

        List<PredictionTaskVO> records = page.getRecords().stream()
                .map(task -> buildHistoryVO(task))
                .toList();

        return new PageResult<>(page.getTotal(), pageNum, pageSize, records);
    }

    // ── 内部工具 ──────────────────────────────────────────────


    private List<ModelInputFrame> convertNumericValues(List<PredictionRequest.NumericValue> values, String modelType) {
        // 云图时序模型(MULTIMODAL)不需要功率数据
        boolean needNumeric = !"MULTIMODAL".equals(modelType);
        if (!needNumeric) {
            // 云图模型：用占位数据填充30个时间步
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            return java.util.stream.IntStream.range(0, 30)
                    .mapToObj(i -> new ModelInputFrame(now.minusMinutes(29 - i), 0, 0, 0))
                    .toList();
        }
        if (values == null || values.size() != 30) {
            throw new BusinessException(400, "数值输入必须包含 30 个点");
        }
        List<ModelInputFrame> frames;
        try {
            frames = values.stream().map(value -> {
                if (!Double.isFinite(value.value())) {
                    throw new BusinessException(400, "数值输入包含非法值");
                }
                double numericValue = value.value();
                return new ModelInputFrame(
                        LocalDateTime.parse(value.time(), FORMATTER),
                        numericValue,
                        numericValue,
                        numericValue);
            }).toList();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException(400, "数值输入时间格式不合法");
        }
        validateOneMinuteInterval(frames);
        return frames;
    }

    private List<ModelPredictRequest.ImageFrame> validateImages(List<ModelPredictRequest.ImageFrame> images,
                                                                 List<ModelInputFrame> frames, String modelType) {
        // 功率时序模型不需要云图
        if ("NUMERIC".equals(modelType)) {
            return frames.stream()
                    .map(f -> new ModelPredictRequest.ImageFrame(
                            f.time().format(FORMATTER),
                            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="))
                    .toList();
        }
        // 云图模型和多模态模型都需要6张云图
        if (images == null || images.size() != 6) {
            throw new BusinessException(400, "图片输入必须包含 6 张");
        }
        for (ModelPredictRequest.ImageFrame image : images) {
            if (image.image() == null || image.image().isBlank()) {
                throw new BusinessException(400, "图片输入不能为空");
            }
        }
        // 将6张图片填充为30张（每张重复5次），时间与30帧对齐
        return java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> {
                    int imgIndex = i / 5;
                    return new ModelPredictRequest.ImageFrame(
                            frames.get(i).time().format(FORMATTER),
                            images.get(imgIndex).image());
                })
                .toList();
    }

    private void validateOneMinuteInterval(List<ModelInputFrame> frames) {
        for (int i = 1; i < frames.size(); i++) {
            long seconds = Duration.between(frames.get(i - 1).time(), frames.get(i).time()).toSeconds();
            if (seconds != 60) {
                throw new BusinessException(400, "数值时间序列必须按 1 分钟间隔连续");
            }
        }
    }

    private void validateStationAccess(PowerStation station) {
        // 基础检查：电站存在
        // 权限检查通过 SecurityUtils 获取用户，后续可增强为查询 UserStationPermission
        SecurityUtils.requireCurrentUserId();
    }

    private PredictionTaskVO buildTaskVO(PredictionTaskDO task, ModelInfoDO model,
                                          List<ModelPredictResponse.Prediction> predictions) {
        List<PredictionResultVO> resultVOs = predictions.stream().map(p -> {
            LocalDateTime predictTime = task.getInputEndTime() != null
                    ? task.getInputEndTime().plusMinutes(p.timeOffset())
                    : LocalDateTime.now().plusMinutes(p.timeOffset());
            return new PredictionResultVO(p.timeOffset(), predictTime,
                    BigDecimal.valueOf(p.predictPower()), null, null, null);
        }).toList();

        return new PredictionTaskVO(task.getTaskId(), task.getTaskNo(), task.getStatus(),
                model.getModelName(), model.getModelCode(), task.getStationId(),
                task.getInputMode(), task.getCreatedAt(), task.getCostTimeMs(), resultVOs);
    }

    private PredictionTaskVO buildHistoryVO(PredictionTaskDO task) {
        try {
            ModelInfoDO model = modelService.requireModel(task.getModelId());
            return new PredictionTaskVO(task.getTaskId(), task.getTaskNo(), task.getStatus(),
                    model.getModelName(), model.getModelCode(), task.getStationId(),
                    task.getInputMode(), task.getCreatedAt(), task.getCostTimeMs(), null);
        } catch (BusinessException e) {
            return new PredictionTaskVO(task.getTaskId(), task.getTaskNo(), task.getStatus(),
                    null, null, task.getStationId(),
                    task.getInputMode(), task.getCreatedAt(), task.getCostTimeMs(), null);
        }
    }

    private PredictionResultVO toResultVO(PredictionResultDO r) {
        return new PredictionResultVO(r.getTimeOffsetMinutes(), r.getPredictTime(),
                r.getPredictPowerKw(), r.getActualPowerKw(),
                r.getErrorValue(), r.getErrorRate());
    }
}
