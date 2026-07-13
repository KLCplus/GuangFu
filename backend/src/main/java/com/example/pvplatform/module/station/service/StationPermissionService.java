package com.example.pvplatform.module.station.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.UserStationPermissionDO;
import com.example.pvplatform.persistence.mapper.PowerStationMapper;
import com.example.pvplatform.persistence.mapper.UserStationPermissionMapper;
import com.example.pvplatform.security.SecurityUser;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class StationPermissionService {
    private final PowerStationMapper stationMapper;
    private final UserStationPermissionMapper permissionMapper;

    public StationPermissionService(PowerStationMapper stationMapper,
                                    UserStationPermissionMapper permissionMapper) {
        this.stationMapper = stationMapper;
        this.permissionMapper = permissionMapper;
    }

    public PowerStationDO requireView(Long stationId) {
        return require(stationId, false);
    }

    public PowerStationDO requireManage(Long stationId) {
        return require(stationId, true);
    }

    private PowerStationDO require(Long stationId, boolean manage) {
        if (stationId == null || stationId <= 0) {
            throw new BusinessException(400, "电站 ID 不合法");
        }
        PowerStationDO station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(404, "电站不存在");
        }
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        if (hasRole(user, "ROLE_ADMIN") || station.getOwnerUserId() != null
            && station.getOwnerUserId().equals(user.getUserId())) {
            return station;
        }
        if (!manage && station.getOwnerUserId() == null) {
            return station;
        }
        UserStationPermissionDO permission = permissionMapper.selectOne(
            Wrappers.<UserStationPermissionDO>lambdaQuery()
                .eq(UserStationPermissionDO::getUserId, user.getUserId())
                .eq(UserStationPermissionDO::getStationId, stationId)
                .last("LIMIT 1"));
        boolean allowed = permission != null && (!manage
            || "MANAGE".equalsIgnoreCase(permission.getPermissionType()));
        if (!allowed) {
            throw new BusinessException(403, manage ? "无权管理该电站" : "无权访问该电站");
        }
        return station;
    }

    public boolean isAdmin() {
        SecurityUser user = SecurityUtils.getCurrentUser();
        return user != null && hasRole(user, "ROLE_ADMIN");
    }

    private boolean hasRole(SecurityUser user, String authority) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .anyMatch(authority::equals);
    }
}
