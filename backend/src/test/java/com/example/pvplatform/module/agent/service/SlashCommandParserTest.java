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
        assertTool("查看新闻通知", "news.list");
        assertTool("查看钱包余额", "wallet.balance");
        assertTool("查看市场套餐", "marketplace.list");
        assertTool("查看模型列表", "model.list");
        assertTool("查看 API 调用日志", "api.usage");
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

    private void assertTool(String message, String expectedTool) {
        AgentToolIntent intent = parser.parse(message, Map.of(), null, Map.of());
        assertEquals(expectedTool, intent.toolName(), message);
    }
}
