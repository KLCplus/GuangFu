package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_face_auth")
public class SysFaceAuthDO {
    @TableId(type = IdType.AUTO)
    private Long faceId;
    private Long userId;
    private String provider;
    private String faceDbName;
    private String entityId;
    private String faceFeatureId;
    private String faceImageUrl;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
