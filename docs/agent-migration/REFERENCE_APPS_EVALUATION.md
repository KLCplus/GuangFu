# Reference Apps Evaluation

Fixed reference commit: `426cfa66b5fd832090038c36e50a6aa1dab3119c`.

## Generative UI Starter

Path: `generative_ui_agents/generative-ui-starter-project`

What ran:

- `npm install` initially failed because `uv` was missing.
- Installed `uv` with `python3 -m pip install --user uv`.
- `npm install` then completed and installed the Python agent dependencies.
- `npm run dev` started Next.js UI at `http://localhost:3000`.

Partial blocker:

- LangGraph agent server started via npm script but stalled while `@langchain/langgraph-cli` downloaded its own runtime; no model call was made.
- `.env` was absent, so no OpenAI key was available.

Migratable code:

- `agent/main.py`: agent creation, tools list, CopilotKit middleware, state streaming.
- `agent/src/todos.py`: typed AgentState and state update tool pattern.
- `agent/src/a2ui_fixed_schema.py`: fixed schema A2UI render operations.
- `agent/src/a2ui_dynamic_schema.py`: dynamic A2UI operation generation pattern.
- `src/app/declarative-generative-ui/definitions.ts`: component schema catalog.
- `src/app/declarative-generative-ui/renderers.tsx`: renderer whitelist.
- `src/hooks/use-generative-ui-examples.tsx`: frontend component/tool/HITL registration.

Risk:

- `npm audit` reported 28 vulnerabilities, including 2 critical. We should not copy its lockfile blindly into production.

## AI Consultant Agent

Path: `advanced_ai_agents/single_agent_apps/ai_consultant_agent`

What ran:

- Installed requirements.
- Imported module and initialized `root_agent`.
- Verified `root_agent.name == ai_consultant_agent`.
- Verified 3 wrapped tools.

Not run:

- Real Gemini/Perplexity interaction, because no Gemini/Perplexity keys were provided.

Migratable code:

- Safety wrapper around tools.
- Structured tool result pattern.
- Stage-oriented consultation prompt, adapted into photovoltaic phases.

## Personalized Memory

Path: `advanced_llm_apps/llm_apps_with_memory_tutorials/llm_app_personalized_memory`

What ran:

- Installed requirements.
- Imported `mem0.Memory`.

Not run:

- Streamlit interaction with OpenAI and Qdrant, because no OpenAI key and no Qdrant service were provided.

Migratable pattern:

- Retrieve memories before LLM call.
- Inject relevant memory as context.
- Add memory after interaction.

Required rewrite:

- Storage must be MySQL, not Qdrant.
- Memory writes must be typed, confidence-scored, user-bound, enabled, and sensitive-data-filtered.

## Multi MCP Agent Router

Path: `mcp_ai_agents/multi_mcp_agent_router`

What ran:

- Installed requirements.
- Imported `agent_forge`.
- Verified 4 agent definitions.
- Verified classifier routes security query to `security_auditor`.

Not run:

- Real MCP server spawning and Anthropic calls, because no Anthropic key was provided and npm MCP servers were not launched.

Migratable code:

- Agent capability metadata.
- Keyword classifier shape.
- MCP tool schema conversion.
- Tool session map and per-tool error isolation.

## Agent Skills

Path: `agent_skills`

What ran:

- Read `project-graveyard/SKILL.md`.
- Read `advisor-orchestrator-worker/SKILL.md`.
- Read skill lint/scanner scripts.

Migratable code:

- YAML frontmatter structure.
- `When to use` / `When not to use` sections.
- Execution steps and failure rules.
- Lint/security scanner patterns as a future quality gate.

