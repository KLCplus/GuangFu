package com.example.pvplatform.module.agent.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.agent.dto.AgentChatRequest;
import com.example.pvplatform.module.agent.service.AgentMessageService;
import com.example.pvplatform.module.agent.runtime.alibaba.AlibabaAgentRuntimeService;
import com.example.pvplatform.module.agent.runtime.alibaba.event.AgentRunEventService;
import com.example.pvplatform.module.agent.service.AgentSessionService;
import com.example.pvplatform.module.agent.tool.AgentToolRegistry;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentSessionService sessionService;
    private final AgentMessageService messageService;
    private final AgentToolRegistry toolRegistry;
    private final AlibabaAgentRuntimeService runtime;
    private final AgentRunEventService runEvents;

    public AgentController(AgentSessionService sessionService, AgentMessageService messageService,
                           AgentToolRegistry toolRegistry, AlibabaAgentRuntimeService runtime,
                           AgentRunEventService runEvents) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.toolRegistry = toolRegistry;
        this.runtime = runtime;
        this.runEvents = runEvents;
    }

    @PostMapping("/sessions")
    public Result<?> createSession(@RequestBody(required = false) Map<String, Object> body) {
        Object title = body == null ? null : body.get("title");
        return Result.success(sessionService.create(title == null ? null : String.valueOf(title)));
    }

    @GetMapping("/sessions")
    public Result<?> sessions(@RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size,
                              @RequestParam(required = false) Integer pageNum,
                              @RequestParam(required = false) Integer pageSize,
                              @RequestParam(required = false) Boolean archived,
                              @RequestParam(required = false) Boolean pinned,
                              @RequestParam(required = false) String keyword) {
        int actualPage = page != null ? page : (pageNum == null ? 1 : pageNum);
        int actualSize = size != null ? size : (pageSize == null ? 20 : pageSize);
        return Result.success(sessionService.list(actualPage, actualSize, archived, pinned, keyword));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<?> messages(@PathVariable Long sessionId) {
        sessionService.requireOwned(sessionId);
        return Result.success(messageService.listWithDetails(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/tool-calls")
    public Result<?> toolCalls(@PathVariable Long sessionId) {
        sessionService.requireOwned(sessionId);
        return Result.success(messageService.listToolCalls(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/run-recovery")
    public Result<?> runRecovery(@PathVariable Long sessionId) {
        sessionService.requireOwned(sessionId);
        return Result.success(runEvents.recovery(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/archive")
    public Result<?> archive(@PathVariable Long sessionId) {
        return Result.success(sessionService.archive(sessionId, true));
    }

    @PostMapping("/sessions/{sessionId}/unarchive")
    public Result<?> unarchive(@PathVariable Long sessionId) {
        return Result.success(sessionService.archive(sessionId, false));
    }

    @PostMapping("/sessions/{sessionId}/pin")
    public Result<?> pin(@PathVariable Long sessionId) {
        return Result.success(sessionService.pin(sessionId, true));
    }

    @PostMapping("/sessions/{sessionId}/unpin")
    public Result<?> unpin(@PathVariable Long sessionId) {
        return Result.success(sessionService.pin(sessionId, false));
    }

    @PutMapping("/sessions/{sessionId}")
    public Result<?> rename(@PathVariable Long sessionId, @RequestBody Map<String, Object> body) {
        Object title = body == null ? null : body.get("title");
        return Result.success(sessionService.rename(sessionId, title == null ? null : String.valueOf(title)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<?> delete(@PathVariable Long sessionId) {
        sessionService.delete(sessionId);
        return Result.success(Map.of("deleted", true));
    }

    @GetMapping("/tools")
    public Result<?> tools() {
        return Result.success(toolRegistry.list());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            runtime.chat(request, emitter);
            SecurityContextHolder.clearContext();
        });
        return emitter;
    }
}
