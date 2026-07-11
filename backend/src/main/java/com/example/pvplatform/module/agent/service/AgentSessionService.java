package com.example.pvplatform.module.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.AgentSessionDTO;
import com.example.pvplatform.module.agent.entity.AgentSessionDO;
import com.example.pvplatform.module.agent.mapper.AgentSessionMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AgentSessionService {
    private final AgentSessionMapper sessionMapper;

    public AgentSessionService(AgentSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    public AgentSessionDTO create(String title) {
        Long userId = SecurityUtils.requireCurrentUserId();
        AgentSessionDO row = new AgentSessionDO();
        row.setUserId(userId);
        row.setTitle(title == null || title.isBlank() ? "新的 Agent 会话" : title.trim());
        row.setArchived(false);
        row.setPinned(false);
        row.setStatus("ACTIVE");
        row.setDeleted(false);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(row.getCreatedAt());
        sessionMapper.insert(row);
        return toDTO(row);
    }

    public AgentSessionDO requireOwned(Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(400, "缺少会话 ID");
        }
        Long userId = SecurityUtils.requireCurrentUserId();
        AgentSessionDO row = sessionMapper.selectOne(Wrappers.<AgentSessionDO>lambdaQuery()
            .eq(AgentSessionDO::getSessionId, sessionId)
            .eq(AgentSessionDO::getUserId, userId)
            .eq(AgentSessionDO::getDeleted, false));
        if (row == null) {
            throw new BusinessException(404, "Agent 会话不存在");
        }
        return row;
    }

    public AgentSessionDO ensure(Long sessionId, String titleHint) {
        if (sessionId != null) {
            return requireOwned(sessionId);
        }
        AgentSessionDTO dto = create(titleHint == null || titleHint.isBlank() ? "Agent 会话" : summarize(titleHint));
        return requireOwned(dto.sessionId());
    }

    public PageResult<AgentSessionDTO> list(int pageNum, int pageSize, Boolean archived, Boolean pinned, String keyword) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数不合法");
        }
        Long userId = SecurityUtils.requireCurrentUserId();
        Page<AgentSessionDO> page = sessionMapper.selectPage(new Page<>(pageNum, pageSize),
            Wrappers.<AgentSessionDO>lambdaQuery()
                .eq(AgentSessionDO::getUserId, userId)
                .eq(AgentSessionDO::getDeleted, false)
                .eq(archived != null, AgentSessionDO::getArchived, archived)
                .eq(pinned != null, AgentSessionDO::getPinned, pinned)
                .like(keyword != null && !keyword.isBlank(), AgentSessionDO::getTitle, keyword == null ? null : keyword.trim())
                .orderByDesc(AgentSessionDO::getPinned)
                .orderByDesc(AgentSessionDO::getUpdatedAt));
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::toDTO).toList());
    }

    public AgentSessionDTO archive(Long sessionId, boolean archived) {
        return updateFlags(sessionId, archived, null, null);
    }

    public AgentSessionDTO pin(Long sessionId, boolean pinned) {
        return updateFlags(sessionId, null, pinned, null);
    }

    public AgentSessionDTO rename(Long sessionId, String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "会话标题不能为空");
        }
        return updateFlags(sessionId, null, null, title.trim());
    }

    public void delete(Long sessionId) {
        requireOwned(sessionId);
        AgentSessionDO update = new AgentSessionDO();
        update.setSessionId(sessionId);
        update.setDeleted(true);
        update.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(update);
    }

    private AgentSessionDTO updateFlags(Long sessionId, Boolean archived, Boolean pinned, String title) {
        AgentSessionDO current = requireOwned(sessionId);
        AgentSessionDO update = new AgentSessionDO();
        update.setSessionId(sessionId);
        if (archived != null) update.setArchived(archived);
        if (pinned != null) update.setPinned(pinned);
        if (title != null) update.setTitle(title);
        update.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(update);
        if (archived != null) current.setArchived(archived);
        if (pinned != null) current.setPinned(pinned);
        if (title != null) current.setTitle(title);
        current.setUpdatedAt(update.getUpdatedAt());
        return toDTO(current);
    }

    public void touch(Long sessionId) {
        AgentSessionDO update = new AgentSessionDO();
        update.setSessionId(sessionId);
        update.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(update);
    }

    public String summarize(String text) {
        String value = text == null ? "Agent 会话" : text.trim().replaceAll("\s+", " ");
        return value.length() <= 30 ? value : value.substring(0, 30);
    }

    private AgentSessionDTO toDTO(AgentSessionDO row) {
        return new AgentSessionDTO(row.getSessionId(), row.getTitle(), row.getArchived(), row.getPinned(), row.getStatus(), row.getCreatedAt(), row.getUpdatedAt());
    }
}
