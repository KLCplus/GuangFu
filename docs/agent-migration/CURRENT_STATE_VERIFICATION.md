# Current State Verification

Verified on `2026-07-12`, branch `feat/awesome-llm-agent-migration`.

## Summary

The current Agent is a real Spring Boot tool-calling chat loop, not a pure mock. It has SSE, sessions, messages, tool calls, approvals, DeepSeek JSON decisions, slash parsing, and multiple real business tools. It is not yet the target migrated runtime: no independent LangGraph runtime, no Agent Run/Step persistence, no real Skill loader, no long-term Memory, no AG-UI/A2UI style component registry, and no unified internal Tool Gateway.

## Verification Matrix

| Area | Documents claim | Code exists | Actually ran | Half-finished | Mismatch |
|---|---|---|---|---|---|
| Agent SSE | `/api/agent/chat/stream` streams events | `AgentController.chatStream` and `AgentOrchestratorService` | Yes, local request streamed `started/thinking/tool_call/tool_result/final` | Event names are legacy and debug events still emitted | `llm_decision` still sent on wire |
| Sessions/messages | Session CRUD and message persistence | `AgentSessionService`, `AgentMessageService`, mappers | Yes, `/api/agent/sessions` returned persisted records | Local debug auth open allowed no-token access | Production permission not validated in this run |
| Tool registry | Spring Bean tool registry | `AgentToolRegistry` | Yes, `/api/agent/tools` returned 30 tools | Registry does not filter by user role | Disabled/admin metadata visible |
| Tool calls | station/weather/prediction/report/model/api tools | `tool/impl/*.java` | Yes, station/weather/prediction list called | `prediction.detail` not called when list empty | First vertical chain lacks prediction detail data |
| Approvals | Write/cost/admin tools require confirmation | `AgentApprovalService`, `requiresApproval()` | Not re-tested in this run; code and prior script exist | Approval resume is another chat request, not Run resume | Not a runtime pause/resume model |
| DeepSeek | JSON mode and fallback after tools | `DeepSeekAgentLlmClient`, parser | Yes, local final answer came from LLM after tools | Non-streaming LLM; token streaming is not real | Model hallucinated generic percent ranges not returned by tools |
| Slash command | `/station`, `/weather`, `/predict`, `/report`, `/model`, `/api` | `SlashCommandParser` | Indirectly via code and existing scripts | `preferredTool` still takes priority over slash | Target says slash should be first |
| Frontend /reports | Agent workbench exists | `AnalysisReport.vue`, `api/agent.ts` | Build passed | Large single-file UI; no AG-UI shared state | Not Generative UI protocol |
| Memory | Long-term memory target documented | No long-term memory tables/services found | Not runnable | Only recent 8 messages used | Documents are target, not implemented |
| Skills | Skill target documented | No runtime Skill loader in current app | Not runnable | None | Must be migrated |
| MCP/router | MCP-ready target documented | No MCP server/router in current app | Not runnable | Java Bean registry only | Must be migrated |
| Evaluation | E2E script exists | `scripts/agent-e2e-test.mjs` | Not run with real token | Covers only 5 legacy scenarios | Does not cover required migration E2E |

## Local Runtime Result

`mvn -q -DskipTests compile` passed.

`npm run build` passed with existing Rolldown pure annotation warnings and chunk-size warnings.

`./start-local.sh --backend-only --no-install` started backend on `127.0.0.1:8080`.

Smoke input:

`分析 1 号电站当前运行情况，结合天气和最近预测结果。`

Observed tools:

- `station.detail`: success
- `weather.current`: success
- `prediction.list`: success, zero records

Current blockers against target acceptance:

- Local seed station description contains `mock`, so it does not satisfy "no mock" as a final acceptance data source.
- Security debug mode was open locally, so permission failure was not validated.
- No Generative UI component schema was emitted.
- No Skill was loaded or used.
- No Memory was read or written.

