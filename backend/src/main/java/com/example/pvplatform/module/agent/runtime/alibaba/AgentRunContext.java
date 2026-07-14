package com.example.pvplatform.module.agent.runtime.alibaba;

import com.example.pvplatform.module.agent.entity.AgentMessageDO;
import com.example.pvplatform.module.agent.entity.AgentSessionDO;
import com.example.pvplatform.security.SecurityUser;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public record AgentRunContext(String runId, AgentSessionDO session, AgentMessageDO userMessage,
                              SecurityUser user, List<String> allowedTools, AtomicBoolean approvalRequested,
                              AtomicBoolean terminalFailure, AtomicInteger eventSequence) {}
