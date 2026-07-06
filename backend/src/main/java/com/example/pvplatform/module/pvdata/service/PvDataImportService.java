package com.example.pvplatform.module.pvdata.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.pvdata.dto.*;
import com.example.pvplatform.module.pvdata.parser.*;
import com.example.pvplatform.module.pvdata.vo.*;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.persistence.entity.*;
import com.example.pvplatform.persistence.mapper.*;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PvDataImportService {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PvDataMapper dataMapper;
    private final PvDataImportTaskMapper taskMapper;
    private final StationPermissionService permissionService;
    private final PvFileStorageService storageService;
    private final PvDataValidationService validationService;
    private final List<PvDataFileParser> parsers;
    private final int maxRows;

    public PvDataImportService(PvDataMapper dataMapper,
                               PvDataImportTaskMapper taskMapper,
                               StationPermissionService permissionService,
                               PvFileStorageService storageService,
                               PvDataValidationService validationService,
                               List<PvDataFileParser> parsers,
                               @Value("${pv-data.import.max-rows:100000}") int maxRows) {
        this.dataMapper = dataMapper;
        this.taskMapper = taskMapper;
        this.permissionService = permissionService;
        this.storageService = storageService;
        this.validationService = validationService;
        this.parsers = parsers;
        this.maxRows = maxRows;
    }

    public PvDataImportResultVO importFile(Long stationId, MultipartFile file, String strategyValue) {
        permissionService.requireManage(stationId);
        Long userId = SecurityUtils.requireCurrentUserId();
        DuplicateStrategy strategy = DuplicateStrategy.parse(strategyValue);
        PvFileStorageService.StoredFile stored = storageService.store(file, userId);
        PvDataImportTaskDO task = createTask(stationId, userId, stored);
        List<PvDataImportErrorVO> errors = new ArrayList<>();
        try {
            task.setStatus("PROCESSING");
            taskMapper.updateById(task);
            PvDataFileParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(stored.extension())).findFirst()
                .orElseThrow(() -> new BusinessException(400, "不支持的文件格式"));
            ParsedPvDataFile parsed;
            try (InputStream input = Files.newInputStream(stored.path())) {
                parsed = parser.parse(input, maxRows);
            }
            errors.addAll(parsed.errors());
            task.setTotalCount(parsed.totalCount());
            task.setFailCount(parsed.errors().size());
            taskMapper.updateById(task);
            List<PvDataImportRow> valid = new ArrayList<>();
            for (PvDataImportRow row : parsed.rows()) {
                String error = validationService.validate(row);
                if (error == null) {
                    valid.add(row);
                } else {
                    addError(errors, row.rowNumber(), error);
                }
            }
            ImportCounts counts = persist(stationId, valid, strategy, errors);
            int failed = parsed.totalCount() - counts.success();
            finish(task, counts.success() == 0 && failed > 0 ? "FAILED" : "SUCCESS",
                parsed.totalCount(), counts.success(), failed, summarize(errors));
            return result(task, errors);
        } catch (BusinessException exception) {
            finish(task, "FAILED", task.getTotalCount(), task.getSuccessCount(),
                Math.max(value(task.getFailCount()), errors.size()), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            finish(task, "FAILED", task.getTotalCount(), task.getSuccessCount(),
                task.getFailCount(), "导入处理失败");
            throw new BusinessException(500, "光伏数据导入失败");
        }
    }

    public PvDataImportTaskVO task(Long stationId, Long importId) {
        permissionService.requireView(stationId);
        PvDataImportTaskDO task = taskMapper.selectById(importId);
        if (task == null || !stationId.equals(task.getStationId())) {
            throw new BusinessException(404, "导入任务不存在");
        }
        if (!permissionService.isAdmin()
            && !SecurityUtils.requireCurrentUserId().equals(task.getUserId())) {
            throw new BusinessException(403, "无权查看该导入任务");
        }
        return toVO(task);
    }

    private ImportCounts persist(Long stationId, List<PvDataImportRow> rows,
                                 DuplicateStrategy strategy,
                                 List<PvDataImportErrorVO> errors) {
        if (rows.isEmpty()) {
            return new ImportCounts(0);
        }
        LocalDateTime min = rows.stream().map(PvDataImportRow::collectTime).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime max = rows.stream().map(PvDataImportRow::collectTime).max(Comparator.naturalOrder()).orElseThrow();
        Set<LocalDateTime> existing = new HashSet<>(dataMapper.selectList(
            Wrappers.<PvDataDO>lambdaQuery().select(PvDataDO::getCollectTime)
                .eq(PvDataDO::getStationId, stationId)
                .between(PvDataDO::getCollectTime, min, max))
            .stream().map(PvDataDO::getCollectTime).toList());
        List<PvDataImportRow> duplicates = rows.stream()
            .filter(row -> existing.contains(row.collectTime())).toList();
        if (strategy == DuplicateStrategy.FAIL && !duplicates.isEmpty()) {
            for (PvDataImportRow row : duplicates) {
                addError(errors, row.rowNumber(), "该时间点数据已存在");
            }
            throw new BusinessException(400, "检测到重复时间点，导入已终止");
        }
        List<PvDataImportRow> writeRows;
        if (strategy == DuplicateStrategy.SKIP) {
            writeRows = rows.stream().filter(row -> !existing.contains(row.collectTime())).toList();
            for (PvDataImportRow row : duplicates) {
                addError(errors, row.rowNumber(), "该时间点数据已存在，已跳过");
            }
        } else {
            writeRows = rows;
        }
        for (int start = 0; start < writeRows.size(); start += 500) {
            List<PvDataDO> batch = writeRows.subList(start, Math.min(start + 500, writeRows.size()))
                .stream().map(row -> toDO(stationId, row)).toList();
            if (strategy == DuplicateStrategy.UPDATE) {
                dataMapper.batchUpsert(batch);
            } else {
                dataMapper.batchInsert(batch);
            }
        }
        return new ImportCounts(writeRows.size());
    }

    private PvDataDO toDO(Long stationId, PvDataImportRow row) {
        PvDataDO data = new PvDataDO();
        data.setStationId(stationId);
        data.setCollectTime(row.collectTime());
        data.setPowerKw(row.powerKw());
        data.setEnergyTodayKwh(row.energyTodayKwh());
        data.setEnergyTotalKwh(row.energyTotalKwh());
        data.setVoltageV(row.voltageV());
        data.setCurrentA(row.currentA());
        data.setIrradianceWM2(row.irradianceWM2());
        data.setModuleTemperatureC(row.moduleTemperatureC());
        data.setAmbientTemperatureC(row.ambientTemperatureC());
        data.setHumidityPercent(row.humidityPercent());
        data.setWindSpeedMS(row.windSpeedMS());
        data.setDataSource("IMPORT");
        data.setCreatedAt(LocalDateTime.now());
        return data;
    }

    private PvDataImportTaskDO createTask(Long stationId, Long userId,
                                          PvFileStorageService.StoredFile stored) {
        PvDataImportTaskDO task = new PvDataImportTaskDO();
        task.setUserId(userId);
        task.setStationId(stationId);
        task.setFileName(stored.resource().getOriginalName());
        task.setFileUrl(stored.resource().getFileUrl());
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    private void finish(PvDataImportTaskDO task, String status, Integer total,
                        Integer success, Integer failed, String message) {
        task.setStatus(status);
        task.setTotalCount(total == null ? 0 : total);
        task.setSuccessCount(success == null ? 0 : success);
        task.setFailCount(failed == null ? 0 : failed);
        task.setErrorMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private String summarize(List<PvDataImportErrorVO> errors) {
        if (errors.isEmpty()) return null;
        String value = errors.stream().limit(10)
            .map(error -> "第" + error.row() + "行: " + error.message())
            .reduce((a, b) -> a + "; " + b).orElse(null);
        return value != null && value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private void addError(List<PvDataImportErrorVO> errors, int row, String message) {
        if (errors.size() < 100) {
            errors.add(new PvDataImportErrorVO(row, message));
        }
    }

    private PvDataImportResultVO result(PvDataImportTaskDO task,
                                        List<PvDataImportErrorVO> errors) {
        return new PvDataImportResultVO(task.getImportId(), task.getStatus(),
            task.getTotalCount(), task.getSuccessCount(), task.getFailCount(),
            List.copyOf(errors));
    }

    private PvDataImportTaskVO toVO(PvDataImportTaskDO task) {
        return new PvDataImportTaskVO(task.getImportId(), task.getStationId(),
            task.getFileName(), task.getStatus(), value(task.getTotalCount()),
            value(task.getSuccessCount()), value(task.getFailCount()),
            task.getErrorMessage(), format(task.getCreatedAt()), format(task.getFinishedAt()));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(FORMATTER);
    }

    private record ImportCounts(int success) {
    }
}
