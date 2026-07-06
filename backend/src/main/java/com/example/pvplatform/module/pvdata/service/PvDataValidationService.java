package com.example.pvplatform.module.pvdata.service;

import com.example.pvplatform.module.pvdata.dto.PvDataImportRow;
import org.springframework.stereotype.Service;

@Service
public class PvDataValidationService {
    public String validate(PvDataImportRow row) {
        if (negative(row.powerKw())) return "powerKw 不能为负数";
        if (negative(row.energyTodayKwh())) return "energyTodayKwh 不能为负数";
        if (negative(row.energyTotalKwh())) return "energyTotalKwh 不能为负数";
        if (negative(row.irradianceWM2())) return "irradianceWM2 不能为负数";
        if (negative(row.windSpeedMS())) return "windSpeedMS 不能为负数";
        if (row.humidityPercent() != null
            && (row.humidityPercent().signum() < 0
            || row.humidityPercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0)) {
            return "humidityPercent 必须在 0 到 100 之间";
        }
        return null;
    }

    private boolean negative(java.math.BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
