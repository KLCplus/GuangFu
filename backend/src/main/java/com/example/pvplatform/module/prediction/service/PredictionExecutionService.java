package com.example.pvplatform.module.prediction.service;

import com.example.pvplatform.client.ModelServiceClient;
import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.prediction.converter.ModelRequestConverter;
import com.example.pvplatform.module.prediction.dto.ModelInputFrame;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PredictionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionExecutionService.class);

    private final ModelServiceClient modelServiceClient;

    public PredictionExecutionService(ModelServiceClient modelServiceClient) {
        this.modelServiceClient = modelServiceClient;
    }

    /**
     * 执行预测调用（无数据库事务）。
     * 返回响应数据，异常时抛出 BusinessException。
     */
    public ModelPredictResponse.Data execute(ModelInfoDO model, List<ModelInputFrame> frames) {
        ModelPredictRequest request = ModelRequestConverter.toPredictRequest(
                model.getServiceModelName(), frames);

        return execute(model, request);
    }

    /**
     * 执行已组装好的模型请求，用于开放 API 透传云图字段。
     */
    public ModelPredictResponse.Data execute(ModelInfoDO model, ModelPredictRequest request) {
        long startMs = System.currentTimeMillis();

        ModelPredictResponse response = modelServiceClient.predict(
                request, model.getServiceModelName(), model.getOutputSteps());

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info("模型调用完成: taskModel={}, httpCostMs={}, modelCostMs={}",
                model.getServiceModelName(), elapsedMs, response.data().costTime());

        return response.data();
    }

    /**
     * 获取输入最后一帧时间，用于计算预测时间
     */
    public LocalDateTime getLastInputTime(List<ModelInputFrame> frames) {
        return frames.get(frames.size() - 1).time();
    }
}
