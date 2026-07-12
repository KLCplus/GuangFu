---
name: api-usage-analysis
description: "Analyze Open API usage for the current user. Use when the user asks about API calls, quota, key usage, latency, failures, or open platform consumption."
license: Apache-2.0
metadata:
  source: "awesome-llm-apps agent_skills format adapted for GuangFu"
---

# API Usage Analysis

## Trigger Conditions

- User asks about API usage, quota, API key calls, errors, billing, or open platform consumption.

## Required Context

- `userId`
- Optional API key ID or date range.

## Allowed Tools

- `api.usage`
- `api.list`
- `wallet.balance`

## Execution Steps

1. Identify requested scope.
2. Query API usage.
3. Query API key list only when key context is needed.
4. Query wallet balance only when consumption/cost is mentioned.
5. Summarize usage, errors, and next actions.

## Approval Rules

Read-only usage analysis does not need approval. Creating/resetting/deleting keys requires separate approval and is outside this skill.

## Output Schema

```json
{
  "usageSummary": {},
  "risks": [],
  "recommendations": []
}
```

## Failure Handling

- Do not expose full API keys.
- If usage summary is unavailable, return available log summary and state limitation.

## Examples

- "看一下我最近 API 使用情况。"
- "这个月开放平台调用有没有异常？"

