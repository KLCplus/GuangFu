package com.example.pvplatform.module.dashboard.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public Result<?> overview(@RequestParam(required = false) Long stationId) {
        return Result.success(dashboardService.overview(stationId));
    }

    @GetMapping("/stations/{stationId}")
    public Result<?> stationDashboard(@PathVariable Long stationId) {
        return Result.success(dashboardService.stationDashboard(stationId));
    }
}
