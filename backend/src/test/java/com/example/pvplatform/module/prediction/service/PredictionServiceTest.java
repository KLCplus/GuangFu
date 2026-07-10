package com.example.pvplatform.module.prediction.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.client.ModelServiceClient;
import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.prediction.dto.PredictionRequest;
import com.example.pvplatform.persistence.entity.*;
import com.example.pvplatform.persistence.mapper.*;
import com.example.pvplatform.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class PredictionServiceTest {

    @Autowired
    private PredictionService predictionService;

    @MockBean
    private ModelServiceClient modelServiceClient;

    @Autowired
    private ModelInfoMapper modelInfoMapper;

    @Autowired
    private PvDataMapper pvDataMapper;

    @Autowired
    private PowerStationMapper stationMapper;

    @Autowired
    private PredictionTaskMapper taskMapper;

    @Autowired
    private PredictionInputSnapshotMapper inputMapper;

    @Autowired
    private PredictionResultMapper resultMapper;

    private Long testStationId;
    private Long testModelId;
    private static final Long TEST_USER_ID = 100L;

    @BeforeEach
    void setUp() {
        // Reset mock state between tests
        reset(modelServiceClient);

        // Set up security context
        SecurityUser testUser = new SecurityUser(TEST_USER_ID, "testuser", 1,
            List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()));

        // Clean data using proper wrappers
        resultMapper.delete(Wrappers.lambdaQuery());
        inputMapper.delete(Wrappers.lambdaQuery());
        taskMapper.delete(Wrappers.lambdaQuery());
        pvDataMapper.delete(Wrappers.lambdaQuery());
        modelInfoMapper.delete(Wrappers.lambdaQuery());
        stationMapper.delete(Wrappers.lambdaQuery());

        // Create station
        PowerStationDO station = new PowerStationDO();
        station.setStationCode("PV-TEST01");
        station.setStationName("测试电站");
        station.setProvince("四川省");
        station.setCity("成都市");
        station.setOwnerUserId(TEST_USER_ID);
        station.setStatus("RUNNING");
        station.setCreatedAt(LocalDateTime.now());
        stationMapper.insert(station);
        testStationId = station.getStationId();

        // Create ONLINE model
        ModelInfoDO model = new ModelInfoDO();
        model.setModelCode("test_lstm");
        model.setModelName("Test LSTM");
        model.setModelType("NUMERIC");
        model.setModelVersion("v1.0");
        model.setInputWindowMinutes(30);
        model.setInputFrameIntervalSeconds(60);
        model.setOutputSteps(6);
        model.setOutputStepMinutes(5);
        model.setServiceModelName("test_lstm");
        model.setApiPath("/model-api/predict");
        model.setStatus("ONLINE");
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        modelInfoMapper.insert(model);
        testModelId = model.getModelId();

        // Create 30 PV data points
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        for (int i = 0; i < 30; i++) {
            PvDataDO data = new PvDataDO();
            data.setStationId(testStationId);
            data.setCollectTime(now.minusMinutes(29 - i));
            data.setPowerKw(BigDecimal.valueOf(500 + i));
            data.setIrradianceWM2(BigDecimal.valueOf(800));
            data.setAmbientTemperatureC(BigDecimal.valueOf(25));
            data.setCreatedAt(LocalDateTime.now());
            pvDataMapper.insert(data);
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Tests ──────────────────────────────────────────────────

    @Test
    void shouldCreatePredictionSuccessfully() {
        List<ModelPredictResponse.Prediction> predictions = List.of(
            new ModelPredictResponse.Prediction(5, 510.0),
            new ModelPredictResponse.Prediction(10, 515.0),
            new ModelPredictResponse.Prediction(15, 520.0),
            new ModelPredictResponse.Prediction(20, 525.0),
            new ModelPredictResponse.Prediction(25, 530.0),
            new ModelPredictResponse.Prediction(30, 535.0)
        );
        ModelPredictResponse mockResponse = new ModelPredictResponse(200, "success",
            new ModelPredictResponse.Data("test_lstm", predictions, 100));
        when(modelServiceClient.predict(any(ModelPredictRequest.class), any(String.class), any(Integer.class)))
            .thenReturn(mockResponse);

        PredictionRequest request = new PredictionRequest(testStationId, testModelId, "STATION_HISTORY", null, null);
        var result = predictionService.create(request);

        assertNotNull(result);
        assertEquals("SUCCESS", result.status());
        assertEquals("test_lstm", result.modelCode());
        assertEquals(100L, result.costTimeMs());

        // Verify 30 snapshots saved
        Long snapshotCount = inputMapper.selectCount(Wrappers.<PredictionInputSnapshotDO>lambdaQuery()
                .eq(PredictionInputSnapshotDO::getTaskId, result.taskId()));
        assertEquals(30, snapshotCount);

        // Verify 6 results saved
        Long resultCount = resultMapper.selectCount(Wrappers.<PredictionResultDO>lambdaQuery()
                .eq(PredictionResultDO::getTaskId, result.taskId()));
        assertEquals(6, resultCount);
    }

    @Test
    void shouldFailTaskWhenModelServiceUnavailable() {
        when(modelServiceClient.predict(any(ModelPredictRequest.class), any(String.class), any(Integer.class)))
            .thenThrow(new BusinessException(502, "模型服务不可用"));

        PredictionRequest request = new PredictionRequest(testStationId, testModelId, "STATION_HISTORY", null, null);

        assertThrows(BusinessException.class, () -> predictionService.create(request));

        // Verify a FAILED task was created
        List<PredictionTaskDO> tasks = taskMapper.selectList(Wrappers.<PredictionTaskDO>lambdaQuery()
                .eq(PredictionTaskDO::getStationId, testStationId));
        assertFalse(tasks.isEmpty());
        assertEquals("FAILED", tasks.get(0).getStatus());
        assertNotNull(tasks.get(0).getErrorMessage());
    }

    @Test
    void shouldRequireOnlineModel() {
        // Set model to OFFLINE
        ModelInfoDO model = modelInfoMapper.selectById(testModelId);
        model.setStatus("OFFLINE");
        modelInfoMapper.updateById(model);

        PredictionRequest request = new PredictionRequest(testStationId, testModelId, "STATION_HISTORY", null, null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> predictionService.create(request));
        assertEquals(400, ex.getCode());
    }

    @Test
    void shouldNotAllowAccessToOtherUserTasks() {
        List<ModelPredictResponse.Prediction> predictions = List.of(
            new ModelPredictResponse.Prediction(5, 510.0),
            new ModelPredictResponse.Prediction(10, 515.0),
            new ModelPredictResponse.Prediction(15, 520.0),
            new ModelPredictResponse.Prediction(20, 525.0),
            new ModelPredictResponse.Prediction(25, 530.0),
            new ModelPredictResponse.Prediction(30, 535.0)
        );
        ModelPredictResponse mockResponse = new ModelPredictResponse(200, "success",
            new ModelPredictResponse.Data("test_lstm", predictions, 100));
        when(modelServiceClient.predict(any(ModelPredictRequest.class), any(String.class), any(Integer.class)))
            .thenReturn(mockResponse);

        PredictionRequest request = new PredictionRequest(testStationId, testModelId, "STATION_HISTORY", null, null);
        var result = predictionService.create(request);

        // Manually change userId to another user
        PredictionTaskDO task = taskMapper.selectById(result.taskId());
        task.setUserId(99999L);
        taskMapper.updateById(task);

        // Now user should not be able to access this task
        assertThrows(BusinessException.class,
            () -> predictionService.detail(result.taskId()));
    }

    @Test
    void shouldListHistoryWithPagination() {
        for (int i = 0; i < 2; i++) {
            List<ModelPredictResponse.Prediction> predictions = List.of(
                new ModelPredictResponse.Prediction(5, 510.0),
                new ModelPredictResponse.Prediction(10, 515.0),
                new ModelPredictResponse.Prediction(15, 520.0),
                new ModelPredictResponse.Prediction(20, 525.0),
                new ModelPredictResponse.Prediction(25, 530.0),
                new ModelPredictResponse.Prediction(30, 535.0)
            );
            ModelPredictResponse mockResponse = new ModelPredictResponse(200, "success",
                new ModelPredictResponse.Data("test_lstm", predictions, 100));
            when(modelServiceClient.predict(any(ModelPredictRequest.class), any(String.class), any(Integer.class)))
                .thenReturn(mockResponse);

            PredictionRequest request = new PredictionRequest(testStationId, testModelId, "STATION_HISTORY", null, null);
            predictionService.create(request);
        }

        var page = predictionService.history(1, 10, null, null, null);
        assertTrue(page.total() >= 2);
    }
}
