package com.example.pvplatform.module.model.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.model.dto.CreateModelRequest;
import com.example.pvplatform.module.model.dto.UpdateModelRequest;
import com.example.pvplatform.module.model.vo.ModelDetailVO;
import com.example.pvplatform.module.model.vo.ModelListItemVO;
import com.example.pvplatform.persistence.entity.ModelInfoDO;
import com.example.pvplatform.persistence.mapper.ModelInfoMapper;
import com.example.pvplatform.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ModelServiceTest {

    @Autowired
    private ModelService modelService;

    @Autowired
    private ModelInfoMapper modelInfoMapper;

    private Long testModelId;

    @BeforeEach
    void setUp() {
        // Set up admin user in security context
        SecurityUser adminUser = new SecurityUser(1L, "admin", 1,
            List.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities()));

        // Clean up and create base test model
        modelInfoMapper.delete(null);
        ModelInfoDO model = new ModelInfoDO();
        model.setModelCode("test_model");
        model.setModelName("测试模型");
        model.setModelType("NUMERIC");
        model.setModelVersion("v1.0");
        model.setInputWindowMinutes(30);
        model.setInputFrameIntervalSeconds(60);
        model.setOutputSteps(6);
        model.setOutputStepMinutes(5);
        model.setServiceModelName("test_model");
        model.setApiPath("/model-api/predict");
        model.setStatus("OFFLINE");
        model.setDescription("测试");
        model.setMarketplaceVisible(true);
        model.setSortOrder(10);
        model.setCreatedAt(java.time.LocalDateTime.now());
        model.setUpdatedAt(java.time.LocalDateTime.now());
        modelInfoMapper.insert(model);
        testModelId = model.getModelId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── 创建校验 ──────────────────────────────────────────────

    @Test
    void shouldCreateModelSuccessfully() {
        CreateModelRequest req = new CreateModelRequest(
            "unique_model", "新模型", "NUMERIC", "v1.0",
            30, 60, 6, 5,
            "unique_model", "/model-api/predict",
            null, null, "test description");
        Long id = modelService.create(req);
        assertNotNull(id);
    }

    @Test
    void shouldRejectDuplicateModelCode() {
        CreateModelRequest req = new CreateModelRequest(
            "test_model", "新模型", "NUMERIC", "v1.0",
            30, 60, 6, 5,
            "test_model", "/model-api/predict",
            null, null, "test");
        assertThrows(BusinessException.class, () -> modelService.create(req));
    }

    @Test
    void shouldRejectInvalidModelType() {
        CreateModelRequest req = new CreateModelRequest(
            "invalid_type", "新模型", "INVALID_TYPE", "v1.0",
            30, 60, 6, 5,
            "invalid_type", "/model-api/predict",
            null, null, "test");
        BusinessException ex = assertThrows(BusinessException.class, () -> modelService.create(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void shouldRejectEmptyServiceModelName() {
        CreateModelRequest req = new CreateModelRequest(
            "empty_service", "新模型", "NUMERIC", "v1.0",
            30, 60, 6, 5,
            "", "/model-api/predict",
            null, null, "test");
        assertThrows(BusinessException.class, () -> modelService.create(req));
    }

    @Test
    void shouldDefaultToOFFLINEStatus() {
        CreateModelRequest req = new CreateModelRequest(
            "default_status", "新模型", "NUMERIC", "v1.0",
            30, 60, 6, 5,
            "default_status", "/model-api/predict",
            null, null, "test");
        Long id = modelService.create(req);
        ModelDetailVO detail = modelService.detail(id);
        assertEquals("OFFLINE", detail.status());
    }

    // ── 列表和详情 ────────────────────────────────────────────

    @Test
    void shouldListMarketplaceVisibleModelsRegardlessOfOnlineStatus() {
        // Create an ONLINE model
        ModelInfoDO online = new ModelInfoDO();
        online.setModelCode("online_model");
        online.setModelName("在线模型");
        online.setModelType("NUMERIC");
        online.setModelVersion("v1.0");
        online.setInputWindowMinutes(30);
        online.setInputFrameIntervalSeconds(60);
        online.setOutputSteps(6);
        online.setOutputStepMinutes(5);
        online.setServiceModelName("online_model");
        online.setApiPath("/model-api/predict");
        online.setStatus("ONLINE");
        online.setDescription("在线");
        online.setMarketplaceVisible(true);
        online.setSortOrder(20);
        online.setCreatedAt(java.time.LocalDateTime.now());
        online.setUpdatedAt(java.time.LocalDateTime.now());
        modelInfoMapper.insert(online);

        List<ModelListItemVO> list = modelService.list(null);
        // Marketplace display is controlled by marketplace_visible; OFFLINE only controls callability.
        assertTrue(list.stream().anyMatch(m -> "test_model".equals(m.modelCode()) && "OFFLINE".equals(m.status())));
        assertTrue(list.stream().anyMatch(m -> "online_model".equals(m.modelCode())));
    }

    @Test
    void adminShouldSeeAllModels() {
        List<ModelListItemVO> list = modelService.adminList();
        assertTrue(list.stream().anyMatch(m -> "test_model".equals(m.modelCode())));
    }

    @Test
    void shouldReturnModelDetail() {
        ModelDetailVO detail = modelService.detail(testModelId);
        assertEquals("test_model", detail.modelCode());
        assertEquals("OFFLINE", detail.status());
    }

    @Test
    void shouldThrow404ForMissingModel() {
        assertThrows(BusinessException.class, () -> modelService.detail(99999L));
    }

    // ── 状态转换 ──────────────────────────────────────────────

    @Test
    void shouldAllowOfflineToTesting() {
        modelService.updateStatus(testModelId, "TESTING");
        ModelDetailVO detail = modelService.detail(testModelId);
        assertEquals("TESTING", detail.status());
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        // OFFLINE -> ONLINE without health check will fail (FastAPI not running)
        BusinessException ex = assertThrows(BusinessException.class,
            () -> modelService.updateStatus(testModelId, "ONLINE"));
        assertTrue(ex.getMessage().contains("模型服务不可用") || ex.getMessage().contains("无法上线"));
    }

    @Test
    void shouldRejectInvalidStatusValue() {
        assertThrows(BusinessException.class,
            () -> modelService.updateStatus(testModelId, "INVALID"));
    }

    // ── 更新 ──────────────────────────────────────────────────

    @Test
    void shouldUpdateModelInfo() {
        UpdateModelRequest req = new UpdateModelRequest(
            "更新后的名称", null, "v2.0",
            null, null, null, null,
            null, null, null, null, "更新后的描述");
        modelService.update(testModelId, req);
        ModelDetailVO detail = modelService.detail(testModelId);
        assertEquals("更新后的名称", detail.modelName());
        assertEquals("v2.0", detail.modelVersion());
        assertEquals("更新后的描述", detail.description());
        // model_code should NOT change
        assertEquals("test_model", detail.modelCode());
    }

    // ── ONLINE 模型查询 ───────────────────────────────────────

    @Test
    void shouldRequireOnlineModel() {
        // Model is OFFLINE
        BusinessException ex = assertThrows(BusinessException.class,
            () -> modelService.requireOnlineModel(testModelId));
        assertEquals(400, ex.getCode());
        assertEquals("模型当前不可用", ex.getMessage());
    }
}
