package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("weather_data")
public class WeatherDataDO {
    @TableId(type = IdType.AUTO)
    private Long weatherId;
    private Long stationId;
    private LocalDateTime weatherTime;
    private String weatherCode;
    private String weatherText;
    private BigDecimal temperatureC;
    private BigDecimal humidityPercent;
    private String windDirection;
    private String windPower;
    @TableField("wind_speed_m_s")
    private BigDecimal windSpeedMS;
    private BigDecimal precipitationMm;
    private BigDecimal cloudinessPercent;
    private String source;
    private String rawData;
    private LocalDateTime createdAt;
}
