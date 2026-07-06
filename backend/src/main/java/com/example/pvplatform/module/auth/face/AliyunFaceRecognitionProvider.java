package com.example.pvplatform.module.auth.face;

import com.example.pvplatform.common.AliyunOssService;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.config.FaceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@ConditionalOnProperty(name = "face.provider", havingValue = "aliyun")
public class AliyunFaceRecognitionProvider implements FaceRecognitionProvider {
    private static final Logger log = LoggerFactory.getLogger(AliyunFaceRecognitionProvider.class);
    private static final DateTimeFormatter ISO8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final FaceProperties props;
    private final AliyunOssService ossService;
    private final WebClient.Builder webClientBuilder;

    private volatile boolean faceDbReady = false;

    public AliyunFaceRecognitionProvider(FaceProperties props, AliyunOssService ossService,
                                          WebClient.Builder webClientBuilder) {
        this.props = props;
        this.ossService = ossService;
        this.webClientBuilder = webClientBuilder;
        // 懒加载：不在构造函数里连阿里云，等第一次调用时再初始化
    }

    @Override public String extract(MultipartFile image) { throw new UnsupportedOperationException("use enrollFace/searchFace"); }
    @Override public boolean match(String a, String b) { throw new UnsupportedOperationException("use searchFace"); }

    @Override
    public EnrollResult enrollFace(MultipartFile image, String entityId) {
        ensureFaceDb();
        byte[] bytes = readBytes(image);
        String ossKey = ossService.upload(bytes, "jpg", "face-enroll");
        String imageUrl = ossService.generatePresignedUrl(ossKey, props.tempUrlExpireSeconds());

        try {
            createEntityIfNeeded(entityId);
            String faceId = addFace(entityId, imageUrl);
            return new EnrollResult(entityId, faceId);
        } finally {
            ossService.delete(ossKey);
        }
    }

