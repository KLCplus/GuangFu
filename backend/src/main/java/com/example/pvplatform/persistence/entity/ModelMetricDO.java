package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("model_metric")
public class ModelMetricDO {
    @TableId(type = IdType.AUTO)
    private Long metricId;
    private Long modelId;
    private String datasetName;
    private BigDecimal mae;
    private BigDecimal rmse;
    private BigDecimal mape;
    private BigDecimal r2Score;
    private String metricJson;
    private LocalDateTime evaluatedAt;
    private LocalDateTime createdAt;
}
