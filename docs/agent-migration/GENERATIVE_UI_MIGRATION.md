# Generative UI Migration

Reference pattern:

- Catalog definitions: `definitions.ts`
- Whitelist renderers: `renderers.tsx`
- Frontend tools/HITL/default tool rendering: `use-generative-ui-examples.tsx`
- Fixed A2UI operation tool: `a2ui_fixed_schema.py`

Photovoltaic UI schema:

```json
{
  "component": "WeatherImpactCard",
  "props": {
    "stationName": "成都联调光伏电站",
    "weather": "多云",
    "temperature": 35,
    "cloudCover": 48,
    "impact": "云量变化可能带来功率波动"
  }
}
```

Whitelist components:

- `StationSummaryCard`
- `WeatherImpactCard`
- `PredictionTrendCard`
- `PowerMetricCard`
- `RiskAssessmentCard`
- `MaintenanceRecommendationCard`
- `ReportPreviewCard`
- `ApprovalActionCard`
- `ToolProgressCard`
- `ErrorRecoveryCard`

The model/runtime may emit component names and props only. It must never emit HTML, JavaScript, Vue templates, or CSS.

## Current Checkpoint

`agent-runtime/server.py` emits `ui_instruction` events using the schema above.

Spring `/api/agent/chat/stream` forwards those events when `AGENT_RUNTIME_MODE=migrated`.

The Vue `/reports` page still needs a whitelist renderer for the migrated component names.
