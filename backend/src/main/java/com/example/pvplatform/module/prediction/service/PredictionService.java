package com.example.pvplatform.module.prediction.service;

import com.example.pvplatform.client.ModelServiceClient;
import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.module.model.entity.ModelInfo;
import com.example.pvplatform.module.model.service.ModelService;
import com.example.pvplatform.module.prediction.dto.PredictionRequest;
import com.example.pvplatform.module.prediction.entity.PredictionTask;
import com.example.pvplatform.module.prediction.vo.PredictionTaskVO;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PredictionService {
    private final ModelServiceClient modelServiceClient;
    private final ModelService modelService;
    private final PvDataService pvDataService;
    private final AtomicLong taskIds = new AtomicLong(1000);
    private final Map<Long, PredictionTask> tasks = new ConcurrentHashMap<>();

    public PredictionService(ModelServiceClient modelServiceClient, ModelService modelService, PvDataService pvDataService) {
        this.modelServiceClient = modelServiceClient;
        this.modelService = modelService;
        this.pvDataService = pvDataService;
    }

    public PredictionTaskVO create(PredictionRequest request) {
        long taskId = taskIds.incrementAndGet();
        ModelInfo model = modelService.detail(request.modelId());
        tasks.put(taskId, new PredictionTask(taskId, request.stationId(), request.modelId(), model.modelCode(),
            PredictionTask.TaskStatus.RUNNING, LocalDateTime.now(), List.of(), null));

        // 骨架阶段以 mock 历史数据模拟从数据库读取过去 30 分钟输入。
        List<ModelPredictRequest.InputFrame> frames = pvDataService.history().stream()
            .map(item -> new ModelPredictRequest.InputFrame(
                item.time(), item.power(), item.temperature(), item.irradiance()))
            .toList();

        ModelPredictResponse response = modelServiceClient.predict(new ModelPredictRequest(model.modelCode(), frames));
        PredictionTask completed = new PredictionTask(taskId, request.stationId(), request.modelId(), model.modelCode(),
            PredictionTask.TaskStatus.SUCCESS, LocalDateTime.now(), response.data().predictions(), response.data().costTime());
        tasks.put(taskId, completed);
        return toVO(completed);
    }

    public PredictionTaskVO task(Long taskId) {
        return toVO(tasks.getOrDefault(taskId, sample(taskId)));
    }

    public List<ModelPredictResponse.Prediction> results(Long taskId) {
        return tasks.getOrDefault(taskId, sample(taskId)).predictions();
    }

    public List<PredictionTaskVO> history() {
        if (tasks.isEmpty()) return List.of(toVO(sample(1000L)));
        return tasks.values().stream().map(this::toVO).toList();
    }

    private PredictionTaskVO toVO(PredictionTask task) {
        return new PredictionTaskVO(task.taskId(), task.taskStatus().name(), task.modelName(),
            task.predictions(), task.costTime());
    }

    private PredictionTask sample(Long taskId) {
        return new PredictionTask(taskId, 1L, 1L, "lstm_v1", PredictionTask.TaskStatus.SUCCESS,
            LocalDateTime.now(), List.of(
                new ModelPredictResponse.Prediction(5, 530.2),
                new ModelPredictResponse.Prediction(10, 535.6),
                new ModelPredictResponse.Prediction(15, 540.1),
                new ModelPredictResponse.Prediction(20, 542.4),
                new ModelPredictResponse.Prediction(25, 545.0),
                new ModelPredictResponse.Prediction(30, 548.3)
            ), 120L);
    }
}
