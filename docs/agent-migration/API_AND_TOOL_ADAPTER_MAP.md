# API And Tool Adapter Map

| Tool | Spring source today | Internal gateway target | Runtime use |
|---|---|---|---|
| `station.detail` | `StationDetailTool` / `StationService.detail` | `/api/internal/agent/tools/station.detail/execute` | Required for first chain |
| `weather.current` | `WeatherCurrentTool` / `WeatherService.current` | `/api/internal/agent/tools/weather.current/execute` | Required for first chain |
| `prediction.list` | `PredictionListTool` / `PredictionService.history` | `/api/internal/agent/tools/prediction.list/execute` | Required for first chain |
| `prediction.detail` | `PredictionDetailTool` / `PredictionService.detail/results` | `/api/internal/agent/tools/prediction.detail/execute` | Optional follow-up when list has records |
| `report.generate` | `ReportGenerateTool` / `AnalysisService.report` | `/api/internal/agent/tools/report.generate/execute` | Approval required |

Gateway request:

```json
{
  "sessionId": 1,
  "userId": 1,
  "username": "agent-runtime",
  "roles": ["USER"],
  "arguments": {},
  "context": {},
  "approved": false
}
```

Gateway response:

```json
{
  "success": true,
  "summary": "...",
  "highlights": [],
  "data": {},
  "error": null
}
```

Approval-required gateway response:

```json
{
  "success": false,
  "summary": "工具 ... 会执行写操作，需要用户确认后才能继续。",
  "highlights": ["APPROVAL_REQUIRED"],
  "data": {
    "approvalRequired": true,
    "approvalId": 8,
    "toolCallId": 91,
    "clientToolCallId": "tc_...",
    "toolName": "report.generate",
    "reason": "...",
    "arguments": {}
  },
  "error": "APPROVAL_REQUIRED"
}
```

Approval continuation:

- User decision: `POST /api/agent/approvals/{approvalId}/approve`
- Continue execution: `POST /api/agent/chat/stream` with `approvalId`
- Migrated proxy reuses the original pending `agent_tool_call` row, executes the approved tool, persists the assistant final message, and emits `tool_call`, `tool_result`, and `final`.

Internal LLM gateway:

- `POST /api/internal/llm/v1/chat/completions`
- Header: `X-Agent-Internal-Token`
- Request/response shape: OpenAI-compatible Chat Completions.
- The gateway forces the configured DeepSeek model and key from Spring Boot environment.

Internal Memory gateway:

- `POST /api/internal/agent/memory/list`
- `POST /api/internal/agent/memory/write`
- Header: `X-Agent-Internal-Token`
- Runtime provides `userId`, `username`, and `roles`; Spring binds user context before reading or writing MySQL.
- Writes are restricted to approved memory types and rejected when source/value contains sensitive markers.
