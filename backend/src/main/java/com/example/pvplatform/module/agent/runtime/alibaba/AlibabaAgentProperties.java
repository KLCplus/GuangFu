package com.example.pvplatform.module.agent.runtime.alibaba;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.runtime.alibaba")
public class AlibabaAgentProperties {
    private boolean enabled = true;
    private int maxModelCalls = 6;
    private int maxToolCalls = 12;
    private long toolTimeoutMs = 15000;
    private long modelTimeoutMs = 60000;
    private boolean persistEvents = true;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxModelCalls() { return maxModelCalls; }
    public void setMaxModelCalls(int value) { maxModelCalls = value; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int value) { maxToolCalls = value; }
    public long getToolTimeoutMs() { return toolTimeoutMs; }
    public void setToolTimeoutMs(long value) { toolTimeoutMs = value; }
    public long getModelTimeoutMs() { return modelTimeoutMs; }
    public void setModelTimeoutMs(long value) { modelTimeoutMs = value; }
    public boolean isPersistEvents() { return persistEvents; }
    public void setPersistEvents(boolean value) { persistEvents = value; }
}