    @Override
    public SearchResult searchFace(MultipartFile image) {
        byte[] bytes = readBytes(image);
        String ossKey = ossService.upload(bytes, "jpg", "face-login");
        String imageUrl = ossService.generatePresignedUrl(ossKey, props.tempUrlExpireSeconds());

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("DbName", props.aliyun().faceDbName());
            params.put("ImageUrl", imageUrl);
            params.put("Limit", "1");
            params.put("MaxFaceNum", "1");
            Map<String, Object> result = callFaceApi("SearchFace", params);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matchList = (List<Map<String, Object>>)
                ((Map<String, Object>) result.get("Data")).get("MatchList");
            if (matchList == null || matchList.isEmpty()) {
                throw new BusinessException(401, "人脸识别失败");
            }
            Map<String, Object> first = matchList.get(0);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) first.get("FaceItems");
            if (items == null || items.isEmpty()) {
                throw new BusinessException(401, "人脸识别失败");
            }
            Map<String, Object> item = items.get(0);
            Object confidenceValue = item.get("Confidence");
            if (!(confidenceValue instanceof Number confidenceNumber)) {
                throw new BusinessException(502, "人脸服务未返回置信度");
            }
            double confidence = confidenceNumber.doubleValue();
            if (confidence < props.matchThreshold()) {
                throw new BusinessException(401,
                    "人脸识别相似度不足: " + String.format(Locale.ROOT, "%.2f", confidence));
            }
            String entityId = (String) item.get("EntityId");
            String faceId = (String) item.get("FaceId");
            return new SearchResult(entityId, faceId, confidence);
        } finally {
            ossService.delete(ossKey);
        }
    }

    @Override public String name() { return "Aliyun"; }

    // ---- internal ----

    private synchronized void ensureFaceDb() {
        if (faceDbReady) return;
        try {
            Map<String, Object> response = callFaceApi("ListFaceDbs", Map.of());
            boolean exists = false;
            Object dataValue = response.get("Data");
            if (dataValue instanceof Map<?, ?> data) {
                Object dbListValue = data.get("DbList");
                if (dbListValue instanceof List<?> dbList) {
                    exists = dbList.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .anyMatch(db -> props.aliyun().faceDbName()
                            .equals(Objects.toString(db.get("Name"), "")));
                }
            }
            if (!exists) {
                callFaceApi("CreateFaceDb", Map.of("Name", props.aliyun().faceDbName()));
                log.info("阿里云人脸库已创建: {}", props.aliyun().faceDbName());
            }
            faceDbReady = true;
        } catch (Exception e) {
            log.warn("阿里云人脸库初始化失败: {}", e.getMessage());
            throw e;
        }
    }

    private void createEntityIfNeeded(String entityId) {
        try {
            Map<String, Object> response = callFaceApi("GetFaceEntity", Map.of(
                "DbName", props.aliyun().faceDbName(),
                "EntityId", entityId
            ));
            Object dataValue = response.get("Data");
            if (dataValue instanceof Map<?, ?> data
                && entityId.equals(Objects.toString(data.get("EntityId"), ""))) {
                return;
            }
        } catch (BusinessException notFound) {
            // A missing entity is returned as either an empty success response or a 4xx,
            // depending on the service version. Both cases continue to AddFaceEntity.
        }
        callFaceApi("AddFaceEntity", Map.of(
            "DbName", props.aliyun().faceDbName(),
            "EntityId", entityId
        ));
    }

    private String addFace(String entityId, String imageUrl) {
        Map<String, Object> resp = callFaceApi("AddFace", Map.of(
            "DbName", props.aliyun().faceDbName(),
            "EntityId", entityId,
            "ImageUrl", imageUrl
        ));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.get("Data");
        return data != null ? (String) data.get("FaceId") : "";
    }

    /**
     * Call Aliyun FaceBody Open API with HMAC-SHA1 v1 signing.
     */
    private Map<String, Object> callFaceApi(String action, Map<String, String> customParams) {
        FaceProperties.Aliyun cfg = props.aliyun();
        TreeMap<String, String> allParams = new TreeMap<>(customParams);
        allParams.put("Action", action);
        allParams.put("Version", "2019-12-30");
        allParams.put("Format", "JSON");
        allParams.put("RegionId", cfg.faceRegion());
        allParams.put("Timestamp", Instant.now().atZone(java.time.ZoneOffset.UTC).format(ISO8601));
        allParams.put("SignatureMethod", "HMAC-SHA1");
        allParams.put("SignatureVersion", "1.0");
        allParams.put("SignatureNonce", UUID.randomUUID().toString());
        allParams.put("AccessKeyId", cfg.accessKeyId());

        String signature = sign(allParams, cfg);
        allParams.put("Signature", signature);

        // Build query string
        StringBuilder qs = new StringBuilder();
        allParams.forEach((k, v) -> qs.append("&").append(encode(k)).append("=").append(encode(v)));
        String queryString = qs.substring(1);

        String url = "https://" + cfg.faceEndpoint() + "/?" + queryString;
        log.info("Face API {} called", action);

        Map<String, Object> resp = webClientBuilder.build()
            // The query string is already RFC 3986 encoded for the RPC signature.
            // Passing it as a String makes WebClient encode '%' again (%3A -> %253A),
            // which corrupts Timestamp and Signature.
            .get().uri(URI.create(url))
            .retrieve()
            .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                r -> r.bodyToMono(String.class)
                    .map(msg -> new BusinessException(502, "阿里云人脸服务错误: " + msg)))
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        if (resp == null) throw new BusinessException(502, "阿里云人脸服务无响应");

        // Check for errors
        if (resp.containsKey("Code") && !"200".equals(String.valueOf(resp.get("Code")))) {
            String msg = Objects.toString(resp.get("Message"), "未知错误");
            throw new BusinessException(502, "人脸服务错误: " + msg);
        }
        return resp;
    }

    private String sign(Map<String, String> params, FaceProperties.Aliyun cfg) {
        StringBuilder canonical = new StringBuilder();
        params.forEach((k, v) -> canonical.append("&").append(encode(k)).append("=").append(encode(v)));
        String toSign = "GET&" + encode("/") + "&" + encode(canonical.substring(1));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec((cfg.accessKeySecret() + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("签名生成失败", e);
        }
    }

    private String encode(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("*", "%2A").replace("%7E", "~"); }
        catch (Exception e) { return s; }
    }

    private byte[] readBytes(MultipartFile image) {
        try { return image.getBytes(); } catch (IOException e) {
            throw new BusinessException(400, "读取图片失败");
        }
    }
}
