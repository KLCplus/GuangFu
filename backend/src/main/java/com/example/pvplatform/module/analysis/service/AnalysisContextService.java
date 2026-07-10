package com.example.pvplatform.module.analysis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.llm.LlmProperties;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.PredictionResultDO;
import com.example.pvplatform.persistence.entity.PredictionTaskDO;
import com.example.pvplatform.persistence.entity.PvDataDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.entity.WeatherDataDO;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.example.pvplatform.persistence.mapper.PredictionResultMapper;
import com.example.pvplatform.persistence.mapper.PredictionTaskMapper;
import com.example.pvplatform.persistence.mapper.PvDataMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.WeatherDataMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisContextService {
    private final StationPermissionService stationPermissionService;
    private final SysUserMapper userMapper;
    private final PvDataMapper pvDataMapper;
    private final WeatherDataMapper weatherDataMapper;
    private final PredictionTaskMapper taskMapper;
    private final PredictionResultMapper resultMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final LlmProperties llmProperties;

    public AnalysisContextService(StationPermissionService stationPermissionService,
                                  SysUserMapper userMapper,
                                  PvDataMapper pvDataMapper,
                                  WeatherDataMapper weatherDataMapper,
                                  PredictionTaskMapper taskMapper,
                                  PredictionResultMapper resultMapper,
                                  ModelInfoMapper modelInfoMapper,
                                  LlmProperties llmProperties) {
        this.stationPermissionService = stationPermissionService;
        this.userMapper = userMapper;
        this.pvDataMapper = pvDataMapper;
        this.weatherDataMapper = weatherDataMapper;
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.llmProperties = llmProperties;
    }

    public AnalysisContext build(AnalysisRequest request) {
        Long userId = SecurityUtils.requireCurrentUserId();
        PowerStationDO station = stationPermissionService.requireView(request.stationId());
        SysUserDO user = userMapper.selectById(userId);
        PredictionTaskDO task = resolveTask(request, userId);
        ModelInfoDO model = task == null ? null : modelInfoMapper.selectById(task.getModelId());
        List<PredictionResultDO> predictions = task == null ? List.of() : resultMapper.selectList(
            Wrappers.<PredictionResultDO>lambdaQuery()
                .eq(PredictionResultDO::getTaskId, task.getTaskId())
                .orderByAsc(PredictionResultDO::getTimeOffsetMinutes));
        WeatherDataDO weather = request.includeWeather() ? weatherDataMapper.selectOne(
            Wrappers.<WeatherDataDO>lambdaQuery()
                .eq(WeatherDataDO::getStationId, request.stationId())
                .orderByDesc(WeatherDataDO::getWeatherTime)
                .last("LIMIT 1")) : null;
        List<PvDataDO> powerRows = latestPowerRows(request.stationId());

        Map<String, Object> userContext = map();
        put(userContext, "userId", userId);
        put(userContext, "username", user == null ? null : user.getUsername());
        put(userContext, "nickname", user == null ? null : user.getNickname());

        Map<String, Object> stationContext = stationContext(station);
        Map<String, Object> weatherContext = weatherContext(weather, request.includeWeather());
        Map<String, Object> predictionContext = predictionContext(task, model, predictions, request.includePrediction());
        Map<String, Object> powerSummary = powerSummary(powerRows);
        Map<String, Object> platformContext = platformContext(task, predictions);

        return new AnalysisContext(userId, userContext, station, task, model, weather, powerRows, predictions,
            stationContext, weatherContext, predictionContext, powerSummary, platformContext,
            request.includeWeather(), request.includePrediction());
    }

    private PredictionTaskDO resolveTask(AnalysisRequest request, Long userId) {
        if (request.taskId() != null) {
            PredictionTaskDO task = taskMapper.selectById(request.taskId());
            if (task == null || !request.stationId().equals(task.getStationId())
                || (!stationPermissionService.isAdmin() && !userId.equals(task.getUserId()))) {
                throw new BusinessException(404, "预测任务不存在");
            }
            return task;
        }
        if (!request.includePrediction()) {
            return null;
        }
        return taskMapper.selectOne(Wrappers.<PredictionTaskDO>lambdaQuery()
            .eq(PredictionTaskDO::getStationId, request.stationId())
            .eq(PredictionTaskDO::getStatus, "SUCCESS")
            .eq(!stationPermissionService.isAdmin(), PredictionTaskDO::getUserId, userId)
            .orderByDesc(PredictionTaskDO::getCreatedAt)
            .last("LIMIT 1"));
    }

    private List<PvDataDO> latestPowerRows(Long stationId) {
        PvDataDO latest = pvDataMapper.selectOne(Wrappers.<PvDataDO>lambdaQuery()
            .eq(PvDataDO::getStationId, stationId)
            .isNotNull(PvDataDO::getPowerKw)
            .orderByDesc(PvDataDO::getCollectTime)
            .last("LIMIT 1"));
        if (latest == null || latest.getCollectTime() == null) {
            return List.of();
        }
        return pvDataMapper.selectList(Wrappers.<PvDataDO>lambdaQuery()
            .eq(PvDataDO::getStationId, stationId)
            .isNotNull(PvDataDO::getPowerKw)
            .ge(PvDataDO::getCollectTime, latest.getCollectTime().minusMinutes(30))
            .le(PvDataDO::getCollectTime, latest.getCollectTime())
            .orderByAsc(PvDataDO::getCollectTime));
    }

    private Map<String, Object> stationContext(PowerStationDO station) {
        Map<String, Object> context = map();
        put(context, "stationId", station.getStationId());
        put(context, "stationCode", station.getStationCode());
        put(context, "stationName", station.getStationName());
        put(context, "province", station.getProvince());
        put(context, "city", station.getCity());
        put(context, "district", station.getDistrict());
        put(context, "address", station.getAddress());
        put(context, "capacityKw", station.getCapacityKw());
        put(context, "longitude", station.getLongitude());
        put(context, "latitude", station.getLatitude());
        put(context, "status", station.getStatus());
        put(context, "description", station.getDescription());
        return context;
    }

    private Map<String, Object> weatherContext(WeatherDataDO weather, boolean requested) {
        Map<String, Object> context = map();
        put(context, "requested", requested);
        put(context, "available", weather != null);
        if (weather == null) {
            put(context, "note", requested ? "当前上下文无可用天气数据" : "用户未请求天气分析");
            return context;
        }
        put(context, "weatherTime", weather.getWeatherTime());
        put(context, "weatherText", weather.getWeatherText());
        put(context, "temperatureC", weather.getTemperatureC());
        put(context, "humidityPercent", weather.getHumidityPercent());
        put(context, "windDirection", weather.getWindDirection());
        put(context, "windPower", weather.getWindPower());
        put(context, "windSpeedMS", weather.getWindSpeedMS());
        put(context, "precipitationMm", weather.getPrecipitationMm());
        put(context, "cloudinessPercent", weather.getCloudinessPercent());
        put(context, "source", weather.getSource());
        return context;
    }

    private Map<String, Object> predictionContext(PredictionTaskDO task, ModelInfoDO model,
                                                  List<PredictionResultDO> predictions, boolean requested) {
        Map<String, Object> context = map();
        put(context, "requested", requested);
        put(context, "available", task != null && !predictions.isEmpty());
        if (task == null) {
            put(context, "note", requested ? "当前上下文无可用预测任务" : "用户未请求预测分析");
            return context;
        }
        put(context, "taskId", task.getTaskId());
        put(context, "taskNo", task.getTaskNo());
        put(context, "status", task.getStatus());
        put(context, "inputMode", task.getInputMode());
        put(context, "predictStartTime", task.getPredictStartTime());
        put(context, "predictEndTime", task.getPredictEndTime());
        put(context, "costTimeMs", task.getCostTimeMs());
        put(context, "modelName", model == null ? null : model.getModelName());
        put(context, "modelCode", model == null ? null : model.getModelCode());
        put(context, "predictionCount", predictions.size());
        put(context, "predictions", predictions.stream().map(item -> {
            Map<String, Object> row = map();
            put(row, "timeOffsetMinutes", item.getTimeOffsetMinutes());
            put(row, "predictTime", item.getPredictTime());
            put(row, "predictPowerKw", item.getPredictPowerKw());
            put(row, "actualPowerKw", item.getActualPowerKw());
            put(row, "errorRate", item.getErrorRate());
            return row;
        }).toList());
        return context;
    }

    private Map<String, Object> powerSummary(List<PvDataDO> rows) {
        Map<String, Object> summary = map();
        put(summary, "sampleCount", rows.size());
        if (rows.isEmpty()) {
            put(summary, "available", false);
            put(summary, "note", "当前上下文无可用历史功率数据");
            return summary;
        }
        double average = rows.stream().map(PvDataDO::getPowerKw).mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double min = rows.stream().map(PvDataDO::getPowerKw).mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        double max = rows.stream().map(PvDataDO::getPowerKw).mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        double first = rows.get(0).getPowerKw().doubleValue();
        double last = rows.get(rows.size() - 1).getPowerKw().doubleValue();
        boolean hasGap = false;
        for (int i = 1; i < rows.size(); i++) {
            if (Duration.between(rows.get(i - 1).getCollectTime(), rows.get(i).getCollectTime()).toMinutes() > 5) {
                hasGap = true;
                break;
            }
        }
        put(summary, "available", true);
        put(summary, "startTime", rows.get(0).getCollectTime());
        put(summary, "endTime", rows.get(rows.size() - 1).getCollectTime());
        put(summary, "averagePowerKw", round(average));
        put(summary, "minPowerKw", round(min));
        put(summary, "maxPowerKw", round(max));
        put(summary, "changeRate", roundRate(first, last));
        put(summary, "hasTimeGap", hasGap);
        return summary;
    }

    private Map<String, Object> platformContext(PredictionTaskDO task, List<PredictionResultDO> predictions) {
        Map<String, Object> context = map();
        put(context, "llmEnabled", llmProperties.isEnabled());
        put(context, "llmProvider", llmProperties.getProvider());
        put(context, "llmModel", llmProperties.getModel());
        put(context, "predictionTaskStatus", task == null ? null : task.getStatus());
        put(context, "predictionCostTimeMs", task == null ? null : task.getCostTimeMs());
        put(context, "predictionResultCount", predictions.size());
        return context;
    }

    private Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double roundRate(double first, double last) {
        if (Math.abs(first) < 0.000001) {
            return 0;
        }
        return BigDecimal.valueOf((last - first) / Math.abs(first)).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
