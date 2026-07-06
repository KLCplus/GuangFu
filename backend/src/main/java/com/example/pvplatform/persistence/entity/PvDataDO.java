package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pv_data")
public class PvDataDO {
    @TableId(type = IdType.AUTO)
    private Long dataId;
    private Long stationId;
    private LocalDateTime collectTime;
    private BigDecimal powerKw;
    private BigDecimal energyTodayKwh;
    private BigDecimal energyTotalKwh;
    private BigDecimal voltageV;
    private BigDecimal currentA;
    @TableField("irradiance_w_m2")
    private BigDecimal irradianceWM2;
    private BigDecimal moduleTemperatureC;
    private BigDecimal ambientTemperatureC;
    private BigDecimal humidityPercent;
    @TableField("wind_speed_m_s")
    private BigDecimal windSpeedMS;
    private String dataSource;
    private String rawData;
    private LocalDateTime createdAt;
}
