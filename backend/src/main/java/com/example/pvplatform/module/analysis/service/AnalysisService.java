package com.example.pvplatform.module.analysis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.vo.AnalysisReportListItemVO;
import com.example.pvplatform.module.analysis.vo.AnalysisReportVO;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.persistence.entity.*;
import com.example.pvplatform.persistence.mapper.*;
import com.example.pvplatform.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisService {
    private static final double TREND_THRESHOLD = 0.05d;
    private static final double LOW_IRRADIANCE = 200d;

    private final StationPermissionService stationPermissionService;
    private final PvDataMapper pvDataMapper;
    private final WeatherDataMapper weatherDataMapper;
    private final PredictionTaskMapper taskMapper;
    private final PredictionResultMapper resultMapper;
    private final AnalysisReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    public AnalysisService(StationPermissionService stationPermissionService,
                           PvDataMapper pvDataMapper, WeatherDataMapper weatherDataMapper,
                           PredictionTaskMapper taskMapper, PredictionResultMapper resultMapper,
                           AnalysisReportMapper reportMapper, ObjectMapper objectMapper) {
        this.stationPermissionService = stationPermissionService;
        this.pvDataMapper = pvDataMapper;
        this.weatherDataMapper = weatherDataMapper;
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.reportMapper = reportMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnalysisReportVO report(AnalysisRequest request) {
        Long userId = SecurityUtils.requireCurrentUserId();
        PowerStationDO station = stationPermissionService.requireView(request.stationId());
        PredictionTaskDO task = taskMapper.selectById(request.taskId());
        if (task == null || !request.stationId().equals(task.getStationId())
            || (!stationPermissionService.isAdmin() && !userId.equals(task.getUserId()))) {
            throw new BusinessException(404, "预测任务不存在");
        }
        if (!"SUCCESS".equals(task.getStatus())) {
            throw new BusinessException(400, "预测任务尚未成功");
        }

        PvDataDO latestPv = pvDataMapper.selectOne(Wrappers.<PvDataDO>lambdaQuery()
            .eq(PvDataDO::getStationId, request.stationId())
            .isNotNull(PvDataDO::getPowerKw)
            .orderByDesc(PvDataDO::getCollectTime).last("LIMIT 1"));
        List<PvDataDO> pvRows = latestPv == null ? List.of()
            : pvDataMapper.selectList(Wrappers.<PvDataDO>lambdaQuery()
                .eq(PvDataDO::getStationId, request.stationId())
                .isNotNull(PvDataDO::getPowerKw)
                .ge(PvDataDO::getCollectTime, latestPv.getCollectTime().minusMinutes(30))
                .le(PvDataDO::getCollectTime, latestPv.getCollectTime())
                .orderByAsc(PvDataDO::getCollectTime));
        if (pvRows.size() < 2) {
            throw new BusinessException(400, "数据不足，无法生成报告");
        }
        List<PredictionResultDO> predictions = resultMapper.selectList(
            Wrappers.<PredictionResultDO>lambdaQuery().eq(PredictionResultDO::getTaskId, task.getTaskId())
                .orderByAsc(PredictionResultDO::getTimeOffsetMinutes));
        if (request.includePrediction() && predictions.size() < 2) {
            throw new BusinessException(400, "数据不足，无法生成报告");
        }
        WeatherDataDO weather = request.includeWeather() ? weatherDataMapper.selectOne(
            Wrappers.<WeatherDataDO>lambdaQuery().eq(WeatherDataDO::getStationId, request.stationId())
                .orderByDesc(WeatherDataDO::getWeatherTime).last("LIMIT 1")) : null;

        Metrics metrics = calculate(pvRows, predictions);
        String weatherText = weatherAnalysis(weather);
        String predictionText = request.includePrediction() ? predictionAnalysis(metrics) : "未启用预测结果分析。";
        String abnormalText = abnormalAnalysis(pvRows, weather);
        String summary = String.format("%s最近数据平均功率为 %.2f kW，功率%s；%s",
            station.getStationName(), metrics.averagePower, trend(metrics.historyRate), predictionText);
        String suggestion = suggestion(metrics, weather, abnormalText);
        String title = request.title() == null || request.title().isBlank()
            ? station.getStationName() + "综合分析报告" : request.title().trim();

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("averagePowerKw", metrics.averagePower);
        structured.put("minPowerKw", metrics.minPower);
        structured.put("maxPowerKw", metrics.maxPower);
        structured.put("historyChangeRate", metrics.historyRate);
        structured.put("fluctuationRate", metrics.fluctuationRate);
        structured.put("predictionChangeRate", metrics.predictionRate);
        structured.put("sampleCount", pvRows.size());
        structured.put("predictionCount", predictions.size());
        structured.put("weatherAvailable", weather != null);

        AnalysisReportDO row = new AnalysisReportDO();
        row.setUserId(userId);
        row.setStationId(station.getStationId());
        row.setTaskId(task.getTaskId());
        row.setTitle(title);
        row.setSummary(summary);
        row.setWeatherAnalysis(weatherText);
        row.setPredictionAnalysis(predictionText);
        row.setAbnormalAnalysis(abnormalText);
        row.setSuggestion(suggestion);
        row.setReportContent(String.join("\n\n", title, summary, weatherText, predictionText,
            abnormalText, suggestion));
        try {
            row.setReportJson(objectMapper.writeValueAsString(structured));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "报告结构化数据生成失败");
        }
        row.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(row);
        return toVO(row);
    }

    public PageResult<AnalysisReportListItemVO> history(int pageNum, int pageSize, Long stationId) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数不合法");
        }
        Long userId = SecurityUtils.requireCurrentUserId();
        if (stationId != null) {
            stationPermissionService.requireView(stationId);
        }
        var query = Wrappers.<AnalysisReportDO>lambdaQuery()
            .eq(!stationPermissionService.isAdmin(), AnalysisReportDO::getUserId, userId)
            .eq(stationId != null, AnalysisReportDO::getStationId, stationId)
            .orderByDesc(AnalysisReportDO::getCreatedAt);
        Page<AnalysisReportDO> page = reportMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream()
            .map(r -> new AnalysisReportListItemVO(r.getReportId(), r.getStationId(), r.getTaskId(),
                r.getTitle(), r.getSummary(), r.getCreatedAt())).toList());
    }

    public AnalysisReportVO detail(Long reportId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        AnalysisReportDO row = reportMapper.selectOne(Wrappers.<AnalysisReportDO>lambdaQuery()
            .eq(AnalysisReportDO::getReportId, reportId)
            .eq(!stationPermissionService.isAdmin(), AnalysisReportDO::getUserId, userId));
        if (row == null) {
            throw new BusinessException(404, "报告不存在");
        }
        return toVO(row);
    }

    private Metrics calculate(List<PvDataDO> rows, List<PredictionResultDO> predictions) {
        double average = rows.stream().map(PvDataDO::getPowerKw).mapToDouble(BigDecimal::doubleValue)
            .average().orElse(0);
        double min = rows.stream().map(PvDataDO::getPowerKw).mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        double max = rows.stream().map(PvDataDO::getPowerKw).mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        double first = rows.get(0).getPowerKw().doubleValue();
        double last = rows.get(rows.size() - 1).getPowerKw().doubleValue();
        double historyRate = rate(first, last);
        double fluctuation = average == 0 ? 0 : (max - min) / Math.abs(average);
        double predictionRate = 0;
        if (predictions.size() >= 2) {
            predictionRate = rate(predictions.get(0).getPredictPowerKw().doubleValue(),
                predictions.get(predictions.size() - 1).getPredictPowerKw().doubleValue());
        }
        return new Metrics(round(average), round(min), round(max), historyRate, fluctuation, predictionRate);
    }

    private String weatherAnalysis(WeatherDataDO weather) {
        if (weather == null) {
            return "暂无可用天气数据，天气影响结论已降级。";
        }
        double irradianceHint = weather.getCloudinessPercent() == null ? 0 : weather.getCloudinessPercent().doubleValue();
        return String.format("当前天气：%s，温度%s℃，湿度%s%%，云量%s%%。%s",
            value(weather.getWeatherText()), value(weather.getTemperatureC()),
            value(weather.getHumidityPercent()), value(weather.getCloudinessPercent()),
            irradianceHint >= 70 ? "高云量可能限制光伏输出。" : "天气条件未显示明显的高云量限制。");
    }

    private String predictionAnalysis(Metrics metrics) {
        if (metrics.predictionRate > TREND_THRESHOLD) {
            return "未来预测功率呈上升趋势。";
        }
        if (metrics.predictionRate < -TREND_THRESHOLD) {
            return "未来预测功率可能下降，请关注天气和设备状态。";
        }
        return "未来预测功率整体平稳。";
    }

    private String abnormalAnalysis(List<PvDataDO> rows, WeatherDataDO weather) {
        boolean gap = false;
        for (int i = 1; i < rows.size(); i++) {
            if (Duration.between(rows.get(i - 1).getCollectTime(), rows.get(i).getCollectTime()).toMinutes() > 5) {
                gap = true;
                break;
            }
        }
        String quality = gap ? "检测到连续数据断档，报告可信度降低。" : "采样时间序列未发现明显断档。";
        boolean lowIrradiance = rows.stream().map(PvDataDO::getIrradianceWM2)
            .filter(java.util.Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().orElse(1000) < LOW_IRRADIANCE;
        return quality + (lowIrradiance ? " 辐照度偏低，可能限制光伏输出。" : "");
    }

    private String suggestion(Metrics metrics, WeatherDataDO weather, String abnormal) {
        if (abnormal.contains("断档")) {
            return "建议优先检查采集链路和数据完整性，再结合后续数据复核预测结论。";
        }
        if (metrics.predictionRate < -TREND_THRESHOLD) {
            return "建议关注天气变化、组件遮挡和逆变器运行状态。";
        }
        return "建议继续监测功率、辐照度及设备告警，按计划开展巡检。";
    }

    private String trend(double rate) {
        return rate > TREND_THRESHOLD ? "上升" : rate < -TREND_THRESHOLD ? "下降" : "整体平稳";
    }

    private double rate(double first, double last) {
        return Math.abs(first) < 0.000001 ? 0 : (last - first) / Math.abs(first);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Object value(Object value) {
        return value == null ? "未知" : value;
    }

    private AnalysisReportVO toVO(AnalysisReportDO r) {
        return new AnalysisReportVO(r.getReportId(), r.getUserId(), r.getStationId(), r.getTaskId(),
            r.getTitle(), r.getSummary(), r.getWeatherAnalysis(), r.getPredictionAnalysis(),
            r.getAbnormalAnalysis(), r.getSuggestion(), r.getReportContent(), r.getReportJson(),
            r.getCreatedAt());
    }

    private record Metrics(double averagePower, double minPower, double maxPower,
                           double historyRate, double fluctuationRate, double predictionRate) {}
}
