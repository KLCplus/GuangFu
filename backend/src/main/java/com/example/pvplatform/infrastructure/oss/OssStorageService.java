package com.example.pvplatform.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.config.OssProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Date;

/** Only performs OSS object operations; authorization and DB binding stay in modules. */
@Service
public class OssStorageService {
    private final OssProperties properties;
    private volatile OSS client;

    public OssStorageService(OssProperties properties) { this.properties = properties; }

    public String upload(InputStream inputStream, String objectKey, String contentType, long fileSize) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(fileSize);
            oss().putObject(new PutObjectRequest(properties.bucketName(), objectKey, inputStream, metadata));
            return objectKey;
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { throw new BusinessException(502, "OSS 上传失败"); }
    }

    public void delete(String objectKey) {
        try { oss().deleteObject(properties.bucketName(), objectKey); }
        catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(502, "OSS 删除失败"); }
    }

    public String generateReadUrl(String objectKey, Duration expiration, String imageProcess) {
        try {
            URL url = oss().generatePresignedUrl(properties.bucketName(), objectKey,
                new Date(System.currentTimeMillis() + expiration.toMillis()));
            return url.toString();
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(502, "OSS 访问签名失败"); }
    }

    private OSS oss() {
        if (!properties.enabled()) throw new BusinessException(503, "OSS 未启用，请设置 OSS_ENABLED=true");
        OSS existing = client;
        if (existing != null) return existing;
        synchronized (this) {
            if (client != null) return client;
            if (blank(properties.endpoint()) || blank(properties.bucketName()))
                throw new BusinessException(500, "OSS 配置缺失：OSS_ENDPOINT 和 OSS_BUCKET_NAME 必填");
            if ("ECS_RAM_ROLE".equalsIgnoreCase(properties.credentialMode()))
                throw new BusinessException(500, "当前 OSS SDK 配置尚未启用 ECS RAM Role，请使用 ACCESS_KEY 模式或补充 RAM 凭据提供器");
            if (blank(properties.accessKeyId()) || blank(properties.accessKeySecret()))
                throw new BusinessException(500, "OSS 配置缺失：ALIBABA_CLOUD_ACCESS_KEY_ID 和 ALIBABA_CLOUD_ACCESS_KEY_SECRET 必填");
            String endpoint = properties.endpoint().startsWith("http://") || properties.endpoint().startsWith("https://")
                ? properties.endpoint() : "https://" + properties.endpoint();
            client = new OSSClientBuilder().build(endpoint, properties.accessKeyId(), properties.accessKeySecret());
            return client;
        }
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    @PreDestroy public void close() { if (client != null) client.shutdown(); }
}
