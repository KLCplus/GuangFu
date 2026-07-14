package com.example.pvplatform.module.news.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.module.weather.client.QWeatherProvider;
import com.example.pvplatform.module.weather.client.WeatherWarningResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.NewsDO;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.mapper.NewsMapper;
import com.example.pvplatform.persistence.mapper.PowerStationMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WeatherWarningSyncService {
    private static final Logger log = LoggerFactory.getLogger(WeatherWarningSyncService.class);
    private final PowerStationMapper stationMapper;
    private final NewsMapper newsMapper;
    private final QWeatherProvider provider;
    public WeatherWarningSyncService(PowerStationMapper stationMapper, NewsMapper newsMapper, QWeatherProvider provider) {
        this.stationMapper = stationMapper; this.newsMapper = newsMapper; this.provider = provider;
    }

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
    public WeatherSyncResult sync() {
        int parsed=0, inserted=0, updated=0, duplicate=0, skipped=0, failed=0;
        int permissionDenied=0, configurationErrors=0, networkErrors=0;
        String lastError = null;
        List<PowerStationDO> stations = stationMapper.selectList(Wrappers.<PowerStationDO>lambdaQuery()
            .eq(PowerStationDO::getStatus, "RUNNING").isNotNull(PowerStationDO::getLongitude).isNotNull(PowerStationDO::getLatitude));
        Set<String> coordinates = new HashSet<>();
        for (PowerStationDO station : stations) {
            String coordinate = station.getLongitude().setScale(2, RoundingMode.HALF_UP) + "," + station.getLatitude().setScale(2, RoundingMode.HALF_UP);
            if (!coordinates.add(coordinate)) { skipped++; continue; }
            try {
                List<WeatherWarningResult> warnings = provider.getWarnings(station.getLongitude().doubleValue(), station.getLatitude().doubleValue());
                parsed += warnings.size();
                for (WeatherWarningResult warning : warnings) {
                    NewsDO existing = newsMapper.selectOne(Wrappers.<NewsDO>lambdaQuery()
                        .eq(NewsDO::getSourceType, "WEATHER_API").eq(NewsDO::getExternalId, warning.id()).last("LIMIT 1"));
                    String content = join(warning.text(), warning.instruction());
                    boolean cancelled = "CANCEL".equalsIgnoreCase(warning.status()) || "取消".equals(warning.status());
                    if (existing == null) {
                        NewsDO row = new NewsDO(); row.setTitle(warning.title()); row.setSummary(abbreviate(content, 260)); row.setContent(content);
                        row.setNewsType("NEWS"); row.setCategory("WEATHER_ALERT"); row.setContentType("WEATHER_WARNING");
                        row.setSourceType("WEATHER_API"); row.setSourceName("和风天气"); row.setExternalId(warning.id()); row.setExternalContent(1);
                        row.setWarningLevel(join(warning.typeName(), warning.severity())); row.setWarningRegion(station.getProvince()+station.getCity()+station.getDistrict()+"（"+coordinate+"）");
                        row.setWarningAgency(warning.sender()); row.setEffectiveAt(warning.effectiveAt()); row.setExpiresAt(warning.expiresAt());
                        row.setSourcePublishedAt(warning.publishedAt()); row.setFetchedAt(LocalDateTime.now()); row.setTargetRole("ALL");
                        row.setStatus(cancelled ? "OFFLINE" : "PUBLISHED"); row.setPublishedAt(warning.publishedAt()==null?LocalDateTime.now():warning.publishedAt());
                        row.setCreatedAt(LocalDateTime.now()); row.setUpdatedAt(LocalDateTime.now()); row.setDeleted(0); newsMapper.insert(row); inserted++;
                    } else {
                        boolean same = content.equals(existing.getContent()) && (cancelled ? "OFFLINE" : "PUBLISHED").equals(existing.getStatus());
                        existing.setTitle(warning.title()); existing.setSummary(abbreviate(content,260)); existing.setContent(content);
                        existing.setWarningLevel(join(warning.typeName(), warning.severity())); existing.setWarningAgency(warning.sender());
                        existing.setEffectiveAt(warning.effectiveAt()); existing.setExpiresAt(warning.expiresAt()); existing.setFetchedAt(LocalDateTime.now());
                        existing.setStatus(cancelled ? "OFFLINE" : "PUBLISHED"); existing.setUpdatedAt(LocalDateTime.now()); newsMapper.updateById(existing);
                        if (same) duplicate++; else updated++;
                    }
                }
            } catch (Exception exception) {
                failed++;
                lastError = exception.getMessage();
                FailureKind kind = classify(exception);
                if (kind == FailureKind.PERMISSION_DENIED) permissionDenied++;
                else if (kind == FailureKind.CONFIGURATION) configurationErrors++;
                else networkErrors++;
                log.warn("天气预警同步失败 stationId={} coordinate={} reason={}",
                    station.getStationId(), coordinate, exception.getMessage());
            }
        }
        log.info("天气预警同步完成 stations={} coordinates={} parsed={} inserted={} updated={} duplicate={} skipped={} failed={}",
            stations.size(), coordinates.size(), parsed, inserted, updated, duplicate, skipped, failed);
        String status;
        String message;
        if (coordinates.isEmpty()) {
            status = "NO_STATIONS";
            message = "没有可用于查询预警的已启用电站坐标";
        } else if (permissionDenied > 0) {
            status = "PERMISSION_DENIED";
            message = "QWeather 预警数据服务权限不足；普通实时天气不受影响";
        } else if (configurationErrors > 0) {
            status = "CONFIG_MISSING";
            message = "QWeather 预警同步配置不完整";
        } else if (networkErrors > 0 && parsed == 0) {
            status = "NETWORK_ERROR";
            message = "QWeather 预警接口网络请求失败";
        } else if (failed > 0) {
            status = "PARTIAL_SUCCESS";
            message = "部分电站预警同步失败，其他结果已正常处理";
        } else if (parsed == 0) {
            status = "NO_ACTIVE_WARNING";
            message = "请求成功，当前没有正在生效的气象预警";
        } else {
            status = "SUCCESS";
            message = "气象预警同步完成";
        }
        return new WeatherSyncResult(status, message, stations.size(), coordinates.size(), parsed, inserted,
            updated, duplicate, skipped, failed, permissionDenied, configurationErrors, networkErrors, lastError);
    }

    private FailureKind classify(Exception exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.contains("403") || message.contains("no permission") || message.contains("permission denied") || message.contains("权限")) {
            return FailureKind.PERMISSION_DENIED;
        }
        if (message.contains("未配置") || message.contains("凭证") || message.contains("私钥")
            || (exception instanceof BusinessException business && business.getCode() == 500)) {
            return FailureKind.CONFIGURATION;
        }
        return FailureKind.NETWORK;
    }

    private enum FailureKind { PERMISSION_DENIED, CONFIGURATION, NETWORK }
    public record WeatherSyncResult(String status, String message, int stations, int coordinates,
                                    int parsed, int inserted, int updated, int duplicate, int skipped, int failed,
                                    int permissionDenied, int configurationErrors, int networkErrors, String detail) {}
    private String join(String first, String second) { return ((first==null?"":first.trim()) + (second==null||second.isBlank()?"":"\n防御指南："+second.trim())).trim(); }
    private String abbreviate(String value, int max) { return value.length()<=max?value:value.substring(0,max)+"…"; }
}
