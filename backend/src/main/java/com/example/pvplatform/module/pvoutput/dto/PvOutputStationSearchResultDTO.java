package com.example.pvplatform.module.pvoutput.dto;

import java.math.BigDecimal;

public record PvOutputStationSearchResultDTO(
    String systemName,
    Integer systemSizeW,
    String postcode,
    String orientation,
    Integer outputs,
    String lastOutputText,
    Long externalSystemId,
    String panel,
    String inverter,
    BigDecimal distanceKm,
    BigDecimal latitude,
    BigDecimal longitude
) {}
