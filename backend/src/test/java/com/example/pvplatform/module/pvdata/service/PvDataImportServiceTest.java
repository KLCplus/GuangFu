package com.example.pvplatform.module.pvdata.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.pvdata.parser.CsvPvDataFileParser;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.persistence.entity.*;
import com.example.pvplatform.persistence.mapper.*;
import com.example.pvplatform.security.SecurityUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PvDataImportServiceTest {
    private static final String CSV =
        "collectTime,powerKw,energyTodayKwh,energyTotalKwh,voltageV,currentA,"
            + "irradianceWM2,moduleTemperatureC,ambientTemperatureC,humidityPercent,windSpeedMS\n"
            + "2026-07-06 10:00:00,10,,,,,800,,30,60,2\n";
    @TempDir Path tempDir;
    private PvDataMapper dataMapper;
    private PvDataImportService service;

    @BeforeEach
    void setUp() {
        dataMapper = mock(PvDataMapper.class);
        PvDataImportTaskMapper tasks = mock(PvDataImportTaskMapper.class);
        FileResourceMapper files = mock(FileResourceMapper.class);
        StationPermissionService permissions = mock(StationPermissionService.class);
        doAnswer(invocation -> {
            PvDataImportTaskDO task = invocation.getArgument(0);
            task.setImportId(1L);
            return 1;
        }).when(tasks).insert(any(PvDataImportTaskDO.class));
        service = new PvDataImportService(dataMapper, tasks, permissions,
            new PvFileStorageService(files, tempDir.toString(), 1024 * 1024),
            new PvDataValidationService(), List.of(new CsvPvDataFileParser()), 100);
        SecurityUser user = new SecurityUser(7L, "test", 1, List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBatchInsertCsv() {
        when(dataMapper.selectList(any())).thenReturn(List.of());
        var result = service.importFile(1L, file(), "SKIP");
        assertEquals(1, result.successCount());
        assertEquals("SUCCESS", result.status());
        verify(dataMapper).batchInsert(anyList());
    }

    @Test
    void shouldUpsertOrFailOnDuplicateAccordingToStrategy() {
        PvDataDO existing = new PvDataDO();
        existing.setCollectTime(LocalDateTime.of(2026, 7, 6, 10, 0));
        when(dataMapper.selectList(any())).thenReturn(List.of(existing));
        assertEquals(1, service.importFile(1L, file(), "UPDATE").successCount());
        verify(dataMapper).batchUpsert(anyList());

        assertThrows(BusinessException.class,
            () -> service.importFile(1L, file(), "FAIL"));
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "pv.csv", "text/csv",
            CSV.getBytes(StandardCharsets.UTF_8));
    }
}
