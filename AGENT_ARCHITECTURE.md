# Agent Architecture

## Overview

The `/reports` workspace now talks to a real Spring Boot Agent module. The browser sends a natural language task to `POST /api/agent/chat/stream`, the backend orchestrator calls DeepSeek with a JSON ReAct prompt, executes only registered platform tools, streams progress over SSE, and persists sessions, messages, tool calls, and approvals.

The LLM runs in the Spring Boot backend. The model-service remains responsible only for platform prediction/cloud/time-series inference.

## Backend Modules

Main package: `com.example.pvplatform.module.agent`

- `controller`: Agent session, tools, SSE chat, approval endpoints.
- `service`: orchestration, sessions, messages, tool call persistence, approval persistence, SSE events, JSON helpers.
- `llm`: DeepSeek client, prompt builder, JSON tool-call parser.
- `tool`: Agent tool abstraction, registry, permission levels, execution result model.
- `tool.impl`: adapters that call existing business services.
- `entity` and `mapper`: MyBatis persistence for agent tables.

## SSE Flow

`POST /api/agent/chat/stream` returns `text/event-stream` and emits JSON envelopes:

- `started`: session and user message have been created.
- `thinking`: the Agent is preparing a DeepSeek decision.
- `plan`: DeepSeek selected candidate tool calls.
- `tool_call`: a registered tool is about to execute.
- `tool_result`: the real service call finished successfully or failed with a structured error.
- `approval_required`: the next tool is a write/sensitive operation and execution is paused.
- `final`: assistant response is persisted and complete.
- `error`: structured recoverable failure.

The frontend parses the stream with `fetch` and renders messages, tool cards, approval cards, and debug records.

## Tool Calling

DeepSeek is prompted to return one JSON object:

```json
{
  "type": "tool_call",
  "reason": "...",
  "toolCalls": [
    { "toolName": "station.detail", "arguments": { "stationId": 2 } }
  ]
}
```

Other supported `type` values are `final` and `ask_user`. The orchestrator runs at most 5 tool-call loops. Tool results are appended back into the LLM context before the final answer, so the final response is based on real backend data.

## Permission And Approval

Each tool declares a `ToolPermissionLevel`:

- `READ_ONLY`: can execute after normal user permission checks.
- `WRITE`, `COST`, `ADMIN`, `DANGEROUS`: require explicit user confirmation before execution.

`report.generate` is `WRITE`, so the first Agent round creates `agent_approval`, streams `approval_required`, and stops. After the frontend calls `POST /api/agent/approvals/{approvalId}/approve`, the next stream request with `approvalId` executes or cancels the pending tool.

The Agent never executes arbitrary URLs or SQL. It can only invoke registered `AgentTool` implementations, and those implementations use existing services such as `StationService`, `WeatherService`, `PredictionService`, `AnalysisService`, `ModelService`, and `ApiCallLogService`.

## Persistence

Added tables:

- `agent_session`
- `agent_message`
- `agent_tool_call`
- `agent_approval`

They are included in `backend/src/main/resources/sql/init.sql` and as an incremental migration in `backend/src/main/resources/sql/agent_tables_migration.sql`.

## Frontend

`web-frontend/src/views/AnalysisReport.vue` keeps the existing report history and result inspector, but the composer now routes natural language and slash commands through the Agent SSE endpoint. It displays:

- real-time execution steps,
- tool calls and results,
- approval cards for write operations,
- tool registry in the Inspector,
- raw SSE/debug payloads without tokens or API keys.
