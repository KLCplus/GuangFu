package com.example.pvplatform.module.agent.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashCommandParserTest {
    private final SlashCommandParser parser = new SlashCommandParser();

    @Test
    void shouldRouteProjectManagerRequestsBeyondStation() {
        assertTool("查看我的个人信息", "user.profile");
        assertTool("查看新闻公告", "news.list");
        assertTool("查看钱包余额", "wallet.balance");
        assertTool("查看市场套餐", "marketplace.list");
        assertTool("查看模型列表", "model.list");
        assertTool("查看 API 调用日志", "api.usage");
        assertTool("查看仪表盘概览", "dashboard.overview");
        assertTool("查询 1 号电站实时功率", "pv.realtime");
        assertTool("查询 1 号电站历史功率", "pv.history");
        assertTool("查询 1 号电站天气预报", "weather.forecast");
        assertTool("查看未读通知", "notification.unreadCount");
        assertTool("查看通知列表", "notification.list");
    }

    @Test
    void shouldAskForCloudAndModelRunInputsInsteadOfStationFallback() {
        AgentToolIntent cloud = parser.parse("运行云图预测", Map.of(), null, Map.of());
        assertEquals("cloud.predict", cloud.toolName());
        assertTrue(cloud.question().contains("10 张 inputImages"));

        AgentToolIntent modelRun = parser.parse("运行预测模型", Map.of(), null, Map.of());
        assertEquals("model.run", modelRun.toolName());
        assertTrue(modelRun.question().contains("30 帧 numericValues"));
    }

    @Test
    void shouldRouteControlledWriteCommands() {
        AgentToolIntent profile = parser.parse("把昵称改为 张三", Map.of(), null, Map.of());
        assertEquals("user.profile.update", profile.toolName());
        assertEquals("张三", profile.arguments().get("nickname"));

        AgentToolIntent phone = parser.parse("把我的电话号码改为 13900001111", Map.of(), null, Map.of());
        assertEquals("user.profile.update", phone.toolName());
        assertEquals("13900001111", phone.arguments().get("phone"));

        AgentToolIntent readAll = parser.parse("全部通知已读", Map.of(), null, Map.of());
        assertEquals("notification.markAllRead", readAll.toolName());
    }

    @Test
    void shouldRoutePublicPvOutputTools() {
        assertTool("查看公开电站", "pvoutput.station.list");
        assertTool("查看公开电站 2 的状态", "pvoutput.status.latest");
        assertTool("查看公开电站 2 的天气", "pvoutput.weather.current");
    }

    private void assertTool(String message, String expectedTool) {
        AgentToolIntent intent = parser.parse(message, Map.of(), null, Map.of());
        assertEquals(expectedTool, intent.toolName(), message);
    }
}
