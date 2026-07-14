package com.example.pvplatform.module.dashboard.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.client.ModelServiceHealthClient;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.dashboard.vo.DashboardDataVO;
import com.example.pvplatform.module.dashboard.vo.DashboardOverviewVO;
import com.example.pvplatform.module.dashboard.vo.DashboardResourceVO;
import com.example.pvplatform.module.pvdata.entity.PvData;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import com.example.pvplatform.module.pvdata.vo.PvDataVO;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.module.station.service.StationService;
import com.example.pvplatform.module.weather.service.WeatherService;
import com.example.pvplatform.module.weather.vo.CurrentWeatherVO;
import com.example.pvplatform.module.weather.vo.WeatherForecastVO;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.PvDataDO;
import com.example.pvplatform.persistence.entity.StationRuntimeConfigDO;
import com.example.pvplatform.persistence.mapper.PvDataMapper;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final StationService stationService;
    private final PvDataService pvDataService;
    private final WeatherService weatherService;
    private final StationPermissionService permissionService;
    private final StationRuntimeConfigService configService;
    private final DerivedMetricCalculator calculator;
    private final PvDataMapper pvDataMapper;
    private final ModelServiceHealthClient modelHealthClient;

    public DashboardService(StationService stationService,
                            PvDataService pvDataService,
                            WeatherService weatherService,
                            StationPermissionService permissionService,
                            StationRuntimeConfigService configService,
                            DerivedMetricCalculator calculator,
                            PvDataMapper pvDataMapper,
                            ModelServiceHealthClient modelHealthClient) {
        this.stationService = stationService;
        this.pvDataService = pvDataService;
        this.weatherService = weatherService;
        this.permissionService = permissionService;
        this.configService = configService;
        this.calculator = calculator;
        this.pvDataMapper = pvDataMapper;
        this.modelHealthClient = modelHealthClient;
    }

    public DashboardOverviewVO overview(Long stationId) {
        PageResult<PowerStation> page = stationService.list(1, 50, null, null);
        List<PowerStation> stations = page.records();
        Long selectedStationId = stationId != null ? stationId
            : stations.stream().findFirst().map(PowerStation::stationId).orElse(null);

        CurrentWeatherVO weather = null;
        List<WeatherForecastVO> forecasts = List.of();
        PvData realtime = null;
        boolean partial = false;

        if (selectedStationId != null) {
            try {
                weather = weatherService.current(selectedStationId);
            } catch (RuntimeException exception) {
                partial = true;
            }
            try {
                forecasts = weatherService.forecast(selectedStationId);
            } catch (RuntimeException exception) {
                partial = true;
            }
            try {
                realtime = pvDataService.realtime(selectedStationId);
            } catch (RuntimeException exception) {
                partial = true;
            }
        }

        String lastUpdate = weather != null && weather.reportTime() != null
            ? weather.reportTime()
            : realtime == null ? OffsetDateTime.now(DEFAULT_ZONE).format(OFFSET_FORMATTER) : realtime.collectTime();
        return new DashboardOverviewVO(stations, selectedStationId, weather, forecasts, realtime,
            resources(), partial ? "PARTIAL" : "REMOTE", lastUpdate);
    }

    public DashboardDataVO stationDashboard(Long stationId) {
        PowerStationDO station = permissionService.requireView(stationId);
        StationRuntimeConfigDO config = configService.getOrCreate(station);
        ZoneId zone = zone(config);
        OffsetDateTime snapshotTime = OffsetDateTime.now(zone);

        CurrentWeatherVO weather = null;
        List<WeatherForecastVO> forecasts = List.of();
        boolean partial = false;
        try {
            weather = weatherService.current(stationId);
        } catch (RuntimeException exception) {
            partial = true;
        }
        try {
            forecasts = weatherService.forecast(stationId);
        } catch (RuntimeException exception) {
            partial = true;
        }

        PvDataDO real = latestRealSnapshot(stationId, snapshotTime.toLocalDateTime());
        DerivedMetricCalculator.Snapshot snapshot = calculator.calculate(station, config, real, weather, snapshotTime);
        List<DashboardDataVO.HistoryPoint> history = history(station, config, weather, snapshotTime);

        DashboardDataVO.Realtime realtime = new DashboardDataVO.Realtime(stationId, format(snapshotTime),
            snapshot.pvPowerKw(), snapshot.loadPowerKw(), snapshot.gridPowerKw(), snapshot.batteryPowerKw(),
            snapshot.batterySoc(), snapshot.voltageV(), snapshot.currentA(), snapshot.irradianceWm2(),
            snapshot.temperatureC(), snapshot.humidityPercent(), snapshot.windSpeedMs(),
            snapshot.source(), partial ? "PARTIAL" : "GOOD");
        DashboardDataVO.EnergyFlow flow = new DashboardDataVO.EnergyFlow(snapshot.pvPowerKw(),
            snapshot.loadPowerKw(), snapshot.gridPowerKw(), snapshot.batteryPowerKw(), snapshot.batterySoc(),
            snapshot.systemLossKw(), snapshot.gridPowerKw() >= 0D ? "IMPORT" : "EXPORT",
            snapshot.batteryPowerKw() > 0D ? "CHARGE" : snapshot.batteryPowerKw() < 0D ? "DISCHARGE" : "IDLE");

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("pvPowerKw", snapshot.source());
        sources.put("loadPowerKw", "DERIVED");
        sources.put("gridPowerKw", "DERIVED");
        sources.put("battery", config.getBatteryCapacityKwh() == null ? "UNAVAILABLE" : "DERIVED");
        sources.put("weather", weather == null ? "UNAVAILABLE" : weather.source());
        sources.put("history", history.isEmpty() ? "UNAVAILABLE" : "REAL_AND_DERIVED");

        return new DashboardDataVO(toStation(station), format(snapshotTime), partial ? "PARTIAL" : snapshot.source(),
            partial ? "PARTIAL" : "GOOD", realtime, weatherVO(weather, forecasts), flow, history,
            infrastructure(snapshotTime), sources);
    }

    private PvDataDO latestRealSnapshot(Long stationId, LocalDateTime snapshotTime) {
        PvDataDO row = pvDataMapper.selectOne(Wrappers.<PvDataDO>lambdaQuery()
            .eq(PvDataDO::getStationId, stationId)
            .le(PvDataDO::getCollectTime, snapshotTime)
            .orderByDesc(PvDataDO::getCollectTime)
            .last("LIMIT 1"));
        if (row == null || row.getCollectTime() == null) return null;
        return row.getCollectTime().isBefore(snapshotTime.minusHours(2)) ? null : row;
    }

    private List<DashboardDataVO.HistoryPoint> history(PowerStationDO station, StationRuntimeConfigDO config,
                                                       CurrentWeatherVO weather, OffsetDateTime snapshotTime) {
        LocalDateTime end = snapshotTime.toLocalDateTime();
        LocalDateTime start = end.minusHours(24);
        return pvDataMapper.selectHistoryAggregated(station.getStationId(), start, end, 15).stream()
            .filter(row -> parse(row.time()) != null && !parse(row.time()).isAfter(end))
            .map(row -> historyPoint(station, config, weather, row))
            .toList();
    }

    private DashboardDataVO.HistoryPoint historyPoint(PowerStationDO station, StationRuntimeConfigDO config,
                                                      CurrentWeatherVO weather, PvDataVO row) {
        LocalDateTime time = parse(row.time());
        OffsetDateTime offsetTime = time.atZone(DEFAULT_ZONE).toOffsetDateTime();
        double irradiance = row.irradiance() == null ? 0D : row.irradiance();
        if (irradiance <= 0D) {
            irradiance = calculator.derivedIrradiance(station, weather, offsetTime.atZoneSameInstant(DEFAULT_ZONE));
        }
        double capacity = station.getCapacityKw() == null ? 100D : station.getCapacityKw().doubleValue();
        double temperature = row.temperature() == null
            ? weather == null || weather.temperature() == null ? 24D : weather.temperature()
            : row.temperature();
        double pv = row.power() == null ? calculator.derivedPvPower(capacity, irradiance, temperature, config) : row.power();
        DerivedMetricCalculator.Snapshot snap = calculator.calculate(station, config, null, weather, offsetTime);
        return new DashboardDataVO.HistoryPoint(format(offsetTime), round(pv, 2), snap.loadPowerKw(),
            snap.gridPowerKw(), null, round(irradiance, 0));
    }

    private DashboardDataVO.Weather weatherVO(CurrentWeatherVO weather, List<WeatherForecastVO> forecasts) {
        if (weather == null) {
            return new DashboardDataVO.Weather("天气暂不可用", null, null, null, null, null,
                null, "UNAVAILABLE", false, forecasts);
        }
        return new DashboardDataVO.Weather(weather.weather(), weather.temperature(), weather.humidity(),
            weather.windDirection(), weather.windPower(), weather.windSpeed(), toOffset(weather.reportTime()),
            weather.source(), weather.cached(), forecasts);
    }

    private DashboardDataVO.Infrastructure infrastructure(OffsetDateTime checkedAt) {
        String model = modelHealthClient.isHealthy() ? "UP" : "UNAVAILABLE";
        return new DashboardDataVO.Infrastructure("UP", "UP", model, "未接入监控",
            format(checkedAt), "REAL");
    }

    private PowerStation toStation(PowerStationDO station) {
        return new PowerStation(station.getStationId(), station.getStationName(), station.getProvince(),
            station.getCity(), station.getAddress(), number(station.getLongitude()), number(station.getLatitude()),
            number(station.getCapacityKw()), station.getStatus(), station.getDescription());
    }

    private List<DashboardResourceVO> resources() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        int memory = max <= 0 ? 0 : (int) Math.min(100, Math.round(used * 100.0 / max));
        int cpu = (int) Math.min(95, Math.max(5,
            ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage() * 12));
        long diskTotal = java.nio.file.FileSystems.getDefault().getPath(".").toFile().getTotalSpace();
        long diskFree = java.nio.file.FileSystems.getDefault().getPath(".").toFile().getFreeSpace();
        int disk = diskTotal <= 0 ? 0 : (int) Math.min(100, Math.round((diskTotal - diskFree) * 100.0 / diskTotal));

        return List.of(
            resource("CPU", cpu, "后端服务负载"),
            resource("内存", memory, "JVM 内存使用"),
            resource("硬盘", disk, "应用工作目录磁盘"),
            resource("模型服务", modelHealthClient.isHealthy() ? 0 : 100, "模型服务健康检查")
        );
    }

    private DashboardResourceVO resource(String name, int value, String detail) {
        String level = value >= 85 ? "danger" : value >= 70 ? "warning" : "healthy";
        return new DashboardResourceVO(name, value, detail, level);
    }

    private LocalDateTime parse(String value) {
        try {
            return value == null ? null : LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeException exception) {
            throw new BusinessException(500, "历史数据时间格式错误");
        }
    }

    private String toOffset(String value) {
        LocalDateTime parsed = parse(value);
        return parsed == null ? null : format(parsed.atZone(DEFAULT_ZONE).toOffsetDateTime());
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : value.format(OFFSET_FORMATTER);
    }

    private ZoneId zone(StationRuntimeConfigDO config) {
        try {
            return ZoneId.of(config.getTimezone() == null ? "Asia/Shanghai" : config.getTimezone());
        } catch (DateTimeException exception) {
            return DEFAULT_ZONE;
        }
    }

    private Double number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10D, scale);
        return Math.round(value * factor) / factor;
    }
}
