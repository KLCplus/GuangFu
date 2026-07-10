package com.example.pvplatform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("external_pv_station")
public class ExternalPvStationDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String source;
    private Long externalSystemId;
    private String systemName;
    private Integer systemSizeW;
    private String postcode;
    private String orientation;
    private Integer outputs;
    private String lastOutputText;
    private String panel;
    private String inverter;
    private BigDecimal distanceKm;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean enabled;
    private LocalDateTime lastSyncTime;
    private String lastSyncStatus;
    private String lastSyncError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
