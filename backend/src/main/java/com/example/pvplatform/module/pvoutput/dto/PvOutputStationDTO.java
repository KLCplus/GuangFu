package com.example.pvplatform.module.pvoutput.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PvOutputStationDTO(
    Long id,
    String source,
    Long externalSystemId,
    String systemName,
    Integer systemSizeW,
    String postcode,
    String orientation,
    Integer outputs,
    String lastOutputText,
    String panel,
    String inverter,
    BigDecimal distanceKm,
    BigDecimal latitude,
    BigDecimal longitude,
    Boolean enabled,
    LocalDateTime lastSyncTime,
    String lastSyncStatus,
    String lastSyncError
) {}
