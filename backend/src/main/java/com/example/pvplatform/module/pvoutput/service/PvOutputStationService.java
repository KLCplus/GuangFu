package com.example.pvplatform.module.pvoutput.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.pvoutput.client.PvOutputClient;
import com.example.pvplatform.module.pvoutput.dto.PvOutputStationDTO;
import com.example.pvplatform.module.pvoutput.dto.PvOutputStationSaveRequest;
import com.example.pvplatform.module.pvoutput.dto.PvOutputStationSearchResultDTO;
import com.example.pvplatform.module.pvoutput.dto.PvOutputStatusDTO;
import com.example.pvplatform.module.pvoutput.util.PvOutputCsvParser;
import com.example.pvplatform.persistence.entity.ExternalPvStationDO;
import com.example.pvplatform.persistence.entity.ExternalPvStationStatusDO;
import com.example.pvplatform.persistence.mapper.ExternalPvStationMapper;
import com.example.pvplatform.persistence.mapper.ExternalPvStationStatusMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PvOutputStationService {
    private final PvOutputClient client;
    private final PvOutputCsvParser csvParser;
    private final ExternalPvStationMapper stationMapper;
    private final ExternalPvStationStatusMapper statusMapper;

    public PvOutputStationService(PvOutputClient client,
                                  PvOutputCsvParser csvParser,
                                  ExternalPvStationMapper stationMapper,
                                  ExternalPvStationStatusMapper statusMapper) {
        this.client = client;
        this.csvParser = csvParser;
        this.stationMapper = stationMapper;
        this.statusMapper = statusMapper;
    }

    public List<PvOutputStationSearchResultDTO> search(String keyword, String countryCode, int seenDays) {
        String q = keyword == null || keyword.isBlank() ? "Enphase" : keyword.trim();
        String country = countryCode == null || countryCode.isBlank() ? "au" : countryCode.trim();
        int seen = seenDays <= 0 ? 7 : seenDays;
        return csvParser.parseStations(client.searchStations(q, country, seen));
    }

    public PvOutputStationDTO save(PvOutputStationSaveRequest request) {
        if (request.externalSystemId() == null) {
            throw new BusinessException(400, "externalSystemId 不能为空");
        }
        ExternalPvStationDO station = stationMapper.selectOne(Wrappers.<ExternalPvStationDO>lambdaQuery()
            .eq(ExternalPvStationDO::getExternalSystemId, request.externalSystemId())
            .last("LIMIT 1"));
        if (station == null) {
            station = new ExternalPvStationDO();
            station.setSource("PVOUTPUT");
            station.setExternalSystemId(request.externalSystemId());
        }
        fillStation(station, request);
        station.setEnabled(true);
        if (station.getId() == null) {
            stationMapper.insert(station);
        } else {
            stationMapper.updateById(station);
        }
        return toStationDTO(stationMapper.selectById(station.getId()));
    }

    public List<PvOutputStationDTO> list(Boolean enabled, String keyword) {
        var query = Wrappers.<ExternalPvStationDO>lambdaQuery();
        if (enabled != null) {
            query.eq(ExternalPvStationDO::getEnabled, enabled);
        }
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            query.and(q -> q.like(ExternalPvStationDO::getSystemName, term)
                .or().like(ExternalPvStationDO::getPostcode, term)
                .or().like(ExternalPvStationDO::getPanel, term)
                .or().like(ExternalPvStationDO::getInverter, term));
        }
        query.orderByDesc(ExternalPvStationDO::getUpdatedAt);
        return stationMapper.selectList(query).stream().map(this::toStationDTO).toList();
    }

    public PvOutputStationDTO detail(Long id) {
        return toStationDTO(requireStation(id));
    }

    public PvOutputStationDTO setEnabled(Long id, boolean enabled) {
        ExternalPvStationDO station = requireStation(id);
        station.setEnabled(enabled);
        stationMapper.updateById(station);
        return toStationDTO(stationMapper.selectById(id));
    }

    public ExternalPvStationDO requireStation(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "公开电站 ID 不合法");
        }
        ExternalPvStationDO station = stationMapper.selectById(id);
        if (station == null) {
            throw new BusinessException(404, "公开电站不存在");
        }
        return station;
    }

    public PvOutputStatusDTO latestStatus(Long stationId) {
        ExternalPvStationDO station = requireStation(stationId);
        requireExternalSystemId(station);
        ExternalPvStationStatusDO status = statusMapper.selectOne(Wrappers.<ExternalPvStationStatusDO>lambdaQuery()
            .eq(ExternalPvStationStatusDO::getExternalSystemId, station.getExternalSystemId())
            .orderByDesc(ExternalPvStationStatusDO::getSampleTime)
            .last("LIMIT 1"));
        return status == null ? null : toStatusDTO(status);
    }

    public List<PvOutputStatusDTO> history(Long stationId, LocalDateTime startTime, LocalDateTime endTime) {
        ExternalPvStationDO station = requireStation(stationId);
        requireExternalSystemId(station);
        LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime start = startTime == null ? end.minusDays(7) : startTime;
        return statusMapper.selectList(Wrappers.<ExternalPvStationStatusDO>lambdaQuery()
                .eq(ExternalPvStationStatusDO::getExternalSystemId, station.getExternalSystemId())
                .ge(ExternalPvStationStatusDO::getSampleTime, start)
                .le(ExternalPvStationStatusDO::getSampleTime, end)
                .orderByAsc(ExternalPvStationStatusDO::getSampleTime))
            .stream().map(this::toStatusDTO).toList();
    }

    private void requireExternalSystemId(ExternalPvStationDO station) {
        if (station.getExternalSystemId() == null) {
            throw new BusinessException(400, "公开电站缺少外部系统 ID，无法读取状态数据");
        }
    }

    private void fillStation(ExternalPvStationDO station, PvOutputStationSaveRequest request) {
        station.setSystemName(request.systemName());
        station.setSystemSizeW(request.systemSizeW());
        station.setPostcode(request.postcode());
        station.setOrientation(request.orientation());
        station.setOutputs(request.outputs());
        station.setLastOutputText(request.lastOutputText());
        station.setPanel(request.panel());
        station.setInverter(request.inverter());
        station.setDistanceKm(request.distanceKm());
        station.setLatitude(request.latitude());
        station.setLongitude(request.longitude());
    }

    PvOutputStationDTO toStationDTO(ExternalPvStationDO station) {
        return new PvOutputStationDTO(station.getId(), station.getSource(), station.getExternalSystemId(),
            station.getSystemName(), station.getSystemSizeW(), station.getPostcode(), station.getOrientation(),
            station.getOutputs(), station.getLastOutputText(), station.getPanel(), station.getInverter(),
            station.getDistanceKm(), station.getLatitude(), station.getLongitude(), station.getEnabled(),
            station.getLastSyncTime(), station.getLastSyncStatus(), station.getLastSyncError());
    }

    PvOutputStatusDTO toStatusDTO(ExternalPvStationStatusDO status) {
        return new PvOutputStatusDTO(status.getId(), status.getExternalSystemId(), status.getSampleTime(),
            status.getEnergyGenerationWh(), status.getPowerGenerationW(), status.getEnergyConsumptionWh(),
            status.getPowerConsumptionW(), status.getNormalisedOutput(), status.getTemperatureC(),
            status.getVoltageV(), status.getFetchedAt());
    }
}
