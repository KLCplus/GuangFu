package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_key")
public class ApiKeyDO {
    @TableId(type = IdType.AUTO)
    private Long apiKeyId;
    private Long userId;
    private String keyName;
    private String apiKeyPrefix;
    private String apiKeyHash;
    private String status;
    private Integer rateLimitPerMinute;
    private Integer dailyQuota;
    private LocalDateTime expireTime;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
