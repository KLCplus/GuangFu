package com.example.pvplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "face")
public record FaceProperties(
    String provider,
    double matchThreshold,
    int tempUrlExpireSeconds,
    String storageDir,
    Aliyun aliyun
) {
    public record Aliyun(
        String accessKeyId,
        String accessKeySecret,
        String faceRegion,
        String faceEndpoint,
        String faceDbName,
        String ossRegion,
        String ossEndpoint,
        String ossBucket
    ) {}
}
