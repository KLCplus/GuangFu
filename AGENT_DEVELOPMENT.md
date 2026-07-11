# Agent Development Guide

This is the single source of truth for the photovoltaic platform Agent implementation. It replaces the previous split Agent architecture, tool list, and test report documents.

## Architecture

## Current State

`/reports` is the Codex-style Agent workspace for the PV forecasting platform. It uses `POST /api/agent/chat/stream` for SSE, calls DeepSeek through the backend, executes only tools registered in `AgentToolRegistry`, and persists sessions, messages, tool calls, and approvals.

Phase 2 keeps the existing stream endpoint and analysis report APIs intact. The left rail now uses real Agent sessions instead of mixing `analysis_report` history into the chat history.

## Backend Flow

Main package: `com.example.pvplatform.module.agent`.

- `AgentController`: session CRUD/actions, message history, tool-call history, tools list, SSE chat.
- `AgentOrchestratorService`: JSON ReAct loop, max 5 tool rounds, approval pause/continue, SSE events.
- `AgentToolRegistry`: single source of registered tools; enabled tools are sent to the LLM, disabled tools stay visible through `/api/agent/tools` for diagnostics only.
- `AgentToolService`: writes `PENDING`, updates `SUCCESS` or `FAILED`, stores duration and sanitized result JSON.
- `AgentApprovalService`: creates and decides user approvals, scoped to the current user.
- `DeepSeekAgentLlmClient`, `LlmPromptBuilder`, `LlmToolCallParser`: model call, dynamic tool prompt, JSON cleanup/parsing.

## SSE Events

The stream emits JSON envelopes with these event names:

| Event | Meaning |
| --- | --- |
| `started` | Session and user message are persisted. |
| `thinking` | The Agent is selecting the next action. |
| `plan` | DeepSeek selected one or more tool names. |
| `tool_call` | A registered tool is about to execute. |
| `tool_result` | Tool completed or failed with visible error. |
| `approval_required` | Write/sensitive tool is paused until user decision. |
| `final` | Assistant message is persisted and complete. |
| `error` | Recoverable stream error payload. |

## Tool Loop

DeepSeek must return one JSON object:

```json
{
  "type": "tool_call",
  "reason": "...",
  "toolCalls": [
    { "toolName": "station.detail", "arguments": { "stationId": 2 } }
  ],
  "answer": "...",
  "question": "..."
}
```

Supported types are `tool_call`, `final`, and `ask_user`. The backend strips markdown code fences, parses JSON, rejects tools outside the registry or disabled tools, and appends real tool results back into the model context before asking for the final answer. Tool results are persisted after sanitization; API keys, tokens, passwords, and secrets are masked or omitted.


## Tool Coverage

Enabled tools cover station, weather, prediction, report, model, API Key, API usage, news, wallet, marketplace plan listing, cloud prediction, model run, and selected admin operations. Write, dangerous, cost, and admin tools require approval before execution.

Disabled tools are intentionally registered but unavailable to the LLM when the project has no precise real backend operation, for example `marketplace.purchase`, `admin.station.manage`, and `admin.model.manage`.

## Approval Mechanism

Tools declare `ToolPermissionLevel` and `requiresApproval()`. `report.generate` is `WRITE`, so the orchestrator creates `agent_tool_call` with `AWAITING_APPROVAL`, creates `agent_approval`, sends `approval_required`, saves an assistant message, and stops.

The frontend calls `POST /api/agent/approvals/{approvalId}/approve`. A continuation stream with `approvalId` then either executes the pending tool or returns a cancellation message. Only the user who owns the approval can decide it.

## Persistence

Tables are in `backend/src/main/resources/sql/init.sql`; incremental updates are in `backend/src/main/resources/sql/agent_tables_migration.sql`.

- `agent_session`: user-owned chat sessions, archive/pin/status/deleted flags.
- `agent_message`: user and assistant messages with metadata JSON.
- `agent_tool_call`: tool arguments/result/status/error/duration, user id, display name.
- `agent_approval`: approval status, tool name, arguments, comments, decision time.

