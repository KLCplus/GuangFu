# Spring AI Alibaba Agent Migration

## Runtime and rollback

Alibaba is now the only supported Agent runtime. The former `legacy` and `migrated` runtime switches and proxy entrypoints have been removed.

The Alibaba runtime uses `ReactAgent` with Spring AI's DeepSeek `ChatModel`. Required production configuration is `DEEPSEEK_API_KEY`; optional values include `DEEPSEEK_BASE_URL`, `DEEPSEEK_MODEL`, `AGENT_MAX_MODEL_CALLS`, `AGENT_MAX_TOOL_CALLS`, and `AGENT_TOOL_TIMEOUT_MS`.

## Architecture

The unchanged `/api/agent/chat/stream` endpoint enters the Alibaba runtime directly. It selects a packaged Skill, builds a constrained tool set, creates Spring AI `ToolCallback` adapters around the existing `AgentToolRegistry`, and runs `ReactAgent`. Tool execution remains in `AgentToolService`; permissions stay in the existing business tools and confirmation records remain in `AgentApprovalService`.

`agent_message` remains the conversation audit source and `agent_memory` remains long-term memory. `agent_run_event` records non-token execution events for timeline recovery. The recovery endpoint is `GET /api/agent/sessions/{sessionId}/run-recovery`.

## Safety constraints

Business tool names are converted from dots to underscores only for the model-facing callback. Database records retain original names. Write tools create `agent_approval` records and are never executed before user approval. Server-side context provides user, session and message identifiers; they are not model parameters. The UI instruction factory only emits whitelisted components.

## Verification

Run from `backend/`:

```bash
mvn -q -DskipTests compile
mvn -q test
```

Run frontend and retained Python runtime checks from the repository root:

```bash
cd web-frontend && npm run build
cd ../agent-runtime && python -m pytest -q tests/test_runtime_foundation.py
```

Live DeepSeek E2E verification requires a configured API key and a database containing authorized station data. The automated suite intentionally disables the external model.

Local verification completed on 2026-07-14:

- `mvn -q -DskipTests compile`
- `mvn -q test`
- `mvn -q -Dtest=AgentSkillSelectorTest,ToolCallbackNameTest,SlashCommandParserTest,StationPermissionServiceTest test`
- `npm run build` in `web-frontend/`
- `python -m pytest -q tests/test_runtime_foundation.py` in `agent-runtime/` (20 passed)

The ten live E2E acceptance cases are defined in [e2e-test-matrix.md](agent-migration/e2e-test-matrix.md). Live verification on 2026-07-14 used an isolated user with a read grant on station 1 and the configured DeepSeek key. It verified the complete read-analysis chain, context follow-up, default-station memory, terminal missing-station handling, report approval creation, approved and rejected report execution, model timeout degradation, and `run-recovery`. The weather-removal variation was not executed against shared data because it would require deleting or mutating records; its failure path is covered by the tool-error and UI recovery implementation.
