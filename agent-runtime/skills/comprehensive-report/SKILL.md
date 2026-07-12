---
name: comprehensive-report
description: "Prepare or generate photovoltaic analysis reports. Use when the user asks for a report, formal summary, export, or saved analysis record."
license: Apache-2.0
metadata:
  source: "awesome-llm-apps agent_skills format adapted for GuangFu"
---

# Comprehensive Report

## Trigger Conditions

- User asks to generate, save, export, or preview a report.
- User requests a formal analysis conclusion.

## Required Context

- `stationId` for station report.
- `sessionId` for conversation report.
- Current user approval before write tools.

## Allowed Tools

- `station.detail`
- `weather.current`
- `prediction.list`
- `prediction.detail`
- `report.generate`

## Execution Steps

1. Collect read-only evidence first.
2. Build report preview.
3. Request approval for `report.generate`.
4. Execute only after approval.
5. Return report preview/result card.

## Approval Rules

`report.generate` always requires user confirmation.

## Output Schema

```json
{
  "preview": {},
  "approvalRequired": true,
  "reportId": 0,
  "ui": []
}
```

## Failure Handling

- If user rejects, stop without writing.
- If report generation fails, show error recovery card.

## Examples

- "生成 1 号电站综合分析报告。"
- "把这次分析保存成报告。"