Existing databases should run:

```bash
mysql < backend/src/main/resources/sql/agent_tables_migration.sql
```

## Frontend Flow

`web-frontend/src/views/AnalysisReport.vue` now uses:

- `GET /api/agent/sessions` for the left rail.
- `POST /api/agent/sessions` for new sessions.
- `GET /api/agent/sessions/{sessionId}/messages` to restore messages, tool cards, and pending approvals.
- `GET /api/agent/sessions/{sessionId}/tool-calls` for Inspector debug records.
- `GET /api/agent/tools` for the tools tab.
- `fetch` streaming for `/api/agent/chat/stream`, with bearer token attached.

Report detail rendering remains in the right result panel and still uses the existing analysis APIs.

## Tool Registry

Tools are registered through `AgentToolRegistry` and exposed by `GET /api/agent/tools`. Disabled or unclear tools are registered as disabled and are not sent to the LLM tool prompt. No tool returns mock success data.

| 工具名 | 分类 | 权限 | 是否确认 | 输入参数 | 对应后端接口/Service | 状态 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `station.list` | STATION | READ_ONLY | 否 | `{}` | `StationService.list` | 已启用 | 查询当前用户可访问电站。 |
| `station.detail` | STATION | READ_ONLY | 否 | `{ "stationId": number }` | `StationPermissionService.requireView`, `StationService.detail` | 已启用 | 缺 stationId 返回失败，Agent 应追问或先列电站。 |
| `station.create` | STATION | WRITE | 是 | `stationName/province/city/address/longitude/latitude/capacity/status/description` | `StationService.create` | 已启用 | 管理员工具，执行前确认。 |
| `station.update` | STATION | WRITE | 是 | `{ "stationId": number, ...fields }` | `StationService.detail`, `StationService.update` | 已启用 | 管理员工具；未提供字段保留原值。 |
| `station.disable` | STATION | WRITE | 是 | `{ "stationId": number }` | `StationService.detail`, `StationService.update` | 已启用 | 管理员工具；将状态置为 `OFFLINE`。 |
| `station.delete` | STATION | DANGEROUS | 是 | `{ "stationId": number }` | `StationService.delete` | 已启用 | 管理员工具，危险操作。 |
| `weather.current` | WEATHER | READ_ONLY | 否 | `{ "stationId": number }` | `WeatherService.current` | 已启用 | 使用项目天气服务和缓存。 |
| `prediction.list` | PREDICTION | READ_ONLY | 否 | `{ "stationId": number }` | `PredictionService.history` | 已启用 | 按电站查预测任务。 |
| `prediction.detail` | PREDICTION | READ_ONLY | 否 | `{ "taskId": number }` | `PredictionService.detail`, `PredictionService.results` | 已启用 | 返回任务和结果。 |
| `report.generate` | REPORT | WRITE | 是 | `{ "stationId": number, "taskId"?: number, "title"?: string, "includeWeather": boolean, "includePrediction": boolean }` | `AnalysisService.report` | 已启用 | 未确认前不会写库；工具结果只返回安全摘要。 |
| `report.list` | REPORT | READ_ONLY | 否 | `{ "stationId"?: number }` | `AnalysisService.history` | 已启用 | 查询历史报告。 |
| `report.detail` | REPORT | READ_ONLY | 否 | `{ "reportId": number }` | `AnalysisService.detail` | 已启用 | 查询报告详情。 |
| `model.list` | MODEL | READ_ONLY | 否 | `{ "category"?: string }` | `ModelService.list` | 已启用 | 查询模型列表。 |
| `model.detail` | MODEL | READ_ONLY | 否 | `{ "modelId": number }` | `ModelService.detail` | 已启用 | 查询模型详情。 |
| `model.run` | MODEL | COST | 是 | `{ "stationId": number, "modelId": number, "numericValues": array[30], "inputImages": array[30] }` | `PredictionService.create` | 已启用 | 消耗型工具；必须确认；输入帧数严格校验。 |
| `api.list` | API | READ_ONLY | 否 | `{}` | `ApiKeyService.listOwn` | 已启用 | 只返回 key id/name/prefix/status/时间，不返回完整 Key。 |
| `api.usage` | API | READ_ONLY | 否 | `{}` | `ApiCallLogService.ownLogs` | 已启用 | 查询当前用户 API 调用记录/统计入口。 |
| `api.create` | API | WRITE | 是 | `{ "keyName": string, "expireDays"?: number }` | `ApiKeyService.create` | 已启用 | 需要确认；Agent 结果不展示完整 Key。 |
| `api.reset` | API | DANGEROUS | 是 | `{ "apiKeyId": number }` | `ApiKeyService.resetOwn` | 已启用 | 需要确认；Agent 结果不展示重置后的完整 Key。 |
| `api.delete` | API | DANGEROUS | 是 | `{ "apiKeyId": number }` | `ApiKeyService.deleteOwn` | 已启用 | 需要确认。 |
| `news.list` | NEWS | READ_ONLY | 否 | `{ "page"?: number, "size"?: number, "type"?: string }` | `NewsService.list` | 已启用 | 查询新闻/通知。 |
| `cloud.predict` | CLOUD | COST | 是 | `{ "modelName": string, "inputImages": array[10] }` | `CloudForecastService.predict` | 已启用 | 消耗型工具；必须确认；只接真实云预测接口。 |
| `marketplace.list` | MARKETPLACE | READ_ONLY | 否 | `{}` | `OpenAccountService.plans` | 已启用 | 查询可见套餐/权益。 |
| `wallet.balance` | WALLET | READ_ONLY | 否 | `{}` | `OpenAccountService.wallet` | 已启用 | 查询当前用户钱包/余额。 |
| `admin.userApi.list` | ADMIN | ADMIN | 是 | `{}` | `ApiKeyService.adminList` | 已启用 | 管理员工具；返回脱敏 API Key 信息。 |

