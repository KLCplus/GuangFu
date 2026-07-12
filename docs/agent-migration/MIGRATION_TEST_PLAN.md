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

