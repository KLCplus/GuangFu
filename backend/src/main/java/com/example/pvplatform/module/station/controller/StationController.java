package com.example.pvplatform.module.station.controller;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.station.dto.StationRequest;
import com.example.pvplatform.module.station.service.StationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class StationController {
    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping("/api/stations")
    public Result<?> list() {
        return Result.success(PageResult.of(stationService.list()));
    }

    @GetMapping("/api/stations/{stationId}")
    public Result<?> detail(@PathVariable Long stationId) {
        return Result.success(stationService.detail(stationId));
    }

    @PostMapping("/api/admin/stations")
    public Result<?> create(@RequestBody StationRequest request) {
        return Result.success(Map.of("stationId", stationService.create(request)));
    }

    @PutMapping("/api/admin/stations/{stationId}")
    public Result<?> update(@PathVariable Long stationId, @RequestBody StationRequest request) {
        stationService.update(stationId, request);
        return Result.success(Map.of("stationId", stationId, "updated", true));
    }

    @DeleteMapping("/api/admin/stations/{stationId}")
    public Result<?> delete(@PathVariable Long stationId) {
        stationService.delete(stationId);
        return Result.success(Map.of("stationId", stationId, "deleted", true));
    }
}
