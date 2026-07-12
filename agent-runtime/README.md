# Photovoltaic Agent Runtime

Migration foundation adapted from `awesome-llm-apps` at commit
`426cfa66b5fd832090038c36e50a6aa1dab3119c`.

This module is intentionally isolated from MySQL and DeepSeek secrets. It must
call Spring Boot Agent Gateway for LLM and business tools.

Current status:

- Skill loader works against `skills/*/SKILL.md`.
- First vertical chain can be planned and dry-run.
- Tool gateway client contract is defined.
- Internal OpenAI-compatible LLM gateway client is defined.
- UI instructions are schema-checked against a photovoltaic whitelist.

Run tests:

```bash
python3 -m unittest discover -s agent-runtime/tests
```

Run local runtime server:

```bash
PYTHONPATH=agent-runtime AGENT_INTERNAL_TOKEN=local-agent-runtime-token \
  python3 agent-runtime/server.py --spring-base-url http://127.0.0.1:8080
```

The server exposes:

- `GET /health`
- `POST /run/stream`
