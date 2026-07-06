package com.example.pvplatform.module.pvdata.dto;

import com.example.pvplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PvDataHistoryQueryTest {
    @Test
    void shouldApplyDefaults() {
        PvDataHistoryQuery query = new PvDataHistoryQuery(null, null, null).normalized();
        assertEquals("1min", query.interval());
        assertTrue(query.startTime().isBefore(query.endTime()));
        assertEquals(1, query.intervalMinutes());
    }

    @Test
    void shouldRejectInvalidRangeAndInterval() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(BusinessException.class,
            () -> new PvDataHistoryQuery(now, now, "1min").normalized());
        assertThrows(BusinessException.class,
            () -> new PvDataHistoryQuery(now.minusHours(1), now, "30min").normalized());
        assertThrows(BusinessException.class,
            () -> new PvDataHistoryQuery(now.minusDays(32), now, "1h").normalized());
    }
}
