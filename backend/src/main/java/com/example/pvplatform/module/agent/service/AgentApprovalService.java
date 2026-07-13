package com.example.pvplatform.module.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.AgentApprovalDecision;
import com.example.pvplatform.module.agent.entity.AgentApprovalDO;
import com.example.pvplatform.module.agent.entity.AgentToolCallDO;
import com.example.pvplatform.module.agent.mapper.AgentApprovalMapper;
import com.example.pvplatform.module.agent.mapper.AgentToolCallMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AgentApprovalService {
    private final AgentApprovalMapper approvalMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final AgentJsonService jsonService;

    public AgentApprovalService(AgentApprovalMapper approvalMapper, AgentToolCallMapper toolCallMapper,
                                AgentJsonService jsonService) {
        this.approvalMapper = approvalMapper;
        this.toolCallMapper = toolCallMapper;
        this.jsonService = jsonService;
    }

    public AgentApprovalDO create(Long sessionId, AgentToolCallDO toolCall, String reason) {
        AgentApprovalDO row = new AgentApprovalDO();
        row.setSessionId(sessionId);
        row.setToolCallId(toolCall.getToolCallId());
        row.setUserId(SecurityUtils.requireCurrentUserId());
        row.setToolName(toolCall.getToolName());
        row.setStatus("PENDING");
        row.setReason(reason);
        row.setArgumentsJson(toolCall.getArgumentsJson());
        row.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(row);
        return row;
    }

    public AgentApprovalDO decide(Long approvalId, AgentApprovalDecision decision) {
        Long userId = SecurityUtils.requireCurrentUserId();
        AgentApprovalDO row = approvalMapper.selectOne(Wrappers.<AgentApprovalDO>lambdaQuery()
            .eq(AgentApprovalDO::getApprovalId, approvalId)
            .eq(AgentApprovalDO::getUserId, userId));
        if (row == null) {
            throw new BusinessException(404, "确认请求不存在");
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new BusinessException(400, "确认请求已处理");
        }
        boolean approved = Boolean.TRUE.equals(decision.approved());
        row.setStatus(approved ? "APPROVED" : "REJECTED");
        row.setComment(decision.comment());
        row.setDecidedAt(LocalDateTime.now());
        approvalMapper.updateById(row);
        return row;
    }

    public AgentApprovalDO requireForContinuation(Long approvalId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        AgentApprovalDO row = approvalMapper.selectOne(Wrappers.<AgentApprovalDO>lambdaQuery()
            .eq(AgentApprovalDO::getApprovalId, approvalId)
            .eq(AgentApprovalDO::getUserId, userId));
        if (row == null) {
            throw new BusinessException(404, "确认请求不存在");
        }
        return row;
    }

    public AgentApprovalDO findLatestPending(Long sessionId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        if (sessionId == null) {
            return null;
        }
        return approvalMapper.selectOne(Wrappers.<AgentApprovalDO>lambdaQuery()
            .eq(AgentApprovalDO::getSessionId, sessionId)
            .eq(AgentApprovalDO::getUserId, userId)
            .eq(AgentApprovalDO::getStatus, "PENDING")
            .orderByDesc(AgentApprovalDO::getCreatedAt)
            .last("LIMIT 1"));
    }

    public AgentToolCallDO requireToolCall(AgentApprovalDO approval) {
        AgentToolCallDO row = toolCallMapper.selectById(approval.getToolCallId());
        if (row == null) {
            throw new BusinessException(404, "待确认工具调用不存在");
        }
        return row;
    }
}
