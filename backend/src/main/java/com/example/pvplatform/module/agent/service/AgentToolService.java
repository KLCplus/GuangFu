package com.example.pvplatform.module.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.module.agent.entity.AgentToolCallDO;
import com.example.pvplatform.module.agent.mapper.AgentToolCallMapper;
import com.example.pvplatform.module.agent.tool.AgentTool;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
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

    public AgentToolCallDO createPending(Long sessionId, Long messageId, AgentTool tool, Map<String, Object> arguments) {
        AgentToolCallDO row = new AgentToolCallDO();
        row.setSessionId(sessionId);
        row.setMessageId(messageId);
        row.setUserId(SecurityUtils.requireCurrentUserId());
        row.setClientToolCallId("tc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        row.setToolName(tool.name());
        row.setDisplayName(tool.displayName());
        row.setArgumentsJson(jsonService.json(mask(arguments == null ? Map.of() : arguments)));
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(row.getCreatedAt());
        toolCallMapper.insert(row);
        return row;
    }

    public ToolExecutionResult execute(AgentToolCallDO row, AgentTool tool, ToolExecutionContext context, Map<String, Object> arguments) {
        long started = System.currentTimeMillis();
        ToolExecutionResult result = tool.execute(context, arguments == null ? Map.of() : arguments);
        ToolExecutionResult safeResult = new ToolExecutionResult(result.success(), mask(result.data()), result.summary(),
            result.errorCode(), result.errorMessage(), mask(result.raw()));
        long duration = System.currentTimeMillis() - started;
        AgentToolCallDO update = new AgentToolCallDO();
        update.setToolCallId(row.getToolCallId());
        update.setResultJson(jsonService.json(safeResult));
        update.setStatus(safeResult.success() ? "SUCCESS" : "FAILED");
        update.setErrorMessage(safeResult.errorMessage());
        update.setDurationMs(duration);
        update.setUpdatedAt(LocalDateTime.now());
        toolCallMapper.updateById(update);
        row.setStatus(update.getStatus());
        row.setResultJson(update.getResultJson());
        row.setErrorMessage(update.getErrorMessage());
        row.setDurationMs(duration);
        return safeResult;
    }

    public void markAwaitingApproval(AgentToolCallDO row) {
        AgentToolCallDO update = new AgentToolCallDO();
        update.setToolCallId(row.getToolCallId());
        update.setStatus("AWAITING_APPROVAL");
        update.setUpdatedAt(LocalDateTime.now());
        toolCallMapper.updateById(update);
        row.setStatus("AWAITING_APPROVAL");
    }

    public List<AgentToolCallDO> listRecent(Long sessionId, int limit) {
        return toolCallMapper.selectList(Wrappers.<AgentToolCallDO>lambdaQuery()
                .eq(AgentToolCallDO::getSessionId, sessionId)
                .orderByDesc(AgentToolCallDO::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    private Object mask(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> next = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                next.put(key, isSensitive(key) ? "******" : mask(entry.getValue()));
            }
            return next;
        }
        if (value instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false).map(this::mask).toList();
        }
        return value;
    }

    private boolean isSensitive(String key) {
        return key != null && key.matches("(?i).*(api[_-]?key|token|authorization|password|secret).*|key");
    }
}
