package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_info")
public class ModelInfoDO {
    @TableId(type = IdType.AUTO)
    private Long modelId;
    private String modelCode;
    private String modelName;
    private String modelType;
    private String modelVersion;
    private Integer inputWindowMinutes;
    private Integer inputFrameIntervalSeconds;
    private Integer outputSteps;
    private Integer outputStepMinutes;
    private String serviceModelName;
    private String apiPath;
    private String inputSchema;
    private String outputSchema;
    private String status;
    private String description;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