## Disabled

| 工具名 | 原因 |
| --- | --- |
| `marketplace.purchase` | 当前 `OpenAccountService` 只有试用和套餐展示，没有真实购买/扣费接口；不能伪造购买成功。 |
| `admin.station.manage` | 已用 `station.create/update/disable/delete` 精确工具覆盖真实管理操作，综合工具禁用避免参数不清。 |
| `admin.model.manage` | 模型创建、更新、上下线需要更细粒度参数和上线健康检查，暂不暴露为 Agent 写工具。 |

## Safety

Tool arguments and results are persisted through `AgentToolService`, which masks keys matching API key, token, authorization, password, or secret patterns before storing/sending debug data. API Key create/reset tools intentionally omit the full one-time key from Agent results. Tools must call existing services; no mock success result is allowed.

## Validation And Test Report

## Code Audit Summary

已完成：

- `/api/agent/chat/stream` SSE 基础闭环、DeepSeek JSON ReAct、工具注册和真实 Service 调用已经存在。
- 第一批核心工具中，station/weather/prediction/report 已接入真实后端服务。
- approval 基础流程已经存在，`report.generate` 需要确认。
- 扩展工具已覆盖 model、api、news、wallet、marketplace、cloud、admin/station 写操作等真实 Service；无法匹配真实购买/综合管理语义的工具保持 disabled。

本轮补齐：

