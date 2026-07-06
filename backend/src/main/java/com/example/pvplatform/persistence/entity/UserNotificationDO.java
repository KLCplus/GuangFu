package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notification")
public class UserNotificationDO {
    @TableId(type = IdType.AUTO)
    private Long notificationId;
    private Long userId;
    private String title;
    private String content;
    private String notificationType;
    private String relatedType;
    private Long relatedId;
    private Integer readStatus;
    private LocalDateTime readTime;
    private LocalDateTime createdAt;
}
