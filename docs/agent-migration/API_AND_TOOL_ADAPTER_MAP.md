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

Internal LLM gateway:

- `POST /api/internal/llm/v1/chat/completions`
- Header: `X-Agent-Internal-Token`
- Request/response shape: OpenAI-compatible Chat Completions.
- The gateway forces the configured DeepSeek model and key from Spring Boot environment.
