package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_resource")
public class FileResourceDO {
    @TableId(type = IdType.AUTO)
    private Long fileId;
    private Long ownerUserId;
    private String originalName;
    private String storageName;
    private String storagePath;
    private String fileUrl;
    private String fileType;
    private String businessType;
    private Long fileSize;
    private String checksum;
    private String objectKey;
    private String contentType;
    private String fileStatus;
    private Long bizId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
