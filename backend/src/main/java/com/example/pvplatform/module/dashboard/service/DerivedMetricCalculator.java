package com.example.pvplatform.module.dashboard.service;

import com.example.pvplatform.module.weather.vo.CurrentWeatherVO;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.PvDataDO;
import com.example.pvplatform.persistence.entity.StationRuntimeConfigDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;

@Component
public class DerivedMetricCalculator {
    private static final double SQRT3 = Math.sqrt(3D);

    public Snapshot calculate(PowerStationDO station, StationRuntimeConfigDO config, PvDataDO real,
                              CurrentWeatherVO weather, OffsetDateTime snapshotTime) {
        ZoneId zone = zone(config);
        ZonedDateTime localTime = snapshotTime.atZoneSameInstant(zone);
        double capacity = positive(station.getCapacityKw(), 100D);
        String status = station.getStatus() == null ? "RUNNING" : station.getStatus();
        double ambient = choose(real == null ? null : real.getAmbientTemperatureC(),
            weather == null ? null : weather.temperature(), 24D + stationNoise(station, "temp", localTime) * 3D);
        double humidity = choose(real == null ? null : real.getHumidityPercent(),
            weather == null ? null : weather.humidity(), 55D + stationNoise(station, "hum", localTime) * 8D);
        double wind = choose(real == null ? null : real.getWindSpeedMS(),
            weather == null ? null : weather.windSpeed(), 2.2D + stationNoise(station, "wind", localTime) * 0.8D);
        double irradiance = real != null && real.getIrradianceWM2() != null
            ? clamp(real.getIrradianceWM2().doubleValue(), 0D, 1100D)
            : derivedIrradiance(station, weather, localTime);
        double pvPower = real != null && real.getPowerKw() != null
            ? clamp(real.getPowerKw().doubleValue(), 0D, capacity)
            : derivedPvPower(capacity, irradiance, ambient, config);

        if ("OFFLINE".equalsIgnoreCase(status) || "STOPPED".equalsIgnoreCase(status)) {
            pvPower = 0D;
            irradiance = Math.min(irradiance, 5D);
        } else if ("MAINTENANCE".equalsIgnoreCase(status)) {
            pvPower *= 0.18D;
        } else if ("FAULT".equalsIgnoreCase(status)) {
            pvPower *= 0.08D;
        }

        double load = derivedLoad(station, config, localTime);
        Battery battery = derivedBattery(station, config, localTime, pvPower, load);
        double loss = Math.max(0.02D, (pvPower + load + Math.abs(battery.powerKw())) * 0.02D);
        double charge = Math.max(0D, battery.powerKw());
        double discharge = Math.max(0D, -battery.powerKw());
        double grid = load + charge + loss - pvPower - discharge;
        double voltage = real != null && real.getVoltageV() != null
            ? real.getVoltageV().doubleValue()
            : derivedVoltage(station, config, localTime);
        double current = real != null && real.getCurrentA() != null
            ? real.getCurrentA().doubleValue()
            : currentFor(pvPower, voltage, config);

        return new Snapshot(round(pvPower, 2), round(load, 2), round(grid, 2), round(battery.powerKw(), 2),
            battery.soc() == null ? null : round(battery.soc(), 1), round(voltage, 1), round(current, 1),
            round(irradiance, 0), round(ambient, 1), round(clamp(humidity, 0D, 100D), 0),
            round(Math.max(0D, wind), 1), round(loss, 2), real == null ? "DERIVED" : "REAL");
    }

    public double derivedIrradiance(PowerStationDO station, CurrentWeatherVO weather, ZonedDateTime time) {
        int minute = time.getHour() * 60 + time.getMinute();
        int sunrise = 360 - (int) Math.round(Math.max(-45D, Math.min(45D, lat(station))) * 0.8D);
        int sunset = 1110 + (int) Math.round(Math.max(-45D, Math.min(45D, lat(station))) * 0.8D);
        if (minute <= sunrise || minute >= sunset) {
            return clamp(2D + Math.abs(stationNoise(station, "night", time)) * 3D, 0D, 5D);
        }
        double daylight = (minute - sunrise) / (double) (sunset - sunrise);
        double clear = 980D * Math.pow(Math.sin(Math.PI * daylight), 1.3D);
        double factor = weatherFactor(weather == null ? null : weather.weather(), station, time);
        double noise = 1D + stationNoise(station, "irr", time) * weatherNoiseRange(weather == null ? null : weather.weather());
        return clamp(clear * factor * noise, 0D, 1100D);
    }

    public double derivedPvPower(double capacity, double irradiance, double ambient, StationRuntimeConfigDO config) {
        if (irradiance <= 5D) return 0D;
        double cellTemperature = ambient + 0.03D * irradiance;
        double tempFactor = clamp(1D - 0.004D * (cellTemperature - 25D), 0.78D, 1.05D);
        double pr = positive(config.getPerformanceRatio(), 0.84D);
        return clamp(capacity * irradiance / 1000D * pr * tempFactor, 0D, capacity);
    }

    public double currentFor(double powerKw, double voltage, StationRuntimeConfigDO config) {
        if (powerKw <= 0D || voltage <= 0D) return 0D;
        double pf = positive(config.getPowerFactor(), 0.96D);
        if ("SINGLE_PHASE".equalsIgnoreCase(config.getPhaseType())) {
            return powerKw * 1000D / (voltage * pf);
        }
        return powerKw * 1000D / (SQRT3 * voltage * pf);
    }

