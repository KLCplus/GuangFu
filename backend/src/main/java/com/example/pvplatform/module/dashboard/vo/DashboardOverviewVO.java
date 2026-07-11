package com.example.pvplatform.module.dashboard.vo;

import com.example.pvplatform.module.pvdata.entity.PvData;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.module.weather.vo.CurrentWeatherVO;
import com.example.pvplatform.module.weather.vo.WeatherForecastVO;

import java.util.List;

public record DashboardOverviewVO(
    List<PowerStation> stations,
    Long selectedStationId,
    CurrentWeatherVO weather,
    List<WeatherForecastVO> forecasts,
    PvData realtime,
    List<DashboardResourceVO> resources,
    String dataSource,
    String lastUpdate
) {}
