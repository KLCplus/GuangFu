package com.example.pvplatform.module.pvdata.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/stations/{stationId}")
public class PvDataController {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
        return Result.success(pvDataService.history(stationId, parse(startTime), parse(endTime)));
    }

    @PostMapping("/data/upload")
    public Result<?> upload(@PathVariable Long stationId, @RequestParam MultipartFile file) {
        return Result.success(Map.of("fileName", file.getOriginalFilename(), "successCount", 30, "failCount", 0));
    }

    private LocalDateTime parse(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value, FORMATTER);
    }
}
