package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("news")
public class NewsDO {
    @TableId(type = IdType.AUTO)
    private Long newsId;
    private String title;
    private String summary;
    private String content;
    private String coverUrl;
    private Long coverFileId;
    private String newsType;
    private String category;
    private String contentType;
    private String sourceType;
    private String sourceName;
    private String sourceUrl;
    private String externalId;
    private LocalDateTime sourcePublishedAt;
    private LocalDateTime fetchedAt;
    private Integer externalContent;
    private String warningLevel;
    private String warningRegion;
    private String warningAgency;
    private LocalDateTime effectiveAt;
    private LocalDateTime expiresAt;
    private String targetRole;
    private String status;
    private Long publisherId;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
