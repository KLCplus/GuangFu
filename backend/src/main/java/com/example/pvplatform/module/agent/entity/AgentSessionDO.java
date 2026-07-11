package com.example.pvplatform.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_session")
public class AgentSessionDO {
    @TableId(type = IdType.AUTO)
    private Long sessionId;
    private Long userId;
    private String title;
    private Boolean archived;
    private Boolean pinned;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
