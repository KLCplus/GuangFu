package com.example.pvplatform.module.dashboard.service;

import com.example.pvplatform.module.weather.vo.CurrentWeatherVO;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.StationRuntimeConfigDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class DerivedMetricCalculatorTest {
    private final DerivedMetricCalculator calculator = new DerivedMetricCalculator();

    @Test
    void sunnyDayProducesHighButBoundedPvPower() {
        var snapshot = calculator.calculate(station("RUNNING"), config(200), null, weather("晴"),
            OffsetDateTime.of(2026, 7, 14, 12, 0, 0, 0, ZoneOffset.ofHours(8)));
        assertTrue(snapshot.irradianceWm2() > 650);
        assertTrue(snapshot.pvPowerKw() > 90);
        assertTrue(snapshot.pvPowerKw() <= 200);
    }

    @Test
    void rainyDayReducesIrradianceAndPower() {
        var station = station("RUNNING");
        var config = config(200);
        var time = OffsetDateTime.of(2026, 7, 14, 12, 0, 0, 0, ZoneOffset.ofHours(8));
        var sunny = calculator.calculate(station, config, null, weather("晴"), time);
        var rainy = calculator.calculate(station, config, null, weather("中雨"), time);
        assertTrue(rainy.irradianceWm2() < sunny.irradianceWm2());
        assertTrue(rainy.pvPowerKw() < sunny.pvPowerKw());
    }

    @Test
    void nightPvPowerIsZero() {
        var snapshot = calculator.calculate(station("RUNNING"), config(200), null, weather("晴"),
            OffsetDateTime.of(2026, 7, 14, 23, 30, 0, 0, ZoneOffset.ofHours(8)));
        assertTrue(snapshot.irradianceWm2() <= 5);
        assertEquals(0D, snapshot.pvPowerKw());
    }

    @Test
    void currentMatchesThreePhasePowerFormula() {
        var snapshot = calculator.calculate(station("RUNNING"), config(200), null, weather("晴"),
            OffsetDateTime.of(2026, 7, 14, 12, 0, 0, 0, ZoneOffset.ofHours(8)));
        double recalculated = Math.sqrt(3) * snapshot.voltageV() * snapshot.currentA() * 0.96 / 1000D;
        assertEquals(snapshot.pvPowerKw(), recalculated, snapshot.pvPowerKw() * 0.02 + 0.2);
    }

    @Test
    void energyBalanceAllowsOnlySmallLoss() {
        var snapshot = calculator.calculate(station("RUNNING"), config(200), null, weather("晴"),
            OffsetDateTime.of(2026, 7, 14, 12, 0, 0, 0, ZoneOffset.ofHours(8)));
        double discharge = Math.max(0D, -snapshot.batteryPowerKw());
        double charge = Math.max(0D, snapshot.batteryPowerKw());
        double input = snapshot.pvPowerKw() + Math.max(0D, snapshot.gridPowerKw()) + discharge;
        double output = snapshot.loadPowerKw() + charge + Math.max(0D, -snapshot.gridPowerKw()) + snapshot.systemLossKw();
        assertEquals(input, output, Math.max(0.3, input * 0.02));
    }

    private PowerStationDO station(String status) {
        PowerStationDO station = new PowerStationDO();
        station.setStationId(7L);
        station.setStationName("Test Station");
        station.setLatitude(BigDecimal.valueOf(30.6));
        station.setLongitude(BigDecimal.valueOf(104.1));
        station.setCapacityKw(BigDecimal.valueOf(200));
        station.setStatus(status);
        return station;
    }

    private StationRuntimeConfigDO config(double batteryKwh) {
        StationRuntimeConfigDO config = new StationRuntimeConfigDO();
        config.setStationId(7L);
        config.setBatteryCapacityKwh(BigDecimal.valueOf(batteryKwh));
        config.setNominalVoltageV(BigDecimal.valueOf(380));
        config.setPhaseType("THREE_PHASE");
        config.setPerformanceRatio(BigDecimal.valueOf(0.84));
        config.setPowerFactor(BigDecimal.valueOf(0.96));
        config.setBaseLoadKw(BigDecimal.valueOf(35));
        config.setLoadPeakFactor(BigDecimal.valueOf(1.5));
        config.setStationType("INDUSTRIAL");
        config.setTimezone("Asia/Shanghai");
        return config;
    }

    private CurrentWeatherVO weather(String text) {
        return new CurrentWeatherVO(7L, text, 28D, 55D, "东风", "3", 2.5D,
            "2026-07-14 12:00:00", "TEST", false);
    }
}
