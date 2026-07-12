# Memory Migration

Reference pattern: `llm_app_personalized_memory/llm_app_memory.py`.

Implemented rules:

- Read enabled user memory before planning through Spring internal Memory Gateway.
- Use `default_station` memory to fill station context only when the user request does not specify a station.
- Inject only enabled, relevant, non-sensitive memories.
- Separate session messages from long-term memory.
- Write memory only through typed extraction rules in `agent-runtime/pv_agent_runtime/memory.py`.
- Never let the LLM directly write arbitrary long-term memory.

Implemented MySQL shape:

| Field | Required | Notes |
|---|---:|---|
| `memory_id` | yes | primary key |
| `user_id` | yes | current platform user |
| `memory_type` | yes | user_preference/default_station/report_format/frequent_metric/recent_context/station_focus/ops_preference |
| `memory_key` | yes | update key within a memory type |
| `value_json` | yes | structured value |
| `source_message` | yes | traceability back to source text |
| `confidence` | yes | decimal, 0-1 |
| `enabled` | yes | user/admin can disable |
| `created_at` | yes | audit |
| `updated_at` | yes | audit |

Implemented files:

- `backend/src/main/resources/sql/init.sql`
- `backend/src/main/resources/sql/agent_tables_migration.sql`
- `backend/src/main/java/com/example/pvplatform/module/agent/entity/AgentMemoryDO.java`
- `backend/src/main/java/com/example/pvplatform/module/agent/mapper/AgentMemoryMapper.java`
- `backend/src/main/java/com/example/pvplatform/module/agent/service/AgentMemoryService.java`
- `backend/src/main/java/com/example/pvplatform/module/agent/controller/AgentInternalMemoryController.java`
- `agent-runtime/pv_agent_runtime/memory.py`
- `agent-runtime/pv_agent_runtime/runtime.py`

Internal endpoints:

- `POST /api/internal/agent/memory/list`
- `POST /api/internal/agent/memory/write`
- Header: `X-Agent-Internal-Token`
- Runtime must provide `userId`, `username`, and `roles`; Spring binds these into `SecurityContext`.

Never store API keys, tokens, passwords, private keys, identity documents, payment credentials, or raw environment values.
