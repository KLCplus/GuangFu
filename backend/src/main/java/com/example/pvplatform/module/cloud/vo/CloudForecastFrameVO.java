package com.example.pvplatform.module.cloud.vo;

public record CloudForecastFrameVO(
    Integer frameIndex,
    Integer timeOffset,
    String image,
    Double confidence,
    Double cloudCoverage
) {}
