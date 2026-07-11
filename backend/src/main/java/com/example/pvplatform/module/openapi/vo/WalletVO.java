package com.example.pvplatform.module.openapi.vo;

import java.math.BigDecimal;
import java.util.List;

public record WalletVO(
    BigDecimal balance,
    BigDecimal frozenBalance,
    BigDecimal monthlyCost,
    String currency,
    List<WalletRecordVO> records
) {}
