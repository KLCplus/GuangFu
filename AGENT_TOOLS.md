# Agent Tools

All tools are registered in `AgentToolRegistry` and exposed by `GET /api/agent/tools`.

| Tool | Category | Permission | Approval | Backend service |
| --- | --- | --- | --- | --- |
| `station.list` | STATION | READ_ONLY | No | `StationService.list` |
| `station.detail` | STATION | READ_ONLY | No | `StationPermissionService.requireView`, `StationService.detail` |
| `weather.current` | WEATHER | READ_ONLY | No | `StationPermissionService.requireView`, `WeatherService.current` |
| `prediction.list` | PREDICTION | READ_ONLY | No | `StationPermissionService.requireView`, `PredictionService.history` |
| `prediction.detail` | PREDICTION | READ_ONLY | No | `PredictionService.detail`, `PredictionService.results` |
| `report.list` | REPORT | READ_ONLY | No | `AnalysisService.history` |
| `report.detail` | REPORT | READ_ONLY | No | `AnalysisService.detail` |
| `report.generate` | REPORT | WRITE | Yes | `AnalysisService.report` |
| `model.list` | MODEL | READ_ONLY | No | `ModelService.list` |
| `api.usage` | API | READ_ONLY | No | `ApiCallLogService.ownLogs` |

## Input Schemas

### station.list

```json
{}
```

### station.detail

```json
{ "stationId": 2 }
```

### weather.current

```json
{ "stationId": 2 }
```

### prediction.list

```json
{ "stationId": 2 }
```

### prediction.detail

```json
{ "taskId": 8 }
```

### report.list

```json
{ "stationId": 2 }
```

`stationId` is optional.

### report.detail

```json
{ "reportId": 1 }
```

### report.generate

```json
{
  "stationId": 2,
  "taskId": 8,
  "title": "2号电站综合分析报告",
  "includeWeather": true,
  "includePrediction": true
}
```

`taskId` is optional. This tool writes a report and requires approval.

### model.list

```json
{ "category": "NUMERIC" }
```

`category` is optional.

### api.usage

```json
{}
```

## Disabled/TODO Tool Scope

The first implementation intentionally does not fake these tools:

- `cloud.predict`
- `model.run`
- `marketplace.list`
- `wallet.balance`
- `news.list`
- `admin.userApi.list`
- `admin.station.update`
- `admin.model.update`

They should be added only after their real service contracts and permission checks are confirmed.
