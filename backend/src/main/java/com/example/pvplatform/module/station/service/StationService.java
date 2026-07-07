package com.example.pvplatform.module.station.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.station.dto.StationRequest;
import com.example.pvplatform.module.station.entity.PowerStation;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.UserStationPermissionDO;
import com.example.pvplatform.persistence.mapper.PowerStationMapper;
import com.example.pvplatform.persistence.mapper.UserStationPermissionMapper;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class StationService {
    private final PowerStationMapper stationMapper;
    private final UserStationPermissionMapper permissionMapper;

    public StationService(PowerStationMapper stationMapper,
                          UserStationPermissionMapper permissionMapper) {
        this.stationMapper = stationMapper;
        this.permissionMapper = permissionMapper;
    }

    @Cacheable(cacheNames = "station:list", key = "'all'")
    public List<PowerStation> list() {
        return stationMapper.selectList(Wrappers.<PowerStationDO>lambdaQuery()
                .orderByDesc(PowerStationDO::getCreatedAt))
            .stream().map(this::toEntity).toList();
    }

    public PageResult<PowerStation> list(int pageNum, int pageSize, String keyword, String status) {
        validatePage(pageNum, pageSize);
        SecurityUser user = SecurityUtils.getCurrentUser();
        var query = Wrappers.<PowerStationDO>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            query.and(q -> q.like(PowerStationDO::getStationName, term)
                .or().like(PowerStationDO::getProvince, term)
                .or().like(PowerStationDO::getCity, term)
                .or().like(PowerStationDO::getAddress, term));
        }
        if (status != null && !status.isBlank()) {
            query.eq(PowerStationDO::getStatus, status.trim());
        }
        if (user != null && !isAdmin(user)) {
            List<Long> permittedStationIds = permissionMapper.selectList(
                    Wrappers.<UserStationPermissionDO>lambdaQuery()
                        .eq(UserStationPermissionDO::getUserId, user.getUserId()))
                .stream().map(UserStationPermissionDO::getStationId).distinct().toList();
            query.and(q -> {
                q.eq(PowerStationDO::getOwnerUserId, user.getUserId());
                if (!permittedStationIds.isEmpty()) {
                    q.or().in(PowerStationDO::getStationId, permittedStationIds);
                }
            });
        }
        query.orderByDesc(PowerStationDO::getCreatedAt);
        Page<PowerStationDO> page = stationMapper.selectPage(new Page<>(pageNum, pageSize), query);
        List<PowerStation> records = page.getRecords().stream().map(this::toEntity).toList();
        return new PageResult<>(page.getTotal(), pageNum, pageSize, records);
    }

    @Cacheable(cacheNames = "station:detail", key = "#stationId")
    public PowerStation detail(Long stationId) {
        PowerStationDO station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(404, "电站不存在");
        }
        return toEntity(station);
    }

    @CacheEvict(cacheNames = {"station:list", "station:detail"}, allEntries = true)
    public Long create(StationRequest request) {
        PowerStationDO station = fromRequest(request);
        station.setStationCode("PV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        station.setStatus(request.status() == null ? "RUNNING" : request.status());
        stationMapper.insert(station);
        return station.getStationId();
    }

    @CacheEvict(cacheNames = {"station:list", "station:detail"}, allEntries = true)
    public void update(Long stationId, StationRequest request) {
        if (stationMapper.selectById(stationId) == null) {
            throw new BusinessException(404, "电站不存在");
        }
        PowerStationDO station = fromRequest(request);
        station.setStationId(stationId);
        stationMapper.updateById(station);
    }

    @CacheEvict(cacheNames = {"station:list", "station:detail"}, allEntries = true)
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

    private void validatePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数不合法");
        }
    }

    private boolean isAdmin(SecurityUser user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
