---
name: power-prediction-analysis
description: "Analyze photovoltaic prediction tasks and predicted power trends. Use when the user asks about prediction result, task trend, forecast quality, or recent model output."
license: Apache-2.0
metadata:
  source: "awesome-llm-apps agent_skills format adapted for GuangFu"
---

# Power Prediction Analysis

## Trigger Conditions

- User asks about prediction, forecast, trend, model result, or task ID.

## Required Context

- `taskId` for detail analysis, or `stationId` for recent prediction lookup.

## Allowed Tools

- `prediction.list`
- `prediction.detail`
- `station.detail`

## Execution Steps

1. Determine whether user supplied task ID.
2. If only station ID is known, query `prediction.list`.
3. Select the latest usable task when available.
4. Query `prediction.detail`.
5. Evaluate trend, range, missing data, and anomalies.
6. Synthesize prediction trend UI and explanation.

## Approval Rules

No approval for read-only prediction analysis.

## Output Schema

```json
{
  "taskId": 0,
  "trend": "string",
  "risk": "string",
  "highlights": [],
  "ui": []
}
```

## Failure Handling

- If no task exists, state no prediction data and suggest creating a task.
- If task is not accessible, report permission failure.

## Examples

- "解释任务 8 的预测结果。"
- "分析 1 号电站最近预测趋势。"

