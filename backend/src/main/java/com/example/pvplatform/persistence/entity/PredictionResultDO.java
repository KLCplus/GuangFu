package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("prediction_result")
public class PredictionResultDO {
    @TableId(type = IdType.AUTO)
    private Long resultId;
    private Long taskId;
    private Integer timeOffsetMinutes;
    private LocalDateTime predictTime;
    private BigDecimal predictPowerKw;
    private BigDecimal actualPowerKw;
    private BigDecimal errorValue;
    private BigDecimal errorRate;
    private LocalDateTime createdAt;
}
