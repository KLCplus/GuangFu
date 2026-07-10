package com.example.pvplatform.module.pvoutput.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PvOutputStatusDTO(
    Long id,
    Long externalSystemId,
    LocalDateTime sampleTime,
    Integer energyGenerationWh,
    Integer powerGenerationW,
    Integer energyConsumptionWh,
    Integer powerConsumptionW,
    BigDecimal normalisedOutput,
    BigDecimal temperatureC,
    BigDecimal voltageV,
    LocalDateTime fetchedAt
) {}
