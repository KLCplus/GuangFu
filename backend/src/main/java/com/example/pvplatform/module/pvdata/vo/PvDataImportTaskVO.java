package com.example.pvplatform.module.pvdata.vo;

public record PvDataImportTaskVO(
    Long importId,
    Long stationId,
    String fileName,
    String status,
    int totalCount,
    int successCount,
    int failCount,
    String errorMessage,
    String createdAt,
    String finishedAt
) {
}
