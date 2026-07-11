package com.example.pvplatform.module.cloud.vo;

import java.util.List;

public record CloudForecastVO(
    String modelName,
    List<CloudForecastFrameVO> predictions,
    Integer costTime
) {}
