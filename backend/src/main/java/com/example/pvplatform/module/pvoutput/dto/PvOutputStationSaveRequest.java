package com.example.pvplatform.module.pvoutput.dto;

import java.math.BigDecimal;

public record PvOutputStationSaveRequest(
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
    BigDecimal longitude
) {}
