package com.example.pvplatform.module.prediction.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.model.service.ModelService;
import com.example.pvplatform.module.prediction.dto.ModelInputFrame;
import com.example.pvplatform.module.prediction.dto.PredictionRequest;
import com.example.pvplatform.module.prediction.vo.PredictionDetailVO;
import com.example.pvplatform.module.prediction.vo.PredictionResultVO;
import com.example.pvplatform.module.prediction.vo.PredictionTaskVO;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationService;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.entity.PredictionResultDO;
import com.example.pvplatform.persistence.entity.PredictionTaskDO;
import com.example.pvplatform.persistence.mapper.PredictionTaskMapper;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final ModelService modelService;
    private final StationService stationService;
    private final PredictionInputService inputService;
    private final PredictionPersistenceService persistenceService;
    private final PredictionExecutionService executionService;
    private final PredictionTaskMapper taskMapper;

    public PredictionService(ModelService modelService,
                              StationService stationService,
                              PredictionInputService inputService,
                              PredictionPersistenceService persistenceService,
                              PredictionExecutionService executionService,
                              PredictionTaskMapper taskMapper) {
        this.modelService = modelService;
        this.stationService = stationService;
        this.inputService = inputService;
        this.persistenceService = persistenceService;
        this.executionService = executionService;
        this.taskMapper = taskMapper;
    }

    // ── 创建预测任务 ──────────────────────────────────────────

    public PredictionTaskVO create(PredictionRequest request) {
        // 1. 校验电站存在和权限
        PowerStation station = stationService.detail(request.stationId());
        validateStationAccess(station);

        // 2. 查询模型并要求 ONLINE
        ModelInfoDO model = modelService.requireOnlineModel(request.modelId());

        // 3. 校验 inputMode
        String inputMode = request.inputMode() == null ? "STATION_HISTORY" : request.inputMode();
        if (!"STATION_HISTORY".equals(inputMode)) {
            throw new BusinessException(400, "当前仅支持 STATION_HISTORY 输入模式");
        }

        // 4. 获取并校验输入
        List<ModelInputFrame> frames = inputService.loadStationHistory(request.stationId());

        // 5. 创建 PENDING 任务 + 保存快照（短事务）
        PredictionTaskDO task = persistenceService.createTaskWithSnapshots(
                request.stationId(), request.modelId(), inputMode,
                request.inputStartTime(), request.inputEndTime(), frames);

        Long taskId = task.getTaskId();
        log.info("预测任务已创建: taskId={}, taskNo={}, modelCode={}", taskId, task.getTaskNo(), model.getModelCode());

        try {
            // 6. 更新 RUNNING
            persistenceService.markRunning(taskId);

            // 7. 调用 FastAPI（无事务）
            ModelPredictResponse.Data responseData = executionService.execute(model, frames);
            LocalDateTime lastInputTime = executionService.getLastInputTime(frames);

            // 8. 保存结果 + 更新 SUCCESS（短事务）
            persistenceService.saveResultsAndMarkSuccess(taskId, responseData, lastInputTime);

            log.info("预测任务成功: taskId={}, costTimeMs={}", taskId, responseData.costTime());

            PredictionTaskDO completed = taskMapper.selectById(taskId);
            List<PredictionResultDO> savedResults = persistenceService.listResults(taskId);
            return buildTaskVO(completed == null ? task : completed,
                    model, savedResults);

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

    private void validateStationAccess(PowerStation station) {
        // 基础检查：电站存在
        // 权限检查通过 SecurityUtils 获取用户，后续可增强为查询 UserStationPermission
        SecurityUtils.requireCurrentUserId();
    }

    private PredictionTaskVO buildTaskVO(PredictionTaskDO task, ModelInfoDO model,
                                          List<PredictionResultDO> predictions) {
        List<PredictionResultVO> resultVOs = predictions.stream()
                .map(this::toResultVO)
                .toList();
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
