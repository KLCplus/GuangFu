package com.example.pvplatform.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_approval")
public class AgentApprovalDO {
    @TableId(type = IdType.AUTO)
    private Long approvalId;
    private Long sessionId;
    private Long toolCallId;
    private Long userId;
    private String toolName;
    private String status;
    private String reason;
    private String argumentsJson;
    private String comment;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}
