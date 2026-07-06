package com.example.pvplatform.module.pvdata.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PvDataImportRow(
    int rowNumber,
    LocalDateTime collectTime,
    BigDecimal powerKw,
    BigDecimal energyTodayKwh,
    BigDecimal energyTotalKwh,
    BigDecimal voltageV,
    BigDecimal currentA,
    BigDecimal irradianceWM2,
    BigDecimal moduleTemperatureC,
    BigDecimal ambientTemperatureC,
    BigDecimal humidityPercent,
    BigDecimal windSpeedMS
) {
}
