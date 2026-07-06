package com.example.pvplatform.common;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.config.FaceProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

/**
 * Uploads images to Aliyun OSS private bucket and generates temporary signed URLs.
 * Credentials are read from FaceProperties (env placeholders — no real values committed).
 */
@Service
public class AliyunOssService {
    private final FaceProperties props;

    public AliyunOssService(FaceProperties props) {
        this.props = props;
    }

    private OSS buildClient() {
        FaceProperties.Aliyun a = props.aliyun();
        if (a.accessKeyId() == null || a.accessKeyId().isBlank()) {
            throw new BusinessException(500, "阿里云 OSS 凭据未配置（ALIYUN_ACCESS_KEY_ID 为空）");
        }
        return new OSSClientBuilder().build(
            "https://" + a.ossEndpoint(),
            a.accessKeyId(),
            a.accessKeySecret());
    }

    /**
     * Upload image bytes to OSS and return the object key.
     */
    public String upload(byte[] bytes, String extension, String subDir) {
        String ext = (extension != null && !extension.isBlank()) ? extension : "jpg";
        String key = (subDir != null ? subDir + "/" : "") + UUID.randomUUID() + "." + ext;
        OSS client = buildClient();
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            client.putObject(new PutObjectRequest(props.aliyun().ossBucket(), key, in));
        } catch (Exception e) {
            throw new BusinessException(500, "OSS 上传失败: " + e.getMessage());
        } finally {
            client.shutdown();
        }
        return key;
    }

    /**
     * Generate a temporary signed URL (private bucket访问用).
     */
    public String generatePresignedUrl(String key, int expireSeconds) {
        OSS client = buildClient();
        try {
            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
            URL url = client.generatePresignedUrl(props.aliyun().ossBucket(), key, expiration);
            return url.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "OSS 签名URL生成失败");
        } finally {
            client.shutdown();
        }
    }

    /**
     * Delete an object from OSS.
     */
    public void delete(String key) {
        OSS client = buildClient();
        try {
            client.deleteObject(props.aliyun().ossBucket(), key);
        } finally {
            client.shutdown();
        }
    }
}
