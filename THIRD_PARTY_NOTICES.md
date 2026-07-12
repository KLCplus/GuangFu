# Third Party Notices

## awesome-llm-apps

- Repository: `https://github.com/Shubhamsaboo/awesome-llm-apps`
- Commit: `426cfa66b5fd832090038c36e50a6aa1dab3119c`
- License: Apache-2.0

| Original path | Migrated path | Modification |
|---|---|---|
| `generative_ui_agents/generative-ui-starter-project/agent/main.py` | `agent-runtime/pv_agent_runtime/runtime.py` | Structure adapted from demo agent to photovoltaic stages and Spring tool gateway. |
| `generative_ui_agents/generative-ui-starter-project/agent/src/todos.py` | `agent-runtime/pv_agent_runtime/types.py` | Typed state/update pattern adapted from todos to PV agent state. |
| `generative_ui_agents/generative-ui-starter-project/agent/src/a2ui_fixed_schema.py` | `agent-runtime/pv_agent_runtime/events.py` | Fixed UI operation pattern adapted to whitelisted PV UI instructions. |
| `advanced_ai_agents/single_agent_apps/ai_consultant_agent/ai_consultant_agent.py` | `agent-runtime/pv_agent_runtime/runtime.py` | Stage workflow and safe tool result pattern adapted; Gemini/Perplexity business content removed. |
| `advanced_llm_apps/llm_apps_with_memory_tutorials/llm_app_personalized_memory/llm_app_memory.py` | `agent-runtime/pv_agent_runtime/memory.py` | Retrieve/inject/write memory flow adapted; mem0/Qdrant/OpenAI removed. |
| `mcp_ai_agents/multi_mcp_agent_router/agent_forge.py` | `agent-runtime/pv_agent_runtime/tool_router.py` | Capability metadata, classification, dispatch, and error isolation adapted to Spring tools. |
| `agent_skills/project-graveyard/SKILL.md` | `agent-runtime/skills/*/SKILL.md` | Skill structure and frontmatter conventions adapted; domain content replaced. |

Apache-2.0 license text is available in the reference repository and allows modification and redistribution subject to its terms.

