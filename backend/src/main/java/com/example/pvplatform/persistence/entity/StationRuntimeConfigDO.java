package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("station_runtime_config")
public class StationRuntimeConfigDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private BigDecimal batteryCapacityKwh;
    private BigDecimal nominalVoltageV;
    private String phaseType;
    private BigDecimal performanceRatio;
    private BigDecimal powerFactor;
    private BigDecimal baseLoadKw;
    private BigDecimal loadPeakFactor;
    private String stationType;
    private String timezone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
