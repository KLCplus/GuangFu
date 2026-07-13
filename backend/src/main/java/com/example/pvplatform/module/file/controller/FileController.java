package com.example.pvplatform.module.file.controller;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.config.OssProperties;
import com.example.pvplatform.infrastructure.oss.OssStorageService;
import com.example.pvplatform.persistence.entity.FileResourceDO;
import com.example.pvplatform.persistence.entity.NewsDO;
import com.example.pvplatform.persistence.mapper.FileResourceMapper;
import com.example.pvplatform.persistence.mapper.NewsMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.net.URI;
import java.time.Duration;

@RestController
public class FileController {
    private final FileResourceMapper files; private final NewsMapper news; private final OssStorageService oss; private final OssProperties props;
    public FileController(FileResourceMapper files, NewsMapper news, OssStorageService oss, OssProperties props) { this.files=files;this.news=news;this.oss=oss;this.props=props; }
    @GetMapping("/api/files/{fileId}/view")
    public ResponseEntity<Void> view(@PathVariable Long fileId) {
        FileResourceDO file=files.selectById(fileId);
        if(file==null || "DELETED".equals(file.getFileStatus()) || file.getObjectKey()==null) throw new BusinessException(404,"文件不存在");
        authorize(file);
        String url=oss.generateReadUrl(file.getObjectKey(), Duration.ofSeconds(Math.max(60, props.urlExpireSeconds())), null);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(url).toASCIIString()).build();
    }
    private void authorize(FileResourceDO file) {
        if("FACE_IMAGE".equals(file.getBusinessType())) throw new BusinessException(403,"该文件不可访问");
        if("AVATAR".equals(file.getBusinessType())) return;
        if("NEWS_COVER".equals(file.getBusinessType()) || "NEWS_CONTENT".equals(file.getBusinessType())) {
            NewsDO article=news.selectById(file.getBizId());
            if(article!=null && "PUBLISHED".equals(article.getStatus()) && article.getPublishedAt()!=null && !article.getPublishedAt().isAfter(java.time.LocalDateTime.now())) return;
            var user=SecurityUtils.getCurrentUser();
            if(user!=null && user.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) return;
            throw new BusinessException(403,"新闻图片暂不可访问");
        }
        throw new BusinessException(403,"无文件访问权限");
    }
}
