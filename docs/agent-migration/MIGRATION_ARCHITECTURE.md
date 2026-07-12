# Migration Architecture

## Target Boundary

```text
Vue platform
  -> same-domain agent-web route
  -> Spring Boot Agent Gateway
  -> agent-runtime
  -> Spring Boot internal tool API
  -> business services / MySQL / model-service
```

## Migration Decision

The reference frontend is Next.js/CopilotKit. The existing platform is Vue 3. Full Next.js sub-app integration is still possible, but the first safe step is to migrate the protocol and runtime state model while keeping the existing `/reports` route available behind a feature flag.

## Runtime Mode

Add `AGENT_RUNTIME_MODE`:

- `legacy`: current `AgentOrchestratorService`.
- `migrated`: Python `agent-runtime` via Spring Agent Gateway.

The current change adds the migrated runtime foundation but does not flip the default.

## Internal APIs To Add

- `POST /api/internal/llm/v1/chat/completions`
- `POST /api/internal/agent/tools/{toolName}/execute`
- `GET /api/internal/agent/sessions/{sessionId}/context`

All internal endpoints must require an internal token/signature and must bind execution to the authenticated platform user resolved by Spring.

