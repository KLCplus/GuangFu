package com.example.pvplatform.module.cloud.service;

import com.example.pvplatform.client.dto.CloudPredictRequest;
import com.example.pvplatform.client.vo.CloudPredictResponse;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.cloud.dto.CloudForecastRequest;
import com.example.pvplatform.module.cloud.vo.CloudForecastFrameVO;
import com.example.pvplatform.module.cloud.vo.CloudForecastVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.util.List;

@Service
public class CloudForecastService {
    private static final Logger log = LoggerFactory.getLogger(CloudForecastService.class);
    private final WebClient webClient;

    public CloudForecastService(WebClient modelServiceWebClient) {
        this.webClient = modelServiceWebClient;
    }

    public CloudForecastVO predict(CloudForecastRequest request) {
        CloudPredictResponse response;
        try {
            response = webClient.post()
                .uri("/cloud-api/predict")
                .bodyValue(new CloudPredictRequest(request.modelName(), request.inputImages()))
                .retrieve()
                .bodyToMono(CloudPredictResponse.class)
                .block();
        } catch (WebClientRequestException exception) {
            if (exception.getCause() instanceof ConnectException) {
                throw new BusinessException(502, "云图模型服务不可用");
            }
            log.error("云图模型服务请求失败", exception);
            throw new BusinessException(502, "云图模型服务请求失败");
        } catch (WebClientResponseException exception) {
            log.error("云图模型服务 HTTP {}", exception.getStatusCode().value(), exception);
            throw new BusinessException(exception.getStatusCode().is4xxClientError() ? 400 : 502,
                exception.getStatusCode().is4xxClientError() ? "云图输入不合法" : "云图模型服务执行失败");
        } catch (Exception exception) {
            log.error("云图模型服务响应异常", exception);
            throw new BusinessException(502, "云图模型服务响应异常");
        }

        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException(502, "云图模型服务返回异常");
        }
        List<CloudForecastFrameVO> frames = response.data().predictions().stream()
            .map(item -> new CloudForecastFrameVO(
                item.frameIndex(),
                (item.frameIndex() + 1) * 5,
                normalizeImage(item.image()),
                Math.max(84D, 96D - item.frameIndex() * 1.2D),
                null
            ))
            .toList();
        return new CloudForecastVO(response.data().modelName(), frames, response.data().costTime());
    }

    private String normalizeImage(String image) {
        if (image == null || image.startsWith("data:image")) {
            return image;
        }
        return "data:image/png;base64," + image;
    }
}
