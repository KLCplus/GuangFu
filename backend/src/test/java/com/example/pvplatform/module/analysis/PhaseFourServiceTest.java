package com.example.pvplatform.module.analysis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.service.AnalysisService;
import com.example.pvplatform.module.news.dto.NewsRequest;
import com.example.pvplatform.module.news.service.NewsService;
import com.example.pvplatform.persistence.entity.*;
import com.example.pvplatform.persistence.mapper.*;
import com.example.pvplatform.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PhaseFourServiceTest {
    @Autowired private AnalysisService analysisService;
    @Autowired private NewsService newsService;
    @Autowired private SysUserMapper userMapper;
    @Autowired private PowerStationMapper stationMapper;
    @Autowired private PredictionTaskMapper taskMapper;
    @Autowired private PredictionResultMapper resultMapper;
    @Autowired private PvDataMapper pvDataMapper;
    @Autowired private AnalysisReportMapper reportMapper;
    @Autowired private NewsMapper newsMapper;
    @Autowired private UserNotificationMapper notificationMapper;

    private Long userId;

    @BeforeEach
    void setUp() {
        SysUserDO user = new SysUserDO();
        user.setUsername("phase4-" + UUID.randomUUID());
        user.setPasswordHash("not-used");
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        userId = user.getUserId();
        SecurityUser principal = new SecurityUser(userId, user.getUsername(), 1, List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatesAndPersistsReportFromBusinessData() {
        PowerStationDO station = new PowerStationDO();
        station.setStationCode("S-" + UUID.randomUUID().toString().substring(0, 20));
        station.setStationName("测试电站");
        station.setOwnerUserId(userId);
        station.setStatus("RUNNING");
        station.setDeleted(0);
        stationMapper.insert(station);

        PredictionTaskDO task = new PredictionTaskDO();
        task.setTaskNo("T-" + UUID.randomUUID());
        task.setUserId(userId);
        task.setStationId(station.getStationId());
        task.setModelId(1L);
        task.setStatus("SUCCESS");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        insertPv(station.getStationId(), now.minusMinutes(10), "10", "500");
        insertPv(station.getStationId(), now, "12", "600");
        insertPrediction(task.getTaskId(), 5, "12");
        insertPrediction(task.getTaskId(), 30, "14");

        var report = analysisService.report(
            new AnalysisRequest(station.getStationId(), task.getTaskId(), null, true, true));

        assertNotNull(report.reportId());
        assertTrue(report.summary().contains("测试电站"));
        assertTrue(report.weatherAnalysis().contains("降级"));
        assertNotNull(reportMapper.selectById(report.reportId()));
    }

    @Test
    void newsStartsAsDraftAndPublishingCreatesNotification() {
        Long newsId = newsService.create(new NewsRequest("模型更新", "摘要", "正文", null,
            "MODEL_UPDATE", "ALL"));
        assertEquals("DRAFT", newsMapper.selectById(newsId).getStatus());

        newsService.publish(newsId);

        assertEquals("PUBLISHED", newsMapper.selectById(newsId).getStatus());
        long notifications = notificationMapper.selectCount(
            Wrappers.<UserNotificationDO>lambdaQuery()
                .eq(UserNotificationDO::getUserId, userId)
                .eq(UserNotificationDO::getRelatedId, newsId));
        assertEquals(1, notifications);
        assertEquals(1, newsService.list(1, 10, "MODEL_UPDATE").records().size());
    }

    private void insertPv(Long stationId, LocalDateTime time, String power, String irradiance) {
        PvDataDO row = new PvDataDO();
        row.setStationId(stationId);
        row.setCollectTime(time);
        row.setPowerKw(new BigDecimal(power));
        row.setIrradianceWM2(new BigDecimal(irradiance));
        pvDataMapper.insert(row);
    }

    private void insertPrediction(Long taskId, int offset, String power) {
        PredictionResultDO row = new PredictionResultDO();
        row.setTaskId(taskId);
        row.setTimeOffsetMinutes(offset);
        row.setPredictTime(LocalDateTime.now().plusMinutes(offset));
        row.setPredictPowerKw(new BigDecimal(power));
        resultMapper.insert(row);
    }
}
