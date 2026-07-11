package com.example.pvplatform.module.openapi.vo;

import java.util.List;

public record OpenAccountOverviewVO(
    WalletVO wallet,
    List<ApiEntitlementVO> apiEntitlements,
    List<OpenPlanVO> plans
) {}
