package com.example.pvplatform.module.pvoutput.scheduler;

import com.example.pvplatform.module.pvoutput.config.PvOutputProperties;
import com.example.pvplatform.module.pvoutput.service.PvOutputSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PvOutputScheduler {
    private static final Logger log = LoggerFactory.getLogger(PvOutputScheduler.class);

    private final PvOutputProperties properties;
    private final PvOutputSyncService syncService;

    public PvOutputScheduler(PvOutputProperties properties, PvOutputSyncService syncService) {
        this.properties = properties;
        this.syncService = syncService;
    }

    @Scheduled(
        fixedDelayString = "${pvoutput.sync-interval-ms:600000}",
        initialDelayString = "${pvoutput.initial-delay-ms:30000}"
    )
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
