package com.example.pvplatform.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_message")
public class AgentMessageDO {
    @TableId(type = IdType.AUTO)
    private Long messageId;
    private Long sessionId;
    private Long userId;
    private String role;
    private String content;
    private String metadataJson;
    private LocalDateTime createdAt;
}
