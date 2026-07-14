package com.example.pvplatform.module.agent.runtime.alibaba.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_run_event")
public class AgentRunEventDO {
    @TableId(type = IdType.AUTO)
    private Long eventId;
    private String runId;
    private Long sessionId;
    private Long messageId;
    private Long userId;
    private Integer sequenceNo;
    private String eventType;
    private String nodeName;
    private String toolName;
    private Long toolCallId;
    private String payloadJson;
    private String eventStatus;
    private LocalDateTime createdAt;
}
