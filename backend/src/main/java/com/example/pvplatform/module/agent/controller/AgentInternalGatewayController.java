package com.example.pvplatform.module.agent.controller;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.agent.dto.InternalToolExecuteRequest;
import com.example.pvplatform.module.agent.dto.InternalToolExecuteResponse;
import com.example.pvplatform.module.agent.entity.AgentApprovalDO;
import com.example.pvplatform.module.agent.entity.AgentToolCallDO;
import com.example.pvplatform.module.agent.service.AgentApprovalService;
import com.example.pvplatform.module.agent.service.AgentToolService;
import com.example.pvplatform.module.agent.tool.AgentTool;
import com.example.pvplatform.module.agent.tool.AgentToolRegistry;
import com.example.pvplatform.module.agent.tool.ToolExecutionContext;
import com.example.pvplatform.module.agent.tool.ToolExecutionResult;
import com.example.pvplatform.security.SecurityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/agent")
public class AgentInternalGatewayController {
    private final AgentToolRegistry toolRegistry;
    private final AgentToolService toolService;
    private final AgentApprovalService approvalService;

    @Value("${agent.internal-token:}")
    private String internalToken;

    public AgentInternalGatewayController(AgentToolRegistry toolRegistry, AgentToolService toolService,
                                          AgentApprovalService approvalService) {
        this.toolRegistry = toolRegistry;
        this.toolService = toolService;
        this.approvalService = approvalService;
    }

    @PostMapping("/tools/{toolName}/execute")
    public InternalToolExecuteResponse executeTool(@PathVariable String toolName,
                                                   @RequestHeader(value = "X-Agent-Internal-Token", required = false) String token,
                                                   @RequestBody InternalToolExecuteRequest request) {
        requireInternalToken(token);
        if (request.userId() == null) {
            throw new BusinessException(401, "内部工具调用缺少 userId");
        }

        SecurityUser user = new SecurityUser(
            request.userId(),
            request.username() == null || request.username().isBlank() ? "agent-runtime" : request.username(),
            1,
            request.roles() == null || request.roles().isEmpty() ? List.of("USER") : request.roles()
        );
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            AgentTool tool = toolRegistry.require(toolName);
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            AgentToolCallDO row = toolService.createPending(request.sessionId(), null, tool, args);
            if (tool.requiresApproval() && !Boolean.TRUE.equals(request.approved())) {
                toolService.markAwaitingApproval(row);
                String reason = "工具 " + tool.displayName() + " 会执行写操作，需要用户确认后才能继续。";
                AgentApprovalDO approval = approvalService.create(request.sessionId(), row, reason);
                return InternalToolExecuteResponse.approvalRequired(
                    approval.getApprovalId(),
                    row.getToolCallId(),
                    row.getClientToolCallId(),
                    tool.name(),
                    reason,
                    args
                );
            }
            ToolExecutionResult result = toolService.execute(
                row,
                tool,
                new ToolExecutionContext(request.userId(), request.sessionId(), null, user),
                args
            );
            return new InternalToolExecuteResponse(
                result.success(),
                result.summary(),
                result.highlights(),
                result.data(),
                result.errorMessage()
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void requireInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BusinessException(500, "内部 Agent Gateway 未配置 token");
        }
        if (token == null || !internalToken.equals(token)) {
            throw new BusinessException(401, "内部 Agent Gateway token 无效");
        }
    }
}
