package com.example.pvplatform.module.prediction.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.client.ModelServiceClient;
import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.model.entity.ModelInfo;
import com.example.pvplatform.module.model.service.ModelService;
import com.example.pvplatform.module.prediction.dto.PredictionRequest;
import com.example.pvplatform.module.prediction.vo.PredictionTaskVO;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import com.example.pvplatform.persistence.entity.PredictionInputSnapshotDO;
import com.example.pvplatform.persistence.entity.PredictionResultDO;
import com.example.pvplatform.persistence.entity.PredictionTaskDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.PredictionInputSnapshotMapper;
import com.example.pvplatform.persistence.mapper.PredictionResultMapper;
import com.example.pvplatform.persistence.mapper.PredictionTaskMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class PredictionService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ModelServiceClient modelServiceClient;
    private final ModelService modelService;
    private final PvDataService pvDataService;
    private final PredictionTaskMapper taskMapper;
    private final PredictionInputSnapshotMapper inputMapper;
    private final PredictionResultMapper resultMapper;
    private final SysUserMapper userMapper;

    public PredictionService(ModelServiceClient modelServiceClient, ModelService modelService,
                             PvDataService pvDataService, PredictionTaskMapper taskMapper,
                             PredictionInputSnapshotMapper inputMapper, PredictionResultMapper resultMapper,
                             SysUserMapper userMapper) {
        this.modelServiceClient = modelServiceClient;
        this.modelService = modelService;
        this.pvDataService = pvDataService;
        this.taskMapper = taskMapper;
        this.inputMapper = inputMapper;
        this.resultMapper = resultMapper;
        this.userMapper = userMapper;
    }

    public PredictionTaskVO create(PredictionRequest request) {
        ModelInfo model = modelService.detail(request.modelId());
        List<ModelPredictRequest.InputFrame> frames = pvDataService.latestThirty(request.stationId()).stream()
            .map(item -> new ModelPredictRequest.InputFrame(
                item.time(), item.power(), item.temperature(), item.irradiance()))
            .toList();

        PredictionTaskDO task = createRunningTask(request);
        saveInputSnapshots(task.getTaskId(), frames);
        try {
            ModelPredictResponse response = modelServiceClient.predict(
                new ModelPredictRequest(model.modelCode(), frames));
            if (response == null || response.data() == null) {
                throw new BusinessException(502, "模型服务返回空结果");
            }
            saveResults(task.getTaskId(), frames.get(frames.size() - 1).time(), response.data().predictions());
            task.setStatus("SUCCESS");
            task.setCostTimeMs(response.data().costTime());
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return new PredictionTaskVO(task.getTaskId(), task.getStatus(), model.modelCode(),
                response.data().predictions(), response.data().costTime());
        } catch (RuntimeException exception) {
            task.setStatus("FAILED");
            task.setErrorMessage(exception.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            throw exception;
        }
    }

    public PredictionTaskVO task(Long taskId) {
        PredictionTaskDO task = requireTask(taskId);
        ModelInfo model = modelService.detail(task.getModelId());
        return new PredictionTaskVO(taskId, task.getStatus(), model.modelCode(),
            results(taskId), task.getCostTimeMs());
    }

    public List<ModelPredictResponse.Prediction> results(Long taskId) {
        requireTask(taskId);
        return resultMapper.selectList(Wrappers.<PredictionResultDO>lambdaQuery()
                .eq(PredictionResultDO::getTaskId, taskId)
                .orderByAsc(PredictionResultDO::getTimeOffsetMinutes))
            .stream()
            .map(row -> new ModelPredictResponse.Prediction(
                row.getTimeOffsetMinutes(), row.getPredictPowerKw().doubleValue()))
            .toList();
    }

    public List<PredictionTaskVO> history() {
        return taskMapper.selectList(Wrappers.<PredictionTaskDO>lambdaQuery()
                .orderByDesc(PredictionTaskDO::getCreatedAt))
            .stream().map(task -> {
                ModelInfo model = modelService.detail(task.getModelId());
                return new PredictionTaskVO(task.getTaskId(), task.getStatus(), model.modelCode(),
                    List.of(), task.getCostTimeMs());
            }).toList();
    }

    private PredictionTaskDO createRunningTask(PredictionRequest request) {
        SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getStatus, 1).orderByAsc(SysUserDO::getUserId).last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(400, "请先注册用户后再创建预测任务");
        }
        PredictionTaskDO task = new PredictionTaskDO();
        task.setTaskNo("PRED-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        task.setUserId(user.getUserId());
        task.setStationId(request.stationId());
        task.setModelId(request.modelId());
        task.setInputMode(request.inputMode() == null ? "STATION_HISTORY" : request.inputMode());
        task.setInputStartTime(parse(request.inputStartTime()));
        task.setInputEndTime(parse(request.inputEndTime()));
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    private void saveInputSnapshots(Long taskId, List<ModelPredictRequest.InputFrame> frames) {
        for (ModelPredictRequest.InputFrame frame : frames) {
            PredictionInputSnapshotDO row = new PredictionInputSnapshotDO();
            row.setTaskId(taskId);
            row.setPointTime(LocalDateTime.parse(frame.time(), FORMATTER));
            row.setPowerKw(BigDecimal.valueOf(frame.power()));
            row.setTemperatureC(BigDecimal.valueOf(frame.temperature()));
            row.setIrradianceWM2(BigDecimal.valueOf(frame.irradiance()));
            inputMapper.insert(row);
        }
    }

    private void saveResults(Long taskId, String lastInputTime,
                             List<ModelPredictResponse.Prediction> predictions) {
        LocalDateTime baseTime = LocalDateTime.parse(lastInputTime, FORMATTER);
        for (ModelPredictResponse.Prediction prediction : predictions) {
            PredictionResultDO row = new PredictionResultDO();
            row.setTaskId(taskId);
            row.setTimeOffsetMinutes(prediction.timeOffset());
            row.setPredictTime(baseTime.plusMinutes(prediction.timeOffset()));
            row.setPredictPowerKw(BigDecimal.valueOf(prediction.predictPower()));
            resultMapper.insert(row);
        }
    }

    private PredictionTaskDO requireTask(Long taskId) {
        PredictionTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "预测任务不存在");
        }
        return task;
    }

    private LocalDateTime parse(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value, FORMATTER);
    }
}
