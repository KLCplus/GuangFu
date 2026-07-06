package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("prediction_input_snapshot")
public class PredictionInputSnapshotDO {
    @TableId(type = IdType.AUTO)
    private Long inputId;
    private Long taskId;
    private LocalDateTime pointTime;
    private BigDecimal powerKw;
    private BigDecimal temperatureC;
    @TableField("irradiance_w_m2")
    private BigDecimal irradianceWM2;
    private BigDecimal humidityPercent;
    @TableField("wind_speed_m_s")
    private BigDecimal windSpeedMS;
    private String rawData;
    private LocalDateTime createdAt;
}
