package com.example.pvplatform.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_call")
public class AgentToolCallDO {
    @TableId(type = IdType.AUTO)
    private Long toolCallId;
    private Long sessionId;
    private Long messageId;
    private String clientToolCallId;
    private String toolName;
    private String argumentsJson;
    private String resultJson;
    private String status;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
