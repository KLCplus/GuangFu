package com.example.pvplatform.module.prediction.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.module.prediction.dto.ModelInputFrame;
import com.example.pvplatform.persistence.entity.PredictionInputSnapshotDO;
import com.example.pvplatform.persistence.entity.PredictionResultDO;
import com.example.pvplatform.persistence.entity.PredictionTaskDO;
import com.example.pvplatform.persistence.mapper.PredictionInputSnapshotMapper;
import com.example.pvplatform.persistence.mapper.PredictionResultMapper;
import com.example.pvplatform.persistence.mapper.PredictionTaskMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PredictionPersistenceService {

    private final PredictionTaskMapper taskMapper;
    private final PredictionInputSnapshotMapper inputMapper;
    private final PredictionResultMapper resultMapper;

    public PredictionPersistenceService(PredictionTaskMapper taskMapper,
                                         PredictionInputSnapshotMapper inputMapper,
                                         PredictionResultMapper resultMapper) {
        this.taskMapper = taskMapper;
        this.inputMapper = inputMapper;
        this.resultMapper = resultMapper;
    }

    /**
     * 事务 1：创建 PENDING 任务 + 保存输入快照
     */
    @Transactional
    public PredictionTaskDO createTaskWithSnapshots(Long stationId, Long modelId,
                                                     String inputMode, String inputStartTime,
                                                     String inputEndTime,
                                                     List<ModelInputFrame> frames) {
        return createTaskWithSnapshotsForUser(SecurityUtils.requireCurrentUserId(), stationId, modelId,
            inputMode, inputStartTime, inputEndTime, frames);
    }

    @Transactional
    public PredictionTaskDO createTaskWithSnapshotsForUser(Long userId, Long stationId, Long modelId,
                                                            String inputMode, String inputStartTime,
                                                            String inputEndTime,
                                                            List<ModelInputFrame> frames) {
        PredictionTaskDO task = new PredictionTaskDO();
        task.setTaskNo("PRED-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        task.setUserId(userId);
        task.setStationId(stationId);
        task.setModelId(modelId);
        task.setInputMode(inputMode == null ? "STATION_HISTORY" : inputMode);
        task.setInputStartTime(inputStartTime != null ? parseTime(inputStartTime) : null);
        task.setInputEndTime(inputEndTime != null ? parseTime(inputEndTime) : null);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // 保存输入快照
        saveSnapshots(task.getTaskId(), frames);

        return task;
    }

    /**
     * 更新任务为 RUNNING
     */
    public void markRunning(Long taskId) {
        PredictionTaskDO task = new PredictionTaskDO();
        task.setTaskId(taskId);
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 事务 2：保存结果并更新 SUCCESS
     */
    @Transactional
    public void saveResultsAndMarkSuccess(Long taskId, ModelPredictResponse.Data data,
                                           LocalDateTime lastInputTime) {
        List<ModelPredictResponse.Prediction> predictions = data.predictions();
        // 先验证无重复 offset
        Set<Integer> offsets = new HashSet<>();
        for (ModelPredictResponse.Prediction p : predictions) {
            if (!offsets.add(p.timeOffset())) {
                throw new BusinessException(502, "预测结果存在重复时间偏移");
            }
        }

        // 批量保存结果
        List<PredictionResultDO> results = predictions.stream().map(p -> {
            PredictionResultDO r = new PredictionResultDO();
            r.setTaskId(taskId);
            r.setTimeOffsetMinutes(p.timeOffset());
            r.setPredictTime(lastInputTime.plusMinutes(p.timeOffset()));
            r.setPredictPowerKw(BigDecimal.valueOf(p.predictPower()));
            r.setCreatedAt(LocalDateTime.now());
            return r;
        }).toList();
        for (PredictionResultDO r : results) {
            resultMapper.insert(r);
        }

        // 更新任务为 SUCCESS
        PredictionTaskDO task = new PredictionTaskDO();
        task.setTaskId(taskId);
        task.setStatus("SUCCESS");
        task.setCostTimeMs(data.costTime());
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 事务 3：更新任务为 FAILED
     */
    @Transactional
    public void markFailed(Long taskId, String errorMessage) {
        String safeMessage = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500) : errorMessage;
        PredictionTaskDO task = new PredictionTaskDO();
        task.setTaskId(taskId);
        task.setStatus("FAILED");
        task.setErrorMessage(safeMessage);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 查询任务（含归属校验）
     */
    public PredictionTaskDO requireTask(Long taskId) {
        PredictionTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "预测任务不存在");
        }
        return task;
    }

    /**
     * 查询任务并校验归属
     */
    public PredictionTaskDO requireUserTask(Long taskId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        PredictionTaskDO task = taskMapper.selectOne(Wrappers.<PredictionTaskDO>lambdaQuery()
                .eq(PredictionTaskDO::getTaskId, taskId)
                .eq(PredictionTaskDO::getUserId, userId));
        if (task == null) {
            throw new BusinessException(404, "预测任务不存在");
        }
        return task;
    }

    public List<PredictionResultDO> listResults(Long taskId) {
        return resultMapper.selectList(Wrappers.<PredictionResultDO>lambdaQuery()
                .eq(PredictionResultDO::getTaskId, taskId)
                .orderByAsc(PredictionResultDO::getTimeOffsetMinutes));
    }

    private void saveSnapshots(Long taskId, List<ModelInputFrame> frames) {
        List<PredictionInputSnapshotDO> snapshots = frames.stream().map(f -> {
            PredictionInputSnapshotDO s = new PredictionInputSnapshotDO();
            s.setTaskId(taskId);
            s.setPointTime(f.time());
            s.setPowerKw(BigDecimal.valueOf(f.power()));
            s.setTemperatureC(BigDecimal.valueOf(f.temperature()));
            s.setIrradianceWM2(BigDecimal.valueOf(f.irradiance()));
            s.setCreatedAt(LocalDateTime.now());
            return s;
        }).toList();
        for (PredictionInputSnapshotDO s : snapshots) {
            inputMapper.insert(s);
        }
    }

    private LocalDateTime parseTime(String value) {
        return LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
