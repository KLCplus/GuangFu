# Security And Permission

## Current Findings

- Local run used `SECURITY_DEBUG_OPEN=true`, so no-token requests succeeded. This is acceptable only for local debugging and cannot be used for acceptance.
- Existing tools generally check permissions inside tool execution.
- Registry does not filter tools by current user role before prompt construction.

## Required Controls

- `agent-runtime` never accesses MySQL directly.
- `agent-runtime` never holds DeepSeek API keys.
- Spring Agent Gateway resolves current user and enforces ownership/role checks.
- Internal LLM gateway is accessible only to runtime using internal token/signature.
- Tool calls record user, session, duration, status, arguments, redacted result, and error.
- Write/cost/admin tools require approval before execution.

## Implemented Approval Controls

- `report.generate` is marked as a write tool and requires approval.
- Internal tool gateway now creates a pending `agent_tool_call`, marks it `AWAITING_APPROVAL`, creates `agent_approval`, and returns `APPROVAL_REQUIRED`.
- `agent-runtime` converts that response into a whitelisted `ApprovalActionCard` plus `approval_required` stream event.
- Migrated Spring proxy handles `approvalId` continuation by checking the current user owns the approval, reloading the original pending tool call, and executing it only after status becomes `APPROVED`.
- Local smoke confirmed that report generation did not execute before approval and did execute after approval.
