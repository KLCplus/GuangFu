package com.example.pvplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oss")
public record OssProperties(
    boolean enabled,
    String credentialMode,
    String endpoint,
    String region,
    String bucketName,
    String accessKeyId,
    String accessKeySecret,
    String ramRoleName,
    int urlExpireSeconds,
    int avatarMaxSizeMb,
    int newsImageMaxSizeMb
) {}
