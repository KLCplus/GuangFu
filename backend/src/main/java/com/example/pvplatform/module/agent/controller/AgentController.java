package com.example.pvplatform.module.agent.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.agent.dto.AgentChatRequest;
import com.example.pvplatform.module.agent.service.AgentMessageService;
import com.example.pvplatform.module.agent.service.AgentOrchestratorService;
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
    private final AgentOrchestratorService orchestratorService;

    public AgentController(AgentSessionService sessionService, AgentMessageService messageService,
                           AgentToolRegistry toolRegistry, AgentOrchestratorService orchestratorService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.toolRegistry = toolRegistry;
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/sessions")
    public Result<?> createSession(@RequestBody(required = false) Map<String, Object> body) {
        Object title = body == null ? null : body.get("title");
        return Result.success(sessionService.create(title == null ? null : String.valueOf(title)));
    }

    @GetMapping("/sessions")
    public Result<?> sessions(@RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "20") int pageSize,
                              @RequestParam(required = false) Boolean archived) {
        return Result.success(sessionService.list(pageNum, pageSize, archived));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<?> messages(@PathVariable Long sessionId) {
        sessionService.requireOwned(sessionId);
        return Result.success(messageService.list(sessionId));
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
            orchestratorService.chat(request, emitter);
            SecurityContextHolder.clearContext();
        });
        return emitter;
    }
}
