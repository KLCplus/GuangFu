package com.example.pvplatform.module.pvdata.dto;

import com.example.pvplatform.common.exception.BusinessException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

public record PvDataHistoryQuery(LocalDateTime startTime, LocalDateTime endTime, String interval) {
    private static final Set<String> INTERVALS = Set.of("1min", "5min", "15min", "1h");

    public PvDataHistoryQuery normalized() {
        LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime start = startTime == null ? end.minusHours(24) : startTime;
        String grain = interval == null || interval.isBlank() ? "1min" : interval;
        if (!INTERVALS.contains(grain)) {
            throw new BusinessException(400, "不支持的时间粒度");
        }
        if (!start.isBefore(end)) {
            throw new BusinessException(400, "开始时间必须早于结束时间");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(Duration.ofDays(31)) > 0) {
            throw new BusinessException(400, "历史数据查询范围不能超过 31 天");
        }
        if (duration.compareTo(Duration.ofDays(7)) > 0
            && ("1min".equals(grain) || "5min".equals(grain))) {
            throw new BusinessException(400, "超过 7 天的查询必须使用 15min 或 1h 粒度");
        }
        return new PvDataHistoryQuery(start, end, grain);
    }

    public int intervalMinutes() {
        return switch (interval) {
            case "1min" -> 1;
            case "5min" -> 5;
            case "15min" -> 15;
            case "1h" -> 60;
            default -> throw new BusinessException(400, "不支持的时间粒度");
        };
    }
}
