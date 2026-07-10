package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("external_pv_station_status")
public class ExternalPvStationStatusDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long externalSystemId;
    private LocalDateTime sampleTime;
    private Integer energyGenerationWh;
    private Integer powerGenerationW;
    private Integer energyConsumptionWh;
    private Integer powerConsumptionW;
    private BigDecimal normalisedOutput;
    private BigDecimal temperatureC;
    private BigDecimal voltageV;
    private String rawPayload;
    private LocalDateTime fetchedAt;
}
