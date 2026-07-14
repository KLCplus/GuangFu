package com.example.pvplatform.module.dashboard.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.StationRuntimeConfigDO;
import com.example.pvplatform.persistence.mapper.StationRuntimeConfigMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class StationRuntimeConfigService {
    private final StationRuntimeConfigMapper mapper;

    public StationRuntimeConfigService(StationRuntimeConfigMapper mapper) {
        this.mapper = mapper;
    }

    public StationRuntimeConfigDO getOrCreate(PowerStationDO station) {
        StationRuntimeConfigDO existing = mapper.selectOne(Wrappers.<StationRuntimeConfigDO>lambdaQuery()
            .eq(StationRuntimeConfigDO::getStationId, station.getStationId()).last("LIMIT 1"));
        if (existing != null) return existing;
        StationRuntimeConfigDO row = defaults(station);
        mapper.insert(row);
        return row;
    }

    private StationRuntimeConfigDO defaults(PowerStationDO station) {
        long seed = station.getStationId() == null ? 1L : station.getStationId();
        double capacity = station.getCapacityKw() == null ? 100D : station.getCapacityKw().doubleValue();
        StationRuntimeConfigDO row = new StationRuntimeConfigDO();
        row.setStationId(station.getStationId());
        row.setBatteryCapacityKwh(seed % 3 == 0 ? null : bd(Math.max(0, capacity * (1.2 + (seed % 5) * 0.25))));
        row.setNominalVoltageV(bd(capacity <= 8 ? 220D : 380D));
        row.setPhaseType(capacity <= 8 ? "SINGLE_PHASE" : "THREE_PHASE");
        row.setPerformanceRatio(bd(0.78 + (seed % 13) * 0.009));
        row.setPowerFactor(bd(0.92 + (seed % 8) * 0.008));
        row.setBaseLoadKw(bd(Math.max(0.6, capacity * (0.18 + (seed % 7) * 0.025))));
        row.setLoadPeakFactor(bd(1.25 + (seed % 6) * 0.12));
        row.setStationType(seed % 2 == 0 ? "INDUSTRIAL" : "COMMERCIAL");
        row.setTimezone("Asia/Shanghai");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        return row;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
