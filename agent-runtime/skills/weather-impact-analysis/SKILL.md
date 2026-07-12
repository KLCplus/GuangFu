---
name: weather-impact-analysis
description: "Analyze weather impact on photovoltaic generation. Use when the user asks about weather, cloud, temperature, rain, wind, irradiance impact, or weather-driven power fluctuation."
license: Apache-2.0
metadata:
  source: "awesome-llm-apps agent_skills format adapted for GuangFu"
---

# Weather Impact Analysis

## Trigger Conditions

- User asks whether weather affects generation.
- User mentions cloud, rain, snow, fog, temperature, humidity, or wind.

## Required Context

- `stationId` or a location.
- User permission for the station when station-bound.

## Allowed Tools

- `station.detail`
- `weather.current`

## Execution Steps

1. Identify station or location.
2. Query station detail if station-bound.
3. Query current weather.
4. Map weather factors to photovoltaic impact.
5. Return a weather impact card and recommendations.

## Approval Rules

No approval for read-only weather analysis.

## Output Schema

```json
{
  "weather": {},
  "impact": "string",
  "riskLevel": "low|medium|high",
  "recommendations": []
}
```

## Failure Handling

- If station ID is missing, ask for station ID or location.
- If weather provider fails, report the provider error and do not invent weather.

## Examples

- "2 号电站天气怎么样，会影响发电吗？"
- "今天多云对发电影响大吗？"

