package com.example.pvplatform.module.station.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.mapper.PowerStationMapper;
import com.example.pvplatform.persistence.mapper.UserStationPermissionMapper;
import com.example.pvplatform.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StationPermissionServiceTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowOwnerAndDenyUnassignedUser() {
        PowerStationMapper stations = mock(PowerStationMapper.class);
        UserStationPermissionMapper permissions = mock(UserStationPermissionMapper.class);
        PowerStationDO station = new PowerStationDO();
        station.setOwnerUserId(10L);
        when(stations.selectById(1L)).thenReturn(station);
        StationPermissionService service = new StationPermissionService(stations, permissions);

        authenticate(10L, "USER");
        assertSame(station, service.requireManage(1L));

        authenticate(11L, "USER");
        assertEquals(403,
            assertThrows(BusinessException.class, () -> service.requireView(1L)).getCode());
    }

    @Test
    void shouldAllowAdminForEveryStation() {
        PowerStationMapper stations = mock(PowerStationMapper.class);
        PowerStationDO station = new PowerStationDO();
        when(stations.selectById(1L)).thenReturn(station);
        StationPermissionService service = new StationPermissionService(stations,
            mock(UserStationPermissionMapper.class));
        authenticate(99L, "ADMIN");
        assertSame(station, service.requireManage(1L));
    }

    @Test
    void shouldAllowPublicStationViewButDenyManageForNormalUser() {
        PowerStationMapper stations = mock(PowerStationMapper.class);
        UserStationPermissionMapper permissions = mock(UserStationPermissionMapper.class);
        PowerStationDO station = new PowerStationDO();
        station.setOwnerUserId(null);
        when(stations.selectById(1L)).thenReturn(station);
        StationPermissionService service = new StationPermissionService(stations, permissions);

        authenticate(11L, "USER");
        assertSame(station, service.requireView(1L));
        assertEquals(403,
            assertThrows(BusinessException.class, () -> service.requireManage(1L)).getCode());
    }

    private void authenticate(Long id, String role) {
        SecurityUser user = new SecurityUser(id, "test", 1, List.of(role));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
