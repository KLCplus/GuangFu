package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_call_log")
public class ApiCallLogDO {
    @TableId(type = IdType.AUTO)
    private Long logId;
    private Long userId;
    private Long apiKeyId;
    private Long modelId;
    private String requestPath;
    private String requestMethod;
    private String requestIp;
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private Long costTimeMs;
    private Integer httpStatus;
    private String bizStatus;
    private String errorMessage;
    private String requestSummary;
    private String responseSummary;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
}
