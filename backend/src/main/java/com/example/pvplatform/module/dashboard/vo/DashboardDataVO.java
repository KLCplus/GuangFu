package com.example.pvplatform.module.dashboard.vo;

import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.weather.vo.WeatherForecastVO;

import java.util.List;
import java.util.Map;

public record DashboardDataVO(
    PowerStation station,
    String snapshotTime,
    String dataSource,
    String quality,
    Realtime realtime,
    Weather weather,
    EnergyFlow energyFlow,
    List<HistoryPoint> history,
    Infrastructure infrastructure,
    Map<String, String> sources
) {
    public record Realtime(
        Long stationId,
        String snapshotTime,
        Double pvPowerKw,
        Double loadPowerKw,
        Double gridPowerKw,
        Double batteryPowerKw,
        Double batterySoc,
        Double voltageV,
        Double currentA,
        Double irradianceWm2,
        Double temperatureC,
        Double humidityPercent,
        Double windSpeedMs,
        String dataSource,
        String quality
    ) {}

    public record Weather(
        String text,
        Double temperatureC,
        Double humidityPercent,
        String windDirection,
        String windScale,
        Double windSpeedMs,
        String updateTime,
        String dataSource,
        boolean cached,
        List<WeatherForecastVO> forecast
    ) {}

    public record EnergyFlow(
        Double pvPowerKw,
        Double loadPowerKw,
        Double gridPowerKw,
        Double batteryPowerKw,
        Double batterySoc,
        Double systemLossKw,
        String gridDirection,
        String batteryDirection
    ) {}

    public record HistoryPoint(
        String time,
        Double pvPowerKw,
        Double loadPowerKw,
        Double gridPowerKw,
        Double predictionPowerKw,
        Double irradianceWm2
    ) {}

    public record Infrastructure(
        String backendStatus,
        String databaseStatus,
        String modelServiceStatus,
        String queueStatus,
        String checkedAt,
        String dataSource
    ) {}
}
