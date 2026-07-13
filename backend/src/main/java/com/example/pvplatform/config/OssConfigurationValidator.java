package com.example.pvplatform.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Fails fast without ever echoing credential values. */
@Component
public class OssConfigurationValidator implements ApplicationRunner {
    private final OssProperties oss;
    public OssConfigurationValidator(OssProperties oss) { this.oss = oss; }
    @Override public void run(ApplicationArguments args) {
        if (!oss.enabled()) return;
        if (blank(oss.endpoint()) || blank(oss.bucketName())) throw new IllegalStateException("OSS_ENABLED=true requires OSS_ENDPOINT and OSS_BUCKET_NAME");
        if ("ECS_RAM_ROLE".equalsIgnoreCase(oss.credentialMode())) throw new IllegalStateException("OSS_CREDENTIAL_MODE=ECS_RAM_ROLE is reserved but not supported by the current OSS client configuration");
        if (blank(oss.accessKeyId()) || blank(oss.accessKeySecret())) throw new IllegalStateException("OSS_ENABLED=true requires Alibaba Cloud access-key environment variables");
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
