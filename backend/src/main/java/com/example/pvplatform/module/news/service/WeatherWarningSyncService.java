package com.example.pvplatform.module.news.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.module.weather.client.QWeatherProvider;
import com.example.pvplatform.module.weather.client.WeatherWarningResult;
import com.example.pvplatform.persistence.entity.NewsDO;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.mapper.NewsMapper;
import com.example.pvplatform.persistence.mapper.PowerStationMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WeatherWarningSyncService {
    private final PowerStationMapper stationMapper;
    private final NewsMapper newsMapper;
    private final QWeatherProvider provider;
    public WeatherWarningSyncService(PowerStationMapper stationMapper, NewsMapper newsMapper, QWeatherProvider provider) {
        this.stationMapper = stationMapper; this.newsMapper = newsMapper; this.provider = provider;
    }

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
    public ExternalNewsSyncService.SyncStats sync() {
        int parsed=0, inserted=0, updated=0, duplicate=0, skipped=0, failed=0;
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
            } catch (Exception exception) { failed++; }
        }
        return new ExternalNewsSyncService.SyncStats(parsed,inserted,updated,duplicate,skipped,failed);
    }
    private String join(String first, String second) { return ((first==null?"":first.trim()) + (second==null||second.isBlank()?"":"\n防御指南："+second.trim())).trim(); }
    private String abbreviate(String value, int max) { return value.length()<=max?value:value.substring(0,max)+"…"; }
}
