---
name: anomaly-diagnosis
description: "Diagnose photovoltaic abnormal operation. Use when the user mentions anomaly, power drop, fluctuation, underperformance, fault, or unexplained generation loss."
license: Apache-2.0
metadata:
  source: "awesome-llm-apps agent_skills format adapted for GuangFu"
---

# Anomaly Diagnosis

## Trigger Conditions

- User asks why power dropped or fluctuated.
- User mentions abnormal generation, fault, risk, or underperformance.

## Required Context

- `stationId`
- Time range when available.

## Allowed Tools

- `station.detail`
- `weather.current`
- `prediction.list`
- `prediction.detail`

## Execution Steps

1. Understand abnormal symptom and time range.
2. Query station detail.
3. Query weather.
4. Query prediction list/detail when available.
5. Evaluate whether missing power history prevents conclusion.
6. Return risk assessment and next actions.

## Approval Rules

Read-only diagnosis does not need approval. Any report generation must switch to `comprehensive-report` and request approval.

## Output Schema

```json
{
  "diagnosis": "string",
  "confidence": 0.0,
  "missingData": [],
  "risks": [],
  "recommendations": []
}
```

## Failure Handling

- If `power.history` is unavailable, explicitly state the limitation.
- Do not claim measured power anomaly without actual power data.

## Examples

- "分析 2 号电站今天功率波动原因。"
- "1 号电站发电下降是不是天气导致的？"