- 会话 CRUD/actions：创建、列表、消息、归档/取消、固定/取消、重命名、逻辑删除。
- 消息历史返回工具调用和 approval，前端刷新后可恢复工具卡片和确认卡片。
- `agent_tool_call` 增加 `user_id`、`display_name`，`agent_approval` 增加 `tool_name`，`agent_session` 增加 `deleted`。
- 工具调用结果入库前脱敏，调试面板不展示 token/API Key。
- `/reports` 左侧切换为真实 Agent session 列表，不再混用报告历史。
- Inspector 调试 tab 显示当前 sessionId 和最近工具调用。
- 追加接入 `model.detail`、`api.list/create/reset/delete`、`news.list`、`wallet.balance`、`marketplace.list`、`cloud.predict`、`model.run`、`station.create/update/disable/delete`、`admin.userApi.list`。
- `marketplace.purchase`、`admin.station.manage`、`admin.model.manage` 注册为 disabled，并在工具清单中说明原因。

需要保留：

- 现有 `/api/agent/chat/stream`、`/api/analysis/report`、`/api/analysis/reports`、`/api/analysis/reports/{id}`。
- 现有 AnalysisReport 结果面板和报告详情适配逻辑。

## Automated Checks

| Check | Command | Result | Notes |
| --- | --- | --- | --- |
| Backend compile | `mvn -q -DskipTests compile` in `backend` | PASS | 2026-07-11 run. |
| Backend tests | `mvn test` in `backend` | PASS | 66 run, 0 failures, 0 errors, 2 skipped. |
| Frontend build | `npm run build` in `web-frontend` | PASS | Rolldown dependency annotation/chunk warnings only. |
| Backend runtime startup | `./start-local.sh --backend-only` | PASS | The root startup script loads `backend/.env.local` before Maven starts. |
| Agent tools HTTP smoke | `curl -s http://127.0.0.1:8080/api/agent/tools` | PASS | Returned 28 tools: 25 enabled, 3 disabled with reasons. |
| Agent sessions HTTP smoke | `GET/POST /api/agent/sessions` | PASS | Required running fixed migration first. |
| Agent SSE station query | `查看 2 号电站信息` | PASS | SSE emitted `started/thinking/plan/tool_call/tool_result/final`; station 2 does not exist, so tool failed visibly and final explained the real error. |
| Agent missing parameter | `帮我分析这个电站` | PASS | Returned final prompt asking for stationId, no default stationId used. |
| Agent weather analysis | `分析 1 号电站当前天气是否会影响发电` | PASS | Called `station.detail` and `weather.current`, final cited real weather data. |
| Agent approval reject | Reject `approvalId=2` then continue | PASS | Returned cancellation final and did not execute `report.generate`. |
| Agent approval approve | Approve `approvalId=3` then continue | PASS | Executed `report.generate`, created `reportId=2`, streamed `tool_result success` and `final`. |
| Agent SSE model/API/news | `查询当前可用模型列表，并说明第一个模型的详情` | PASS | Called `model.list`, `model.detail`, `api.list`, `news.list`; all returned real persisted/service data. |
| Agent SSE wallet/marketplace | `查询我的钱包余额和市场套餐` | PASS | Called `wallet.balance` and `marketplace.list`; returned real wallet and plan data. |
| Agent api.create approval | `帮我创建一个名为 agent-smoke 的 API Key，有效期 7 天` | PASS | Emitted `approval_required` for `api.create`; no API Key was created before approval. |
| Agent history restore | `GET /api/agent/sessions/6/messages` and `/tool-calls` | PASS | Restored tool calls and pending approval `approvalId=4`, including `AWAITING_APPROVAL` tool-call status. |

## Scenario Coverage

