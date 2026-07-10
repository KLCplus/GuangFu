package com.example.pvplatform.module.pvoutput.dto;

public record PvOutputSyncResultDTO(
    Long stationId,
    Long externalSystemId,
    String systemName,
    String status,
    String message,
    PvOutputStatusDTO latestStatus
) {}
