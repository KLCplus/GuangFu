# Migration Test Plan

## Unit

- Skill loader parses all `agent-runtime/skills/*/SKILL.md`.
- Skill router selects `station-inspection` for the first vertical chain.
- Tool router orders station/weather/prediction calls.
- Memory extractor rejects sensitive values.
- UI instruction validator rejects unknown components.

## Integration

- Spring internal LLM gateway proxies DeepSeek without logging key.
- Spring internal tool gateway executes `station.detail`, `weather.current`, `prediction.list`, `prediction.detail`.
- Spring internal tool gateway creates approval records instead of executing `report.generate` before approval.
- Migrated proxy executes approved pending `report.generate` calls when `approvalId` is supplied.
- `AGENT_RUNTIME_MODE=legacy` continues current behavior.
- `AGENT_RUNTIME_MODE=migrated` calls `agent-runtime`.

## E2E

- Query station.
- Query weather.
- Analyze prediction.
- Comprehensive first chain.
- Report confirmation.
- User rejection.
- No permission.
- Tool failure.
- LLM failure.
- Refresh and restore session.

Implemented E2E script:

```bash
node scripts/migrated-agent-e2e.mjs
```

The script expects backend and `agent-runtime` to be running in migrated mode. It validates:

- migrated runtime `started` and `run_completed` events;
- whitelisted photovoltaic UI instructions: `StationSummaryCard`, `WeatherImpactCard`, `PredictionTrendCard`;
- report approval pause with `ApprovalActionCard` and `approval_required`;
- approval continuation with successful `report.generate` and returned `reportId`.
