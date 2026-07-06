package com.example.pvplatform.module.pvdata.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/stations/{stationId}")
public class PvDataController {
    private final PvDataService pvDataService;

    public PvDataController(PvDataService pvDataService) {
        this.pvDataService = pvDataService;
    }

    @GetMapping("/realtime")
    public Result<?> realtime(@PathVariable Long stationId) {
        return Result.success(pvDataService.realtime(stationId));
    }

    @GetMapping("/history")
    public Result<?> history(@PathVariable Long stationId,
                             @RequestParam(required = false) String startTime,
                             @RequestParam(required = false) String endTime,
                             @RequestParam(defaultValue = "1min") String interval) {
        return Result.success(pvDataService.history());
    }

    @PostMapping("/data/upload")
    public Result<?> upload(@PathVariable Long stationId, @RequestParam MultipartFile file) {
        return Result.success(Map.of("fileName", file.getOriginalFilename(), "successCount", 30, "failCount", 0));
    }
}
