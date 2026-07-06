package com.example.pvplatform.module.pvdata.parser;

import com.example.pvplatform.module.pvdata.dto.PvDataImportRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

abstract class AbstractPvDataFileParser {
    static final List<String> HEADERS = List.of(
        "collectTime", "powerKw", "energyTodayKwh", "energyTotalKwh", "voltageV",
        "currentA", "irradianceWM2", "moduleTemperatureC", "ambientTemperatureC",
        "humidityPercent", "windSpeedMS");
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    PvDataImportRow toRow(int rowNumber, List<String> cells) {
        if (cells.size() != HEADERS.size()) {
            throw new IllegalArgumentException("列数应为 " + HEADERS.size());
        }
        LocalDateTime time;
        try {
            time = LocalDateTime.parse(required(cells.get(0), "collectTime"), TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("collectTime 格式必须为 yyyy-MM-dd HH:mm:ss");
        }
        return new PvDataImportRow(rowNumber, time,
            requiredDecimal(cells.get(1), "powerKw"),
            decimal(cells.get(2), "energyTodayKwh"),
            decimal(cells.get(3), "energyTotalKwh"),
            decimal(cells.get(4), "voltageV"),
            decimal(cells.get(5), "currentA"),
            decimal(cells.get(6), "irradianceWM2"),
            decimal(cells.get(7), "moduleTemperatureC"),
            decimal(cells.get(8), "ambientTemperatureC"),
            decimal(cells.get(9), "humidityPercent"),
            decimal(cells.get(10), "windSpeedMS"));
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private BigDecimal requiredDecimal(String value, String field) {
        BigDecimal result = decimal(value, field);
        if (result == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return result;
    }

    private BigDecimal decimal(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " 必须是数字");
        }
    }
}
