package com.example.pvplatform.module.agent.runtime.alibaba.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolCallbackNameTest {
    @Test
    void mapsBusinessToolDotsToModelSafeName() {
        assertEquals("station_detail", SpringAiToolCallbackFactory.callbackName("station.detail"));
    }
}
