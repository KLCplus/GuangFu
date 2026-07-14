# Alibaba Agent E2E Matrix

Run the following cases with `AGENT_RUNTIME_MODE=alibaba`, a configured `DEEPSEEK_API_KEY`, and authorized seeded station data. Each case uses the production SSE endpoint and real business services; no response is mocked.

| Case | Input or condition | Required evidence |
| --- | --- | --- |
| 1 | Analyze station 1 with weather and prediction | `station.detail`, weather, power and prediction tool records; evidence-based final response |
| 2 | Ask about station 1, then ask for its seven-day power | Second request resolves the prior station context |
| 3 | Set default station 2, then analyze default station | `agent_memory` drives tool arguments without exposing storage details |
| 4 | Request an unauthorized station | Tool returns authorization error and no follow-up data is invented |
| 5 | Remove weather data | Final result marks weather unavailable but retains valid power/prediction evidence |
| 6 | Use a station with no prediction | No `prediction.detail` record is created and the final response discloses the gap |
| 7 | Generate a station report and approve | Approval, tool call and generated report records are persisted |
| 8 | Generate a station report and reject | Approval is rejected and no report write occurs |
| 9 | Disable or invalidate DeepSeek | SSE has `ALIBABA_AGENT_ERROR`; no false assistant final message is saved; legacy mode remains usable |
| 10 | Refresh after tool execution or pending approval | `run-recovery` reconstructs the latest timeline and state |

The workspace `.env` was used for live verification without printing its secrets. The automated unit, regression, frontend-build and retained Python-runtime checks are recorded in the migration report. The weather-removal variation was not executed against shared data because it would require deleting or mutating records.
