package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prediction_task")
public class PredictionTaskDO {
    @TableId(type = IdType.AUTO)
    private Long taskId;
    private String taskNo;
    private Long userId;
    private Long stationId;
    private Long modelId;
    private String inputMode;
    private LocalDateTime inputStartTime;
    private LocalDateTime inputEndTime;
    private LocalDateTime predictStartTime;
    private LocalDateTime predictEndTime;
    private String status;
    private String requestParams;
    private String errorMessage;
    private Long costTimeMs;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
