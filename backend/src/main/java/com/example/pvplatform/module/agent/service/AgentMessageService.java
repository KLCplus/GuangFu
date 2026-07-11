package com.example.pvplatform.module.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.module.agent.entity.AgentMessageDO;
import com.example.pvplatform.module.agent.mapper.AgentMessageMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentMessageService {
    private final AgentMessageMapper messageMapper;
    private final AgentJsonService jsonService;

    public AgentMessageService(AgentMessageMapper messageMapper, AgentJsonService jsonService) {
        this.messageMapper = messageMapper;
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
}
