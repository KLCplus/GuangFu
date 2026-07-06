package com.example.pvplatform.module.auth.face;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Mock face recognition provider for development and testing.
 * Uses SHA-256 of image bytes as "face feature" — NOT real recognition.
 * ANY image works; matching is exact byte-for-byte comparison.
 *
 * ⚠️  This is a PLACEHOLDER. Do NOT use in production.
 * A real face SDK + liveness detection is required for any security-sensitive use.
 */
@Component
@ConditionalOnProperty(name = "face.provider", havingValue = "local", matchIfMissing = true)
public class LocalMockFaceProvider implements FaceRecognitionProvider {

    @Override
    public String extract(MultipartFile image) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(image.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("人脸特征提取失败(mock)", e);
        }
    }

    @Override
    public boolean match(String featureA, String featureB) {
        return featureA != null && featureA.equals(featureB);
    }

    @Override
    public String name() {
        return "LocalMock";
    }
}