    private double derivedLoad(PowerStationDO station, StationRuntimeConfigDO config, ZonedDateTime time) {
        double base = positive(config.getBaseLoadKw(), 10D);
        double peak = positive(config.getLoadPeakFactor(), 1.5D);
        double hour = time.getHour() + time.getMinute() / 60D;
        double curve;
        if (hour < 6D) curve = 0.62D;
        else if (hour < 10D) curve = 0.62D + (hour - 6D) / 4D * 0.55D;
        else if (hour < 14D) curve = 1.12D;
        else if (hour < 18D) curve = "INDUSTRIAL".equals(config.getStationType()) ? peak : 1.05D;
        else if (hour < 22D) curve = "COMMERCIAL".equals(config.getStationType()) ? 0.9D : 0.72D;
        else curve = 0.58D;
        return Math.max(0.1D, base * curve * (1D + stationNoise(station, "load", time) * 0.025D));
    }

    private Battery derivedBattery(PowerStationDO station, StationRuntimeConfigDO config, ZonedDateTime time,
                                   double pvPower, double load) {
        double capacity = positive(config.getBatteryCapacityKwh(), 0D);
        if (capacity <= 0D) return new Battery(0D, null);
        double surplus = pvPower - load;
        double maxRate = Math.max(2D, capacity * 0.35D);
        double power;
        if (surplus > 0D) power = Math.min(maxRate, surplus * 0.65D);
        else power = -Math.min(maxRate, Math.abs(surplus) * 0.45D);
        int minuteOfDay = time.getHour() * 60 + time.getMinute();
        double soc = 52D + 23D * Math.sin((minuteOfDay - 420D) / 1440D * 2D * Math.PI)
            + stationNoise(station, "soc", time) * 2D;
        if (power > 0D) soc += 5D;
        if (power < 0D) soc -= 4D;
        return new Battery(clamp(power, -maxRate, maxRate), clamp(soc, 5D, 98D));
    }

    private double derivedVoltage(PowerStationDO station, StationRuntimeConfigDO config, ZonedDateTime time) {
        double nominal = positive(config.getNominalVoltageV(), 380D);
        double pct = stationNoise(station, "voltage", time) * 0.008D;
        double voltage = nominal * (1D + pct);
        return nominal <= 240D ? clamp(voltage, 205D, 240D) : clamp(voltage, 360D, 410D);
    }

    private double weatherFactor(String text, PowerStationDO station, ZonedDateTime time) {
        String value = text == null ? "" : text;
        if (value.contains("暴雨") || value.contains("大雨") || value.contains("中雨")) return 0.02D + stable01(station, "rain", time) * 0.13D;
        if (value.contains("雨")) return 0.08D + stable01(station, "lightRain", time) * 0.22D;
        if (value.contains("阴")) return 0.20D + stable01(station, "overcast", time) * 0.35D;
        if (value.contains("云")) return 0.55D + stable01(station, "cloud", time) * 0.30D;
        return 0.85D + stable01(station, "sun", time) * 0.15D;
    }

    private double weatherNoiseRange(String text) {
        String value = text == null ? "" : text;
        return value.contains("云") || value.contains("雨") ? 0.07D : 0.035D;
    }

    private ZoneId zone(StationRuntimeConfigDO config) {
        try {
            return ZoneId.of(config.getTimezone() == null ? "Asia/Shanghai" : config.getTimezone());
        } catch (DateTimeException exception) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private double stationNoise(PowerStationDO station, String metric, ZonedDateTime time) {
        long bucket = time.toEpochSecond() / 30L;
        double a = noise(station.getStationId(), metric, bucket);
        double b = noise(station.getStationId(), metric, bucket - 1L);
        double fraction = (time.toEpochSecond() % 30L) / 30D;
        return b * (1D - fraction) + a * fraction;
    }

    private double stable01(PowerStationDO station, String metric, ZonedDateTime time) {
        return (stationNoise(station, metric, time) + 1D) / 2D;
    }

    private double noise(Long stationId, String metric, long bucket) {
        long seed = (stationId == null ? 1L : stationId) * 1103515245L + metric.hashCode() * 31L + bucket * 2654435761L;
        double x = Math.sin(seed) * 43758.5453123D;
        return (x - Math.floor(x)) * 2D - 1D;
    }

    private double lat(PowerStationDO station) {
        return station.getLatitude() == null ? 30D : station.getLatitude().doubleValue();
    }

    private double choose(BigDecimal real, Double weather, double fallback) {
        if (real != null) return real.doubleValue();
        if (weather != null) return weather;
        return fallback;
    }

    private double positive(BigDecimal value, double fallback) {
        return value == null || value.doubleValue() <= 0D ? fallback : value.doubleValue();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10D, scale);
        return Math.round(value * factor) / factor;
    }

    public record Snapshot(double pvPowerKw, double loadPowerKw, double gridPowerKw, double batteryPowerKw,
                           Double batterySoc, double voltageV, double currentA, double irradianceWm2,
                           double temperatureC, double humidityPercent, double windSpeedMs,
                           double systemLossKw, String source) {}

    private record Battery(double powerKw, Double soc) {}
}
