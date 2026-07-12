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

