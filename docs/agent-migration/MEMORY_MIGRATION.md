# Memory Migration

Reference pattern: `llm_app_personalized_memory/llm_app_memory.py`.

Migrated rules:

- Read user memory before planning.
- Inject only enabled, relevant, non-sensitive memories.
- Separate session messages from long-term memory.
- Write memory only through typed extraction rules.

Required MySQL shape:

| Field | Required | Notes |
|---|---:|---|
| `memory_id` | yes | primary key |
| `user_id` | yes | current platform user |
| `memory_type` | yes | preference/default_station/report_format/metric/recent_context/station_focus/ops_preference |
| `memory_value_json` | yes | structured value |
| `source_message_id` | yes | traceability |
| `confidence` | yes | decimal |
| `enabled` | yes | user/admin can disable |
| `created_at` | yes | audit |
| `updated_at` | yes | audit |

Never store API keys, tokens, passwords, private keys, identity documents, payment credentials, or raw environment values.

