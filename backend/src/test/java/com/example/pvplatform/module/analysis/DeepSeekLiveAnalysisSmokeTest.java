package com.example.pvplatform.module.analysis;

import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.service.AnalysisService;
import com.example.pvplatform.persistence.entity.PowerStationDO;
import com.example.pvplatform.persistence.entity.PvDataDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.PowerStationMapper;
import com.example.pvplatform.persistence.mapper.PvDataMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekLiveAnalysisSmokeTest {
    @Autowired private AnalysisService analysisService;
    @Autowired private SysUserMapper userMapper;
    @Autowired private PowerStationMapper stationMapper;
    @Autowired private PvDataMapper pvDataMapper;

    @DynamicPropertySource
    static void deepSeekProperties(DynamicPropertyRegistry registry) {
        registry.add("analysis.llm.enabled", () -> true);
        registry.add("analysis.llm.provider", () -> "deepseek");
        registry.add("analysis.llm.api-key", () -> System.getenv("DEEPSEEK_API_KEY"));
        registry.add("analysis.llm.model", () -> System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-v4-flash"));
        registry.add("analysis.llm.timeout-ms", () -> 60000);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatesStructuredReportWithDeepSeek() {
        SysUserDO user = new SysUserDO();
        user.setUsername("deepseek-live-" + UUID.randomUUID());
        user.setPasswordHash("not-used");
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        SecurityUser principal = new SecurityUser(user.getUserId(), user.getUsername(), 1, List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        PowerStationDO station = new PowerStationDO();
        station.setStationCode("DS-" + UUID.randomUUID().toString().substring(0, 20));
        station.setStationName("DeepSeek联调光伏电站");
        station.setOwnerUserId(user.getUserId());
        station.setCapacityKw(new BigDecimal("1200"));
        station.setProvince("四川");
        station.setCity("成都");
        station.setStatus("RUNNING");
        station.setDeleted(0);
        stationMapper.insert(station);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        insertPv(station.getStationId(), now.minusMinutes(20), "360.5", "640");
        insertPv(station.getStationId(), now.minusMinutes(10), "372.3", "660");
        insertPv(station.getStationId(), now, "381.8", "675");

        var report = analysisService.report(new AnalysisRequest(
            station.getStationId(),
            null,
            "DeepSeek Live Smoke 综合分析报告",
            "请基于当前上下文生成结构化综合分析报告，重点说明历史功率趋势和运维建议。",
            false,
            false
        ));

        assertNotNull(report.reportId());
        assertEquals("SUCCESS", report.status());
        assertFalse(report.modelName().startsWith("mock"));
        assertTrue(report.summary() != null && !report.summary().isBlank());
        assertTrue(report.sections() != null && !report.sections().isEmpty());
        assertTrue(report.markdown() != null && !report.markdown().isBlank());
        assertTrue(report.rawResponse() != null && !report.rawResponse().isBlank());
    }

    private void insertPv(Long stationId, LocalDateTime time, String power, String irradiance) {
        PvDataDO row = new PvDataDO();
        row.setStationId(stationId);
        row.setCollectTime(time);
        row.setPowerKw(new BigDecimal(power));
        row.setIrradianceWM2(new BigDecimal(irradiance));
        pvDataMapper.insert(row);
    }
}
