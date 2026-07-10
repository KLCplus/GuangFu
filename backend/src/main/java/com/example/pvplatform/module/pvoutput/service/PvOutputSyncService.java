package com.example.pvplatform.module.pvoutput.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.pvoutput.client.PvOutputClient;
import com.example.pvplatform.module.pvoutput.config.PvOutputProperties;
import com.example.pvplatform.module.pvoutput.dto.PvOutputSyncResultDTO;
import com.example.pvplatform.module.pvoutput.util.PvOutputCsvParser;
import com.example.pvplatform.persistence.entity.ExternalPvStationDO;
import com.example.pvplatform.persistence.entity.ExternalPvStationStatusDO;
import com.example.pvplatform.persistence.mapper.ExternalPvStationMapper;
import com.example.pvplatform.persistence.mapper.ExternalPvStationStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PvOutputSyncService {
    private static final Logger log = LoggerFactory.getLogger(PvOutputSyncService.class);

    private final PvOutputProperties properties;
    private final PvOutputClient client;
    private final PvOutputCsvParser csvParser;
    private final PvOutputStationService stationService;
    private final ExternalPvStationMapper stationMapper;
    private final ExternalPvStationStatusMapper statusMapper;

    public PvOutputSyncService(PvOutputProperties properties,
                               PvOutputClient client,
                               PvOutputCsvParser csvParser,
                               PvOutputStationService stationService,
                               ExternalPvStationMapper stationMapper,
                               ExternalPvStationStatusMapper statusMapper) {
        this.properties = properties;
        this.client = client;
        this.csvParser = csvParser;
        this.stationService = stationService;
        this.stationMapper = stationMapper;
        this.statusMapper = statusMapper;
    }

    public PvOutputSyncResultDTO syncOne(Long stationId) {
        ExternalPvStationDO station = stationService.requireStation(stationId);
        return syncStation(station);
    }

    public List<PvOutputSyncResultDTO> syncAllEnabled() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        List<ExternalPvStationDO> stations = stationMapper.selectList(Wrappers.<ExternalPvStationDO>lambdaQuery()
            .eq(ExternalPvStationDO::getEnabled, true)
            .orderByAsc(ExternalPvStationDO::getId));
        if (stations.size() > 6) {
            log.warn("PVOutput enabled station count {} may exceed free API hourly quota with 10 minute sync interval", stations.size());
        }
        List<PvOutputSyncResultDTO> results = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) {
            results.add(syncStation(stations.get(i)));
            if (i < stations.size() - 1) {
                sleepQuietly();
            }
        }
        return results;
    }

    private PvOutputSyncResultDTO syncStation(ExternalPvStationDO station) {
        try {
            String payload = client.getStatus(station.getExternalSystemId());
            ExternalPvStationStatusDO status = csvParser.parseStatus(payload, station.getExternalSystemId());
            statusMapper.upsert(status);
            station.setLastSyncTime(LocalDateTime.now());
            station.setLastSyncStatus("SUCCESS");
            station.setLastSyncError(null);
            stationMapper.updateById(station);
            ExternalPvStationStatusDO latest = statusMapper.selectOne(Wrappers.<ExternalPvStationStatusDO>lambdaQuery()
                .eq(ExternalPvStationStatusDO::getExternalSystemId, station.getExternalSystemId())
                .orderByDesc(ExternalPvStationStatusDO::getSampleTime)
                .last("LIMIT 1"));
            return new PvOutputSyncResultDTO(station.getId(), station.getExternalSystemId(), station.getSystemName(),
                "SUCCESS", "同步成功", stationService.toStatusDTO(latest));
        } catch (Exception ex) {
            String message = userMessage(ex);
            station.setLastSyncTime(LocalDateTime.now());
            station.setLastSyncStatus("FAILED");
            station.setLastSyncError(message.length() > 512 ? message.substring(0, 512) : message);
            stationMapper.updateById(station);
            log.warn("PVOutput station sync failed, stationId={}, externalSystemId={}, reason={}",
                station.getId(), station.getExternalSystemId(), message);
            return new PvOutputSyncResultDTO(station.getId(), station.getExternalSystemId(), station.getSystemName(),
                "FAILED", message, null);
        }
    }

    private String userMessage(Exception ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        return ex.getMessage() == null || ex.getMessage().isBlank() ? "PVOutput 同步失败" : ex.getMessage();
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
