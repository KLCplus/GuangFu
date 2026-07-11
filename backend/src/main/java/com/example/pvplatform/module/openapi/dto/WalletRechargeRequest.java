package com.example.pvplatform.module.openapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record WalletRechargeRequest(
    @NotNull @DecimalMin("1.00") @DecimalMax("100000.00") BigDecimal amount,
    @Pattern(regexp = "MOCK|ALIPAY|WECHAT|BANK", message = "充值渠道不合法") String channel
) {}
