package com.example.pvplatform.module.auth.face;

import org.springframework.web.multipart.MultipartFile;

/**
 * Pluggable face recognition provider.
 */
public interface FaceRecognitionProvider {

    /**
     * Extract a face feature hash. Used by LocalMockFaceProvider.
     */
    String extract(MultipartFile image);

    /**
     * Compare two features. Used by LocalMockFaceProvider.
     */
    boolean match(String featureA, String featureB);

    /**
     * Enroll a face image for the given entity ID. Returns entityId + faceId.
     * Default delegates to extract() for mock compatibility.
     */
    default EnrollResult enrollFace(MultipartFile image, String entityId) {
        String f = extract(image);
        return new EnrollResult(entityId, f);
    }

    /**
     * Search for a matching face. Returns entityId, faceId, score.
     * Default throws — only cloud providers implement this.
     */
    default SearchResult searchFace(MultipartFile image) {
        throw new UnsupportedOperationException("searchFace not supported by " + name());
    }

    String name();

    record EnrollResult(String entityId, String faceId) {}
    record SearchResult(String entityId, String faceId, double score) {}
}
