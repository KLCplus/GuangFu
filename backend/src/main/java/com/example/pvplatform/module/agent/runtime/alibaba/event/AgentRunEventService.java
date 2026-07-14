package com.example.pvplatform.module.agent.runtime.alibaba.event;

import com.example.pvplatform.module.agent.runtime.alibaba.AgentRunContext;
import com.example.pvplatform.module.agent.service.AgentJsonService;
import com.example.pvplatform.security.SecurityUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AgentRunEventService {
    private final com.example.pvplatform.module.agent.mapper.AgentRunEventMapper mapper;
    private final AgentJsonService json;
    public AgentRunEventService(com.example.pvplatform.module.agent.mapper.AgentRunEventMapper mapper, AgentJsonService json) { this.mapper = mapper; this.json = json; }
    public void save(AgentRunContext context, String type, Object payload) {
        save(context, type, null, null, null, null, payload);
    }

    public void save(AgentRunContext context, String type, String nodeName, String toolName,
                     Long toolCallId, String status, Object payload) {
        AgentRunEventDO event = new AgentRunEventDO();
        event.setRunId(context.runId()); event.setSessionId(context.session().getSessionId());
        event.setMessageId(context.userMessage().getMessageId()); event.setUserId(SecurityUtils.requireCurrentUserId());
        event.setSequenceNo(context.eventSequence().incrementAndGet());
        event.setEventType(type); event.setNodeName(nodeName); event.setToolName(toolName);
        event.setToolCallId(toolCallId); event.setEventStatus(status);
        event.setPayloadJson(json.json(payload)); event.setCreatedAt(LocalDateTime.now());
        mapper.insert(event);
    }

    public List<AgentRunEventDO> latestForSession(Long sessionId) {
        AgentRunEventDO latest = mapper.selectOne(Wrappers.<AgentRunEventDO>lambdaQuery()
            .eq(AgentRunEventDO::getSessionId, sessionId)
            .orderByDesc(AgentRunEventDO::getCreatedAt)
            .last("LIMIT 1"));
        if (latest == null) return List.of();
        return mapper.selectList(Wrappers.<AgentRunEventDO>lambdaQuery()
            .eq(AgentRunEventDO::getRunId, latest.getRunId())
            .orderByAsc(AgentRunEventDO::getSequenceNo));
    }

    public Map<String, Object> recovery(Long sessionId) {
        List<AgentRunEventDO> events = latestForSession(sessionId);
        if (events.isEmpty()) return Map.of("events", List.of(), "state", "NONE");
        AgentRunEventDO last = events.get(events.size() - 1);
        String state = switch (last.getEventType()) {
            case "run_completed", "error", "approval_required" -> last.getEventType();
            default -> "interrupted";
        };
        return Map.of("runId", last.getRunId(), "events", events, "state", state);
    }
}
