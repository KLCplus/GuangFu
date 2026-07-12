package com.example.pvplatform.module.agent.controller;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.AgentMemoryDTO;
import com.example.pvplatform.module.agent.dto.InternalMemoryListRequest;
import com.example.pvplatform.module.agent.dto.InternalMemoryWriteRequest;
import com.example.pvplatform.module.agent.service.AgentMemoryService;
import com.example.pvplatform.security.SecurityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/internal/agent/memory")
public class AgentInternalMemoryController {
    private final AgentMemoryService memoryService;

    @Value("${agent.internal-token:}")
    private String internalToken;

    public AgentInternalMemoryController(AgentMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping("/write")
    public AgentMemoryDTO write(@RequestHeader(value = "X-Agent-Internal-Token", required = false) String token,
                                @RequestBody InternalMemoryWriteRequest request) {
        requireInternalToken(token);
        bindUser(request.userId(), request.username(), request.roles());
        try {
            return memoryService.write(
                request.memoryType(),
                request.memoryKey(),
                request.value(),
                request.sourceMessage(),
                request.confidence(),
                request.enabled()
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @PostMapping("/list")
    public List<AgentMemoryDTO> list(@RequestHeader(value = "X-Agent-Internal-Token", required = false) String token,
                                     @RequestBody InternalMemoryListRequest request) {
        requireInternalToken(token);
        bindUser(request.userId(), request.username(), request.roles());
        try {
            return memoryService.list(request.memoryType(), request.enabledOnly(), request.limit());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void bindUser(Long userId, String username, List<String> roles) {
        if (userId == null) {
            throw new BusinessException(401, "内部记忆调用缺少 userId");
        }
        SecurityUser user = new SecurityUser(
            userId,
            username == null || username.isBlank() ? "agent-runtime" : username,
            1,
            roles == null || roles.isEmpty() ? List.of("USER") : roles
        );
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void requireInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BusinessException(500, "内部 Agent Memory Gateway 未配置 token");
        }
        if (token == null || !internalToken.equals(token)) {
            throw new BusinessException(401, "内部 Agent Memory Gateway token 无效");
        }
    }
}
