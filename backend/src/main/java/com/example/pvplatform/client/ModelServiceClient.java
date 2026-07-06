package com.example.pvplatform.client;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@Component
public class ModelServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ModelServiceClient.class);

    private final WebClient webClient;

    public ModelServiceClient(WebClient modelServiceWebClient) {
        this.webClient = modelServiceWebClient;
    }

    public ModelPredictResponse predict(ModelPredictRequest request, String expectedModelName, int expectedOutputSteps) {
        ModelPredictResponse response;
        try {
            response = webClient.post()
                    .uri("/model-api/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ModelPredictResponse.class)
                    .block();
        } catch (WebClientRequestException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException) {
                log.error("模型服务连接失败: model={}", request.modelName(), e);
                throw new BusinessException(502, "模型服务不可用");
            }
            if (cause instanceof TimeoutException || e.getMessage().contains("timeout")) {
                log.error("模型服务调用超时: model={}", request.modelName(), e);
                throw new BusinessException(502, "模型预测超时");
            }
            log.error("模型服务请求失败: model={}", request.modelName(), e);
            throw new BusinessException(502, "模型服务不可用");
        } catch (WebClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            log.error("模型服务 HTTP {}: model={}", statusCode, request.modelName(), e);
            if (statusCode >= 400 && statusCode < 500) {
                throw new BusinessException(400, "模型输入不合法或模型不存在");
            }
            throw new BusinessException(502, "模型服务执行失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("模型服务调用异常: model={}", request.modelName(), e);
            throw new BusinessException(502, "模型服务响应异常");
        }

        // 响应校验
        validateResponse(response, expectedModelName, expectedOutputSteps);
        return response;
    }

    private void validateResponse(ModelPredictResponse response, String expectedModelName, int expectedOutputSteps) {
        if (response == null || response.data() == null) {
            throw new BusinessException(502, "模型服务返回空结果");
        }
        if (response.code() != 200) {
            throw new BusinessException(502, "模型服务业务错误: " + response.message());
        }
        ModelPredictResponse.Data data = response.data();

        // modelName 匹配
        if (!expectedModelName.equals(data.modelName())) {
            log.warn("模型名称不匹配: 期望={}, 实际={}", expectedModelName, data.modelName());
            throw new BusinessException(502, "模型服务响应模型名不匹配");
        }

        List<ModelPredictResponse.Prediction> predictions = data.predictions();
        // 预测数量 = outputSteps
        if (predictions == null || predictions.size() != expectedOutputSteps) {
            log.warn("预测结果数量不匹配: 期望={}, 实际={}", expectedOutputSteps,
                    predictions == null ? 0 : predictions.size());
            throw new BusinessException(502, "模型预测结果数量不正确");
        }

        // 校验每个预测
        Set<Integer> offsets = new HashSet<>();
        for (ModelPredictResponse.Prediction p : predictions) {
            // timeOffset 正确 (5/10/15/20/25/30)
            if (p.timeOffset() < 5 || p.timeOffset() > 30 || p.timeOffset() % 5 != 0) {
                throw new BusinessException(502, "模型预测时间偏移异常: " + p.timeOffset());
            }
            // predictPower 为有限非负数
            if (Double.isNaN(p.predictPower()) || Double.isInfinite(p.predictPower())) {
                throw new BusinessException(502, "模型预测功率值非法");
            }
            if (p.predictPower() < 0) {
                throw new BusinessException(502, "模型预测功率不能为负");
            }
            // 检查重复 offset
            if (!offsets.add(p.timeOffset())) {
                throw new BusinessException(502, "模型预测存在重复时间偏移: " + p.timeOffset());
            }
        }

        // costTime 非负
        if (data.costTime() < 0) {
            log.warn("costTime 为负值: {}", data.costTime());
            throw new BusinessException(502, "模型服务耗时异常");
        }
    }
}
