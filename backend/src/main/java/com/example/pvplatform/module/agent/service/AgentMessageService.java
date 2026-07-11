package com.example.pvplatform.module.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.module.agent.dto.AgentApprovalDTO;
import com.example.pvplatform.module.agent.dto.AgentMessageDTO;
import com.example.pvplatform.module.agent.dto.AgentToolCallDTO;
import com.example.pvplatform.module.agent.entity.AgentApprovalDO;
import com.example.pvplatform.module.agent.entity.AgentMessageDO;
import com.example.pvplatform.module.agent.entity.AgentToolCallDO;
import com.example.pvplatform.module.agent.mapper.AgentApprovalMapper;
import com.example.pvplatform.module.agent.mapper.AgentMessageMapper;
import com.example.pvplatform.module.agent.mapper.AgentToolCallMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentMessageService {
    private final AgentMessageMapper messageMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final AgentApprovalMapper approvalMapper;
    private final AgentJsonService jsonService;

    public AgentMessageService(AgentMessageMapper messageMapper, AgentToolCallMapper toolCallMapper,
                               AgentApprovalMapper approvalMapper, AgentJsonService jsonService) {
        this.messageMapper = messageMapper;
        this.toolCallMapper = toolCallMapper;
        this.approvalMapper = approvalMapper;
        this.jsonService = jsonService;
    }

    public AgentMessageDO save(Long sessionId, String role, String content, Object metadata) {
        AgentMessageDO row = new AgentMessageDO();
        row.setSessionId(sessionId);
        row.setUserId(SecurityUtils.requireCurrentUserId());
        row.setRole(role);
        row.setContent(content == null ? "" : content);
        row.setMetadataJson(metadata == null ? null : jsonService.json(metadata));
        row.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(row);
        return row;
    }

    public List<AgentMessageDO> list(Long sessionId) {
        return messageMapper.selectList(Wrappers.<AgentMessageDO>lambdaQuery()
            .eq(AgentMessageDO::getSessionId, sessionId)
            .orderByAsc(AgentMessageDO::getCreatedAt)
            .orderByAsc(AgentMessageDO::getMessageId));
    }

    public List<AgentMessageDTO> listWithDetails(Long sessionId) {
        return list(sessionId).stream().map(message -> {
            List<AgentToolCallDTO> toolCalls = toolCallMapper.selectList(Wrappers.<AgentToolCallDO>lambdaQuery()
                    .eq(AgentToolCallDO::getSessionId, sessionId)
                    .eq(AgentToolCallDO::getMessageId, message.getMessageId())
                    .orderByAsc(AgentToolCallDO::getCreatedAt))
                .stream().map(this::toToolDTO).toList();
            List<AgentApprovalDTO> approvals = toolCalls.isEmpty() ? List.of() : approvalMapper.selectList(Wrappers.<AgentApprovalDO>lambdaQuery()
                    .eq(AgentApprovalDO::getSessionId, sessionId)
                    .in(AgentApprovalDO::getToolCallId, toolCalls.stream().map(AgentToolCallDTO::toolCallId).toList())
                    .orderByAsc(AgentApprovalDO::getCreatedAt))
                .stream().map(this::toApprovalDTO).toList();
            return new AgentMessageDTO(message.getMessageId(), message.getSessionId(), message.getRole(), message.getContent(),
                jsonService.value(message.getMetadataJson()), toolCalls, approvals, message.getCreatedAt());
        }).toList();
    }

    public List<AgentToolCallDTO> listToolCalls(Long sessionId) {
        return toolCallMapper.selectList(Wrappers.<AgentToolCallDO>lambdaQuery()
                .eq(AgentToolCallDO::getSessionId, sessionId)
                .orderByDesc(AgentToolCallDO::getCreatedAt))
            .stream().map(this::toToolDTO).toList();
    }

    private AgentToolCallDTO toToolDTO(AgentToolCallDO row) {
        return new AgentToolCallDTO(row.getToolCallId(), row.getSessionId(), row.getMessageId(), row.getClientToolCallId(),
            row.getToolName(), row.getDisplayName(), jsonService.value(row.getArgumentsJson()), jsonService.value(row.getResultJson()),
            row.getStatus(), row.getErrorMessage(), row.getDurationMs(), row.getCreatedAt());
    }

    private AgentApprovalDTO toApprovalDTO(AgentApprovalDO row) {
        return new AgentApprovalDTO(row.getApprovalId(), row.getSessionId(), row.getToolCallId(), row.getToolName(), row.getReason(),
            jsonService.value(row.getArgumentsJson()), row.getStatus(), row.getComment(), row.getDecidedAt(), row.getCreatedAt());
    }
}
