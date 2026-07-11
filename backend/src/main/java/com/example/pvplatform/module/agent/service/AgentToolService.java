package com.example.pvplatform.module.agent.service;

import com.example.pvplatform.module.agent.entity.AgentToolCallDO;
import com.example.pvplatform.module.agent.mapper.AgentToolCallMapper;
import com.example.pvplatform.module.agent.tool.AgentTool;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentToolService {
    private final AgentToolCallMapper toolCallMapper;
    private final AgentJsonService jsonService;

    public AgentToolService(AgentToolCallMapper toolCallMapper, AgentJsonService jsonService) {
        this.toolCallMapper = toolCallMapper;
        this.jsonService = jsonService;
    }

    public AgentToolCallDO createPending(Long sessionId, Long messageId, String toolName, Map<String, Object> arguments) {
        AgentToolCallDO row = new AgentToolCallDO();
        row.setSessionId(sessionId);
        row.setMessageId(messageId);
        row.setClientToolCallId("tc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        row.setToolName(toolName);
        row.setArgumentsJson(jsonService.json(arguments == null ? Map.of() : arguments));
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(row.getCreatedAt());
        toolCallMapper.insert(row);
        return row;
    }

    public ToolExecutionResult execute(AgentToolCallDO row, AgentTool tool, ToolExecutionContext context, Map<String, Object> arguments) {
        long started = System.currentTimeMillis();
        ToolExecutionResult result = tool.execute(context, arguments == null ? Map.of() : arguments);
        long duration = System.currentTimeMillis() - started;
        AgentToolCallDO update = new AgentToolCallDO();
        update.setToolCallId(row.getToolCallId());
        update.setResultJson(jsonService.json(result));
        update.setStatus(result.success() ? "SUCCESS" : "FAILED");
        update.setErrorMessage(result.errorMessage());
        update.setDurationMs(duration);
        update.setUpdatedAt(LocalDateTime.now());
        toolCallMapper.updateById(update);
        row.setStatus(update.getStatus());
        row.setResultJson(update.getResultJson());
        row.setErrorMessage(update.getErrorMessage());
        row.setDurationMs(duration);
        return result;
    }

    public void markAwaitingApproval(AgentToolCallDO row) {
        AgentToolCallDO update = new AgentToolCallDO();
        update.setToolCallId(row.getToolCallId());
        update.setStatus("AWAITING_APPROVAL");
        update.setUpdatedAt(LocalDateTime.now());
        toolCallMapper.updateById(update);
        row.setStatus("AWAITING_APPROVAL");
    }
}
