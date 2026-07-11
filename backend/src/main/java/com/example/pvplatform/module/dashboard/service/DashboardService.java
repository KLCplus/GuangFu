package com.example.pvplatform.module.dashboard.service;

import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.module.dashboard.vo.DashboardOverviewVO;
import com.example.pvplatform.module.dashboard.vo.DashboardResourceVO;
import com.example.pvplatform.module.pvdata.entity.PvData;
import com.example.pvplatform.module.pvdata.service.PvDataService;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.station.service.StationService;
import com.example.pvplatform.module.weather.service.WeatherService;
import com.example.pvplatform.module.weather.vo.CurrentWeatherVO;
import com.example.pvplatform.module.weather.vo.WeatherForecastVO;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DashboardService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final StationService stationService;
    private final PvDataService pvDataService;
    private final WeatherService weatherService;

    public DashboardService(StationService stationService, PvDataService pvDataService,
                            WeatherService weatherService) {
        this.stationService = stationService;
        this.pvDataService = pvDataService;
        this.weatherService = weatherService;
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
            : realtime == null ? LocalDateTime.now().format(FORMATTER) : realtime.collectTime();
        return new DashboardOverviewVO(stations, selectedStationId, weather, forecasts, realtime,
            resources(), partial ? "PARTIAL" : "REMOTE", lastUpdate);
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
            resource("模型服务", 50, "模型服务代理通道")
        );
    }

    private DashboardResourceVO resource(String name, int value, String detail) {
        String level = value >= 85 ? "danger" : value >= 70 ? "warning" : "healthy";
        return new DashboardResourceVO(name, value, detail, level);
    }
}
