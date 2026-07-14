package com.example.pvplatform.module.news.scheduler;

import com.example.pvplatform.module.news.config.NewsSyncProperties;
import com.example.pvplatform.module.news.service.ExternalNewsSyncService;
import com.example.pvplatform.module.news.service.WeatherWarningSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsSyncScheduler {
    private final NewsSyncProperties properties;
    private final ExternalNewsSyncService service;
    private final WeatherWarningSyncService weatherService;
    public NewsSyncScheduler(NewsSyncProperties properties, ExternalNewsSyncService service, WeatherWarningSyncService weatherService) { this.properties = properties; this.service = service; this.weatherService = weatherService; }

    @Scheduled(cron = "${news.sync.web-cron:0 20 */6 * * *}")
    public void syncWebSources() {
        if (properties.isEnabled()) service.syncConfiguredSources();
    }

    @Scheduled(cron = "${news.sync.weather-cron:0 */20 * * * *}")
    public void syncWeatherWarnings() {
        if (properties.isEnabled() && properties.isWeatherEnabled()) weatherService.sync();
    }
}
