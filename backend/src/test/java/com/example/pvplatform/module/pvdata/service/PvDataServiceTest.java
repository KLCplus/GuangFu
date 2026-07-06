package com.example.pvplatform.module.pvdata.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.persistence.entity.PvDataDO;
import com.example.pvplatform.persistence.mapper.PvDataMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PvDataServiceTest {
    private final PvDataMapper mapper = mock(PvDataMapper.class);
    private final StationPermissionService permissions = mock(StationPermissionService.class);
    private final PvDataService service = new PvDataService(mapper, permissions);

    @Test
    void shouldRejectMissingRealtimeData() {
        when(mapper.selectOne(any())).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.realtime(1L));
        assertEquals(404, exception.getCode());
        verify(permissions).requireView(1L);
    }

    @Test
    void shouldAcceptThirtyContinuousFreshRows() {
        when(mapper.selectList(any())).thenReturn(rows(false, false));
        assertEquals(30, service.latestThirty(1L).size());
    }

    @Test
    void shouldRejectGapAndNullPredictionField() {
        when(mapper.selectList(any())).thenReturn(rows(true, false));
        assertEquals("预测输入数据时间不连续",
            assertThrows(BusinessException.class, () -> service.latestThirty(1L)).getMessage());
        when(mapper.selectList(any())).thenReturn(rows(false, true));
        assertEquals("预测输入数据存在空值或非法数值",
            assertThrows(BusinessException.class, () -> service.latestThirty(1L)).getMessage());
    }

    private List<PvDataDO> rows(boolean gap, boolean missing) {
        LocalDateTime start = LocalDateTime.now().withSecond(0).withNano(0).minusMinutes(29);
        List<PvDataDO> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            PvDataDO row = new PvDataDO();
            row.setCollectTime(start.plusMinutes(i + (gap && i >= 15 ? 1 : 0)));
            row.setPowerKw(missing && i == 10 ? null : BigDecimal.TEN);
            row.setAmbientTemperatureC(BigDecimal.valueOf(25));
            row.setIrradianceWM2(BigDecimal.valueOf(800));
            rows.add(row);
        }
        Collections.reverse(rows);
        return rows;
    }
}
