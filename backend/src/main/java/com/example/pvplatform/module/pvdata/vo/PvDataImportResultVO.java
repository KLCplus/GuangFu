package com.example.pvplatform.module.pvdata.vo;

import java.util.List;

public record PvDataImportResultVO(
    Long importId,
    String status,
    int totalCount,
    int successCount,
    int failCount,
    List<PvDataImportErrorVO> errors
) {
}
