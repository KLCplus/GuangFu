# Agent Test Report

## Automated Checks

| Check | Command | Result |
| --- | --- | --- |
| Backend compile | `mvn -Dmaven.repo.local=.m2/repository -DskipTests compile` | PASS |
| Backend tests | `mvn -Dmaven.repo.local=.m2/repository test` | PASS, 64 run, 0 failures/errors, 2 skipped |
| Frontend build | `npm run build` in `web-frontend` | PASS |

Frontend build still reports existing Rolldown warnings from `@vueuse/core` pure annotations and large chunks. They do not fail the build.

## Functional Scenarios

| Scenario | Input | Expected calls/events | Status |
| --- | --- | --- | --- |
| Read-only station query | `查看 2 号电站的信息` | `started`, `thinking`, `plan`, `tool_call station.detail`, `tool_result`, `final` | PASS. Runtime called `station.detail`; station 2 missing, then Agent recovered with `station.list` and returned real accessible station data. |
| Multi-tool analysis | `分析 2 号电站今天功率波动，结合天气和预测任务` | `station.detail`, `weather.current`, `prediction.list` or `prediction.detail`, final answer using tool results | Implemented by JSON ReAct loop |
| Missing parameter | `帮我分析这个电站` without context | DeepSeek should return `ask_user` or select `station.list` | Implemented prompt and `ask_user` handling |
| Write confirmation | `生成 1 号电站综合分析报告` | `approval_required` before `report.generate`; no write before approval | PASS. Runtime emitted `approval_required` with `approvalId=1`. |
| Rejected confirmation | User rejects approval | Pending tool is not executed; final says canceled | Implemented in approval continuation; not manually rerun after approval success test. |
| Interface failure | Invalid `taskId` | Tool returns structured `tool_result` failure and Agent summarizes | Implemented in `AgentToolService` error handling |
| Permission checks | User asks for inaccessible station/admin action | Existing station/report services enforce permissions; admin tools not registered | Implemented for registered tools |
| SSE progress | Any Agent task | Page displays started/thinking/tool/final/error events | PASS in curl stream and wired in `/reports`. |

## Manual Runtime Notes

Applied the local migration successfully before runtime verification. Before testing against a fresh local database, apply:

```bash
mysql < backend/src/main/resources/sql/agent_tables_migration.sql
```

Use the actual project DB connection from local env. Do not commit local env files or expose API keys/tokens in debug output.


## Runtime Verification

- `GET /api/agent/tools`: PASS, returned 10 enabled tools.
- `GET /api/agent/sessions`: PASS, returned persisted session page.
- `POST /api/agent/chat/stream` with `查看 2 号电站的信息`: PASS, streamed `started`, `thinking`, `plan`, `tool_call`, `tool_result`, `final`; Agent recovered from missing station 2 by calling `station.list`.
- `POST /api/agent/chat/stream` with `生成 1 号电站综合分析报告`: PASS, streamed `approval_required`; tool was not executed before approval.
- `POST /api/agent/approvals/1/approve` then continuation stream with `approvalId=1`: PASS, executed `report.generate` and created report `reportId=1`.
- Runtime caveat: PVOutput startup sync hit upstream free quota (`Exceeded 60 requests per hour`), unrelated to Agent endpoints.