| 场景 | 输入 | 预期工具/SSE | 本轮状态 |
| --- | --- | --- | --- |
| 查询电站 | `查看 2 号电站信息` | `started` -> `thinking` -> `tool_call station.detail` -> `tool_result` -> `final` | 代码路径已实现，未启动完整服务做端到端复测。 |
| 缺少参数 | `帮我分析这个电站` | `ask_user` 或 `station.list` | Prompt 和工具参数校验已覆盖。 |
| 天气分析 | `分析 2 号电站当前天气是否会影响发电` | `station.detail`, `weather.current`, `final` | 工具已注册真实 Service。 |
| 预测分析 | `解释任务 8 的预测结果` | `prediction.detail`, `final` | 工具已注册真实 Service。 |
| 生成报告确认 | `生成 2 号电站综合分析报告，包含天气和预测` | `approval_required`，确认后 `report.generate` | 后端确认和续跑逻辑已保留并接入历史恢复。 |
| 拒绝确认 | 用户点取消 | 不执行写操作，回复已取消 | 后端 `REJECTED` 分支已实现。 |
| 接口错误 | 不存在 taskId | `tool_result failed`，前端错误卡片 | `ToolExecutionResult.failure` 和前端错误卡片已覆盖。 |
| 刷新页面 | 重新进入 `/reports` | 会话、消息、工具调用、approval 恢复 | HTTP 历史接口已验证；会话 6 能恢复 SUCCESS 工具调用和 PENDING approval。 |
| 扩展工具只读查询 | `查询我的钱包余额和市场套餐` | `wallet.balance`, `marketplace.list`, `final` | 已端到端通过。 |
| API 创建确认 | `创建 agent-smoke API Key` | `approval_required api.create`，未确认不执行 | 已端到端通过，复查 `api.list` 仍为空。 |

## SQL

初始化文件：`backend/src/main/resources/sql/init.sql`。

现有库迁移：`backend/src/main/resources/sql/agent_tables_migration.sql`。

```bash
mysql < backend/src/main/resources/sql/agent_tables_migration.sql
```

## Runtime Notes

Runtime extended-tool smoke tests used test session `sessionId=6`. Do not reuse the same session for concurrent SSE smoke requests; parallel requests can let the LLM see overlapping conversation context and combine tool plans.

During runtime tests Redis logged cache deserialization WARNs for stale `model:*` and `news:*` cache entries. The configured cache error handler fell back to DB/service calls and Agent tool calls still succeeded. Clearing Redis cache removes the WARN noise.

Direct `mvn spring-boot:run` does not import `backend/.env.local`, so it falls back to framework defaults. Use `./start-local.sh --backend-only` from the repository root or export `.env.local` first.

The first migration attempt failed because local MySQL does not support `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`; `backend/src/main/resources/sql/agent_tables_migration.sql` was changed to an `information_schema`-based compatible migration and then applied successfully.

For live Agent SSE tests, `.env.local` had `ANALYSIS_LLM_ENABLED=false`; tests temporarily started the backend with `ANALYSIS_LLM_ENABLED=true` and `PVOUTPUT_ENABLED=false` without printing the DeepSeek key.

`report.generate` initially returned the full report object in `dataPreview`, including raw/prompt/context snapshots. The tool result has been changed to return only a safe report summary (`reportId`, `stationId`, `title`, `summary`, `riskLevel`, `status`, include flags, model name, timestamps).

## Remaining Work

- 前端浏览器交互（点击确认卡片、刷新页面恢复卡片）仍建议用真实浏览器手动复测；后端 HTTP/SSE 已验证。
- `marketplace.purchase`、`admin.station.manage`、`admin.model.manage` 保持 disabled，直到有明确真实购买/综合管理接口。
- Redis 旧缓存格式会产生 WARN，可通过清理对应缓存或统一缓存序列化策略消除。

## Local Startup

Use the single retained startup script from the repository root:

```bash
./start-local.sh
```

Stop the retained local services with:

```bash
./stop-local.sh
```

Useful options:

```bash
./start-local.sh --backend-only
./start-local.sh --frontend-only
./start-local.sh --init-db
```

Stop local services with the single retained stop script:

```bash
./stop-local.sh
```

Local defaults are written to `backend/.env.local`. The retained startup script defaults `ANALYSIS_LLM_ENABLED=true` so the Agent can answer, and `PVOUTPUT_ENABLED=false` to avoid exhausting PVOutput's hourly request quota during local development.

Do not commit API keys, tokens, passwords, or generated local runtime files.
