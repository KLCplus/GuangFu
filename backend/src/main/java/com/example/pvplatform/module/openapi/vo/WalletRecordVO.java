package com.example.pvplatform.module.openapi.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletRecordVO(
    Long recordId,
    String type,
    BigDecimal amount,
    String title,
    LocalDateTime createdAt
) {}
