package com.example.pvplatform.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_memory")
public class AgentMemoryDO {
    @TableId(type = IdType.AUTO)
    private Long memoryId;
    private Long userId;
    private String memoryType;
    private String memoryKey;
    private String valueJson;
    private String sourceMessage;
    private BigDecimal confidence;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
