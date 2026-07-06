package com.example.pvplatform.module.pvdata.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import com.example.pvplatform.module.pvdata.service.PvDataImportService;
import com.example.pvplatform.module.pvdata.dto.PvDataHistoryQuery;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/stations/{stationId}")
public class PvDataController {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PvDataService pvDataService;
    private final PvDataImportService importService;

    public PvDataController(PvDataService pvDataService, PvDataImportService importService) {
        this.pvDataService = pvDataService;
        this.importService = importService;
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
        return Result.success(pvDataService.history(stationId,
            new PvDataHistoryQuery(parse(startTime), parse(endTime), interval)));
    }

    @PostMapping("/data/upload")
    public Result<?> upload(@PathVariable Long stationId,
                            @RequestParam MultipartFile file,
                            @RequestParam(defaultValue = "SKIP") String duplicateStrategy) {
        return Result.success(importService.importFile(stationId, file, duplicateStrategy));
    }

    @GetMapping("/data/imports/{importId}")
    public Result<?> importTask(@PathVariable Long stationId, @PathVariable Long importId) {
        return Result.success(importService.task(stationId, importId));
    }

    private LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (RuntimeException exception) {
            throw new com.example.pvplatform.common.exception.BusinessException(
                400, "时间格式必须为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
