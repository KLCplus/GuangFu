package com.example.pvplatform.module.analysis.service;

import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.PredictionResultDO;
import com.example.pvplatform.persistence.entity.PredictionTaskDO;
import com.example.pvplatform.persistence.entity.PvDataDO;
import com.example.pvplatform.persistence.entity.WeatherDataDO;

import java.util.List;
import java.util.Map;

public record AnalysisContext(
    Long userId,
    Map<String, Object> user,
    PowerStationDO station,
    PredictionTaskDO task,
    ModelInfoDO model,
    WeatherDataDO weather,
    List<PvDataDO> powerRows,
    List<PredictionResultDO> predictionResults,
    Map<String, Object> stationContext,
    Map<String, Object> weatherContext,
    Map<String, Object> predictionContext,
    Map<String, Object> powerSummary,
    Map<String, Object> platformContext,
    boolean includeWeather,
    boolean includePrediction
) {}
