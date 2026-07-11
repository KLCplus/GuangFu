package com.example.pvplatform.module.pvoutput.scheduler;

import com.example.pvplatform.module.pvoutput.config.PvOutputProperties;
import com.example.pvplatform.module.pvoutput.service.PvOutputSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class PvOutputScheduler implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PvOutputScheduler.class);

    private final PvOutputProperties properties;
    private final PvOutputSyncService syncService;

    public PvOutputScheduler(PvOutputProperties properties, PvOutputSyncService syncService) {
        this.properties = properties;
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("PVOutput sync skipped on startup because it is disabled");
            return;
        }
        if (!properties.hasCredentials()) {
            log.info("PVOutput API credentials are not configured, syncing public live.jsp snapshots instead");
        }
        try {
            int count = syncService.syncAllEnabled().size();
            if (count == 0) {
                log.info("PVOutput startup sync skipped because no enabled external stations exist");
            }
        } catch (Exception ex) {
            log.warn("PVOutput startup sync failed without stopping application: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${pvoutput.sync-interval-ms:600000}")
    public void scheduledSync() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            syncService.syncAllEnabled();
        } catch (Exception ex) {
            log.warn("PVOutput scheduled sync failed: {}", ex.getMessage());
        }
    }
}
