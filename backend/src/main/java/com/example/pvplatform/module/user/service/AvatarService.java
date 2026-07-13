package com.example.pvplatform.module.user.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.config.OssProperties;
import com.example.pvplatform.infrastructure.oss.OssObjectKeyGenerator;
import com.example.pvplatform.infrastructure.oss.OssStorageService;
import com.example.pvplatform.module.file.service.ImageUploadValidator;
import com.example.pvplatform.persistence.entity.FileResourceDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.FileResourceMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AvatarService {
    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);
    private final FileResourceMapper files; private final SysUserMapper users; private final OssStorageService oss;
    private final OssObjectKeyGenerator keys; private final OssProperties properties;
    public AvatarService(FileResourceMapper files, SysUserMapper users, OssStorageService oss, OssObjectKeyGenerator keys, OssProperties properties) { this.files=files; this.users=users; this.oss=oss; this.keys=keys; this.properties=properties; }

    @Transactional
    public AvatarResult upload(MultipartFile file, Long userId) {
        var image = ImageUploadValidator.validate(file, properties.avatarMaxSizeMb() * 1024L * 1024L);
        String key = keys.avatar(userId, image.extension());
        try {
            oss.upload(new ByteArrayInputStream(image.bytes()), key, image.contentType(), image.bytes().length);
            SysUserDO user = users.selectById(userId);
            if (user == null) throw new BusinessException(404, "用户不存在");
            FileResourceDO resource = resource(userId, image, key, "AVATAR", userId);
            files.insert(resource);
            Long oldFileId = user.getAvatarFileId();
            user.setAvatarFileId(resource.getFileId()); user.setAvatarUrl(viewUrl(resource.getFileId())); users.updateById(user);
            if (oldFileId != null) afterCommitDelete(oldFileId);
            return new AvatarResult(resource.getFileId(), viewUrl(resource.getFileId()));
        } catch (RuntimeException e) {
            try { oss.delete(key); } catch (Exception cleanup) { log.error("avatar rollback OSS cleanup failed userId={} objectKey={}", userId, key); }
            throw e;
        }
    }
    @Transactional
    public void delete(Long userId) {
        SysUserDO user=users.selectById(userId); if(user==null) throw new BusinessException(404,"用户不存在");
        Long old=user.getAvatarFileId(); user.setAvatarFileId(null); user.setAvatarUrl(null); users.updateById(user); if(old!=null) afterCommitDelete(old);
    }
    private FileResourceDO resource(Long owner, ImageUploadValidator.ValidImage image, String key, String type, Long bizId) {
        try { FileResourceDO f=new FileResourceDO(); f.setOwnerUserId(owner); f.setOriginalName(image.originalName()); f.setStorageName(key.substring(key.lastIndexOf('/')+1)); f.setStoragePath(key); f.setObjectKey(key); f.setContentType(image.contentType()); f.setFileType(image.extension()); f.setBusinessType(type); f.setBizId(bizId); f.setFileStatus("BOUND"); f.setFileSize((long)image.bytes().length); f.setChecksum(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.bytes()))); f.setCreatedAt(LocalDateTime.now()); f.setUpdatedAt(LocalDateTime.now()); return f; } catch(Exception e) { throw new BusinessException(500,"文件校验失败"); }
    }
    private void afterCommitDelete(Long fileId) { TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit(){ cleanup(fileId); } }); }
    private void cleanup(Long fileId) { FileResourceDO old=files.selectById(fileId); if(old==null || "DELETED".equals(old.getFileStatus()))return; try { oss.delete(old.getObjectKey()); old.setFileStatus("DELETED"); files.updateById(old); } catch(Exception e){ log.error("avatar cleanup pending fileId={} objectKey={}",fileId,old.getObjectKey()); } }
    private String viewUrl(Long id) { return "/api/files/"+id+"/view"; }
    public record AvatarResult(Long fileId, String avatarUrl) {}
}
