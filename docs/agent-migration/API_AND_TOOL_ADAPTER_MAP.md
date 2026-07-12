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
  "arguments": {},
  "context": {}
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

