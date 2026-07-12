---
name: station-inspection
description: "Inspect photovoltaic station operating status. Use when the user asks to analyze a station, current operation, station health, or a combined station/weather/prediction situation."
license: Apache-2.0
metadata:
  source: "awesome-llm-apps agent_skills format adapted for GuangFu"
---

# Station Inspection

## Trigger Conditions

- User asks to analyze station operation.
- User mentions a station ID plus weather, prediction, power, health, risk, or maintenance.
- User asks for current station status.

## Required Context

- `userId`
- `sessionId`
- `stationId` from user task, context memory, or explicit clarification.

## Allowed Tools

- `station.detail`
- `weather.current`
- `prediction.list`
- `prediction.detail`

## Execution Steps

1. Understand the requested station and analysis scope.
2. Collect station detail with `station.detail`.
3. Collect weather with `weather.current`.
4. Collect recent prediction tasks with `prediction.list`.
5. If a recent usable task exists, collect it with `prediction.detail`.
6. Evaluate data completeness.
7. Recommend operational actions.
8. Synthesize final answer and UI instructions.

## Approval Rules

No approval is needed for read-only inspection. Do not call write, cost, or admin tools.

## Output Schema

```json
{
  "summary": "string",
  "dataCompleteness": "complete|partial|failed",
  "risks": [],
  "recommendations": [],
  "ui": []
}
```

## Failure Handling

- If station permission fails, stop and report no access.
- If weather fails, continue with station and prediction data and mark weather missing.
- If prediction list is empty, state that no recent prediction was available.

## Examples

- "分析 1 号电站当前运行情况，结合天气和最近预测结果。"
- "看看 2 号电站现在有没有风险。"

