package com.example.pvplatform.module.openapi.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RechargeOrderVO(
    Long orderId,
    String orderNo,
    BigDecimal amount,
    String currency,
    String channel,
    String status,
    LocalDateTime paidAt,
    LocalDateTime createdAt
) {}
