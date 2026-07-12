# Migration Map

| Reference file | Migrated file | Migration type | Change ratio | Notes |
|---|---|---|---:|---|
| `generative-ui-starter-project/agent/main.py` | `agent-runtime/pv_agent_runtime/runtime.py` | structure migrated | 65% | Replaced demo tools with photovoltaic stage plan and Spring gateway tools. |
| `generative-ui-starter-project/agent/src/todos.py` | `agent-runtime/pv_agent_runtime/types.py` | structure migrated | 70% | Replaced todo state with `AgentState`, `PlanStep`, `UIInstruction`. |
| `generative-ui-starter-project/agent/src/a2ui_fixed_schema.py` | `agent-runtime/pv_agent_runtime/events.py` | structure migrated | 75% | Replaced A2UI ops with strict photovoltaic UI instruction schema. |
| `generative-ui-starter-project/src/app/declarative-generative-ui/definitions.ts` | `docs/agent-migration/GENERATIVE_UI_MIGRATION.md` | design mapped | 80% | Vue/agent-web component registry planned, not copied yet. |
| `generative-ui-starter-project/src/hooks/use-generative-ui-examples.tsx` | `docs/agent-migration/GENERATIVE_UI_MIGRATION.md` | design mapped | 80% | Maps to Vue whitelist renderer and approval cards. |
| `ai_consultant_agent/ai_consultant_agent.py` | `agent-runtime/pv_agent_runtime/runtime.py` | flow adapted | 60% | Input/Research/Analysis/Strategy/Synthesis mapped to photovoltaic stages. |
| `llm_app_personalized_memory/llm_app_memory.py` | `agent-runtime/pv_agent_runtime/memory.py` | flow adapted | 70% | Memory extraction, safe filtering, Spring Memory Gateway read/write adapter. |
| `llm_app_personalized_memory/llm_app_memory.py` | `backend/src/main/java/com/example/pvplatform/module/agent/service/AgentMemoryService.java` | structure adapted | 75% | Long-term memory persistence moved to platform MySQL with user-bound SecurityContext. |
| `multi_mcp_agent_router/agent_forge.py` | `agent-runtime/pv_agent_runtime/tool_router.py` | structure migrated | 55% | Agent metadata/router/error isolation adapted to Spring tool gateway. |
| `agent_skills/project-graveyard/SKILL.md` | `agent-runtime/skills/*/SKILL.md` | format migrated | 65% | Frontmatter and operational sections preserved; content replaced. |
| `agent_skills/evals/tools/skill_lint.py` | `docs/agent-migration/MIGRATION_TEST_PLAN.md` | quality gate planned | 90% | Script not copied yet; lint criteria recorded for future CI. |
