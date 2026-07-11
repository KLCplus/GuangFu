package com.example.pvplatform.module.agent.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.agent.dto.AgentApprovalDecision;
import com.example.pvplatform.module.agent.service.AgentApprovalService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/approvals")
public class AgentApprovalController {
    private final AgentApprovalService approvalService;

    public AgentApprovalController(AgentApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{approvalId}/approve")
    public Result<?> approve(@PathVariable Long approvalId, @RequestBody AgentApprovalDecision decision) {
        return Result.success(approvalService.decide(approvalId, decision));
    }
}
