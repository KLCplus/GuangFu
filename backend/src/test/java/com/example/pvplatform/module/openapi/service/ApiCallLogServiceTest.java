package com.example.pvplatform.module.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.persistence.entity.ApiCallLogDO;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.mapper.ApiCallLogMapper;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiCallLogServiceTest {
    private final ApiCallLogMapper logMapper = mock(ApiCallLogMapper.class);
    private final ModelInfoMapper modelMapper = mock(ModelInfoMapper.class);
    private final ApiCallLogService service = new ApiCallLogService(logMapper, modelMapper);

    @Test
    @SuppressWarnings("unchecked")
    void adminLogsPopulatesModelNameInBatch() {
        ApiCallLogDO log = new ApiCallLogDO();
        log.setLogId(10L);
        log.setModelId(3L);
        log.setRequestTime(LocalDateTime.now());

        Page<ApiCallLogDO> page = new Page<>(1, 10);
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        ModelInfoDO model = new ModelInfoDO();
        model.setModelId(3L);
        model.setModelName("DLinear");
        when(modelMapper.selectBatchIds(List.of(3L))).thenReturn(List.of(model));

        var result = service.adminLogs(1, 10);

        assertEquals(1, result.records().size());
        assertEquals("DLinear", result.records().getFirst().modelName());
        verify(modelMapper).selectBatchIds(List.of(3L));
    }

    @Test
    void savePersistsUsageCounters() {
        service.save(7L, 8L, 3L, "/openapi/v1/predict", "POST", "127.0.0.1",
            LocalDateTime.now(), 200, null, "{}", "{}", 60L, 24L, 84L);

        ArgumentCaptor<ApiCallLogDO> captor = ArgumentCaptor.forClass(ApiCallLogDO.class);
        verify(logMapper).insert(captor.capture());
        ApiCallLogDO row = captor.getValue();
        assertEquals(60L, row.getInputTokens());
        assertEquals(24L, row.getOutputTokens());
        assertEquals(84L, row.getTotalTokens());
    }
}
