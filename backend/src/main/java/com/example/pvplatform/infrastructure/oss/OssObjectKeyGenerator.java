package com.example.pvplatform.infrastructure.oss;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class OssObjectKeyGenerator {
    public String avatar(Long userId, String ext) { return "avatars/" + userId + "/" + UUID.randomUUID() + "." + ext; }
    public String newsCover(Long articleId, String ext) { return "news/" + articleId + "/cover/" + UUID.randomUUID() + "." + ext; }
    public String newsContent(Long articleId, String ext) { return "news/" + articleId + "/content/" + UUID.randomUUID() + "." + ext; }
}
