package com.example.pvplatform.module.pvoutput.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.pvoutput.dto.PvOutputStationSaveRequest;
import com.example.pvplatform.module.pvoutput.service.PvOutputStationService;
import com.example.pvplatform.module.pvoutput.service.PvOutputSyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/pvoutput/stations")
public class PvOutputController {
    private final PvOutputStationService stationService;
    private final PvOutputSyncService syncService;

    public PvOutputController(PvOutputStationService stationService, PvOutputSyncService syncService) {
        this.stationService = stationService;
        this.syncService = syncService;
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "au") String countryCode,
                            @RequestParam(defaultValue = "7") int seenDays) {
        return Result.success(stationService.search(keyword, countryCode, seenDays));
    }

    @PostMapping
    public Result<?> save(@RequestBody PvOutputStationSaveRequest request) {
        return Result.success(stationService.save(request));
    }

    @GetMapping
    public Result<?> list(@RequestParam(required = false) Boolean enabled,
                          @RequestParam(required = false) String keyword) {
        return Result.success(stationService.list(enabled, keyword));
    }

    @PatchMapping("/{id}/enabled")
    public Result<?> enabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.success(stationService.setEnabled(id, enabled));
    }

    @PostMapping("/{id}/sync")
    public Result<?> sync(@PathVariable Long id) {
        return Result.success(syncService.syncOne(id));
    }

    @PostMapping("/sync-all")
    public Result<?> syncAll() {
        return Result.success(syncService.syncAllEnabled());
    }

    @GetMapping("/{id}/latest-status")
    public Result<?> latestStatus(@PathVariable Long id) {
        return Result.success(stationService.latestStatus(id));
    }

    @GetMapping("/{id}/status")
    public Result<?> history(@PathVariable Long id,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(stationService.history(id, startTime, endTime));
    }
}
