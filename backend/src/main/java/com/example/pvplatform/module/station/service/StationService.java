package com.example.pvplatform.module.station.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.station.dto.StationRequest;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.mapper.PowerStationMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class StationService {
    private final PowerStationMapper stationMapper;

    public StationService(PowerStationMapper stationMapper) {
        this.stationMapper = stationMapper;
    }

    public List<PowerStation> list() {
        return stationMapper.selectList(Wrappers.<PowerStationDO>lambdaQuery()
                .orderByDesc(PowerStationDO::getCreatedAt))
            .stream().map(this::toEntity).toList();
    }

    public PowerStation detail(Long stationId) {
        PowerStationDO station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(404, "电站不存在");
        }
        return toEntity(station);
    }

    public Long create(StationRequest request) {
        PowerStationDO station = fromRequest(request);
        station.setStationCode("PV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        station.setStatus(request.status() == null ? "RUNNING" : request.status());
        stationMapper.insert(station);
        return station.getStationId();
    }

    public void update(Long stationId, StationRequest request) {
        if (stationMapper.selectById(stationId) == null) {
            throw new BusinessException(404, "电站不存在");
        }
        PowerStationDO station = fromRequest(request);
        station.setStationId(stationId);
        stationMapper.updateById(station);
    }

    public void delete(Long stationId) {
        if (stationMapper.deleteById(stationId) == 0) {
            throw new BusinessException(404, "电站不存在");
        }
    }

    private PowerStationDO fromRequest(StationRequest request) {
        PowerStationDO station = new PowerStationDO();
        station.setStationName(request.stationName());
        station.setProvince(request.province());
        station.setCity(request.city());
        station.setAddress(request.address());
        station.setLongitude(decimal(request.longitude()));
        station.setLatitude(decimal(request.latitude()));
        station.setCapacityKw(decimal(request.capacity()));
        station.setStatus(request.status());
        station.setDescription(request.description());
        return station;
    }

    private PowerStation toEntity(PowerStationDO station) {
        return new PowerStation(station.getStationId(), station.getStationName(), station.getProvince(),
            station.getCity(), station.getAddress(), number(station.getLongitude()), number(station.getLatitude()),
            number(station.getCapacityKw()), station.getStatus(), station.getDescription());
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private Double number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
