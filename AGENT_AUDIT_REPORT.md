# Agent Audit Report

## 审计范围

本次重新审计了当前 Agent 的后端编排、工具注册、工具实现、DeepSeek ReAct、SSE、会话/消息/工具/确认持久化，以及 `/reports` 前端工作台的会话栏、消息流、输入框、slash command、工具卡片、确认卡片和正式 UI 残留。

## 关键结论

当前 Agent 已经具备真实工具调用链路，但体验仍不稳定地像 Codex。主要原因不是“没有工具”，而是工具结果没有形成统一业务摘要、最终回答没有结构化约束、前端把 SSE 事件合并成粗粒度步骤卡，用户看到的是“调用过工具”，但不一定感受到 Agent 正在高质量完成业务任务。

## 必答问题

| 问题 | 结论 |
| --- | --- |
| 1. 当前为什么不像 Codex？ | Codex 感的核心是任务拆解、执行痕迹、文件/工具结果转为清晰结论。当前有步骤卡，但步骤标题和摘要偏通用，工具结果只靠 `summary/dataPreview` 粗略展示，最终回答还是一段文本。 |
| 2. 当前为什么用户感受不到 Agent 在做事？ | SSE 事件存在，但前端把 `thinking/intent/tool_call/tool_result/final` 映射为少量步骤；每个工具没有专属业务卡片和 highlights，用户看不到“拿到了哪些关键数据”。 |
| 3. 当前工具调用是否真的影响最终回答？ | 工具结果会追加回 LLM 上下文，理论上影响最终回答；但 prompt 对最终回答结构和“引用工具字段”的约束不够强，LLM 不可用时仅降级为工具摘要列表。 |
| 4. 当前 SSE 事件是否被正确转换成用户可见步骤？ | 基本转换了：`started/thinking/intent_resolved/plan/tool_call/tool_result/approval_required/final/error` 都有处理。但 `llm_decision/final_intercepted` 被忽略，工具结果业务化不足。 |
| 5. 当前前端是否只是简单展示文本？ | 不完全是。已有执行卡、工具卡、确认卡，但 final 仍是纯文本，缺少摘要/风险/建议等结构化组件。 |
| 6. 当前 slash command 是否只是输入提示，而不是工具入口？ | 前端目前只插入文本，后端 `SlashCommandParser` 会解析 `/station /weather /predict /report /model /api`，所以不是纯装饰。但前端模板是自然语言，不够明确地提示参数格式。 |
| 7. 当前业务分析为什么粗糙？ | `ToolExecutionResult` 只有 `summary/data/raw/error`，没有 `displayName/highlights`；核心工具 summary 多是“已获取 xxx”，缺少容量、天气影响、预测趋势等业务字段。 |
| 8. 当前哪些工具是真实可用的？ | 第一批核心工具均调用真实 Service：`station.list/detail`、`weather.current`、`prediction.list/detail`、`report.generate/list/detail`、`model.list/detail`、`api.usage`。 |
| 9. 哪些工具只是注册了但体验不可用？ | 部分 admin/marketplace/cloud/wallet/API 写操作虽然注册或预留，但参数、权限和产品确认不足；disabled 工具不会被调用。 |
| 10. 当前最应该优先改哪 5 个问题？ | 统一工具结果结构；核心工具专属摘要；最终回答结构化；前端工具卡显示 highlights；补 E2E 脚本验证工具链。 |

## 后端审计

### AgentController

- `/api/agent/chat/stream` 使用 `SseEmitter`，保留认证上下文后异步执行。
- 会话、消息、工具调用、归档、固定、重命名、删除接口已存在。
- `/api/agent/tools` 返回工具注册表。

### AgentOrchestratorService

优点：
- 支持最多 5 轮工具循环。
- 支持 `approvalId` 继续执行确认后的工具。
- 业务问题 final 会被拦截。
- 工具结果会写回 LLM 上下文。
- LLM 不可用但已有工具结果时会降级返回工具摘要。

问题：
- `intent_resolved/llm_decision/final_intercepted` 仍作为 SSE 发出，前端正式 UI 忽略，但调试信息仍在网络流中。
- 综合分析兜底规则仍是关键词启发式，复杂任务质量依赖 LLM。
- `resultPayload` 输出字段仍叫 `dataPreview`，前端虽不直接展示 JSON，但结构不够产品化。
- 工具失败后最终回答质量依赖 LLM；如果 LLM 不可用，仅返回简短失败列表。

### SlashCommandParser

优点：
- 支持 `/station /weather /predict /report /model /api`。
- 缺少必要参数会追问。
- 自然语言业务问题可解析为工具意图。

问题：
- 自然语言规则仍较硬编码。
- `preferredTool` 仍保留在 DTO 和后端优先级中，虽然前端当前不再发送。
- 综合分析多工具计划在 Orchestrator 中实现，职责和 parser/intent resolver 边界不够清晰。

### LlmPromptBuilder / LlmToolCallParser / DeepSeek

优点：
- Prompt 已要求业务问题优先工具、最终回答基于工具结果。
- Parser 能清理 markdown code block，并兼容 `toolCalls/tool_calls`。
- DeepSeek 使用 JSON mode。

问题：
- Prompt 没有为最终回答定义业务结构：摘要、影响因素、风险、建议。
- 工具描述是注册表动态生成，但没有为每个工具生成“何时使用/必填参数/示例”的更丰富说明。
- 非法 JSON 解析失败后被当作 final，依赖后端业务 final 拦截兜底。

### AgentToolRegistry / Tools

真实可用核心工具：

| 工具 | 状态 | 备注 |
| --- | --- | --- |
| `station.list` | 可用 | 调 `StationService.list` |
| `station.detail` | 可用 | 调 `StationService.detail`，有权限校验 |
| `weather.current` | 可用 | 调 `WeatherService.current` |
| `prediction.list` | 可用 | 调 `PredictionService.history` |
| `prediction.detail` | 可用 | 调 `PredictionService.detail/results` |
| `report.generate` | 可用，需确认 | 调 `AnalysisService.report` |
| `report.list` | 可用 | 调 `AnalysisService.page` |
| `report.detail` | 可用 | 调 `AnalysisService.detail` |
| `model.list` | 可用 | 调 `ModelService.list` |
| `model.detail` | 可用 | 调 `ModelService.detail` |
| `api.usage` | 可用 | 调 `ApiCallLogService.ownLogs` |

主要问题：
- `ToolExecutionResult` 缺少 `displayName`、`highlights`。
- 核心工具 summary 大多是“已获取...”，业务信息不足。
- `api.usage` 只返回日志页，未聚合今日调用量、错误率、平均延迟。

### 持久化和确认

- `agent_session`、`agent_message`、`agent_tool_call`、`agent_approval` 相关服务已存在。
- 工具调用 PENDING/SUCCESS/FAILED/AWAITING_APPROVAL 有记录。
- `report.generate` 会在确认后执行。
- 确认拒绝会返回取消消息。

问题：
- approval 消息和工具结果历史恢复依赖前端二次映射，结构不够专门。
- 确认卡片 reason 偏技术：“WRITE 级别动作”，应产品化为“会创建新的报告记录”。

## 前端审计

### /reports 页面

优点：
- 已删除右侧 Inspector。
- 页面主结构是左侧会话栏 + 中间消息流 + 底部输入框。
- 不显示 payload/response/raw JSON。
- 工具结果是折叠卡片。

问题：
- 消息流仍是单文件巨型组件，后续维护困难。
- final 仅以纯文本展示，缺少业务结构组件。
- 工具摘要大量依赖后端 summary，后端 summary 粗时前端无法补出高质量业务说明。
- 会话历史恢复只展示工具卡和文本，不能恢复完整执行步骤。
- GPT/Codex 感仍不足：缺少“任务计划”和“关键发现”在最终回答中的稳定结构。

### Slash command

- 输入 `/` 有菜单。
- 选择后插入自然语言模板。
- `/weather 2` 这类命令能由后端解析。

问题：
- 模板如“查询电站天气 ”没有明显参数提示。
- 前端不会在菜单里展示“例：/weather 2”。

### 调试和 mock 残留

- 正式 UI 未发现 Inspector、DebugRecord、payload/response/rawOutput 展示。
- 未发现 `setTimeout` 模拟步骤。
- `dataPreview` 仍在内部变量中使用，但不直接向用户展示完整 JSON。
- 构建产物中有 `mock` chunk，来自项目其他模块，不是 `/reports` Agent UI 的 mock 工具结果。

## 优先级建议

1. 扩展 `ToolExecutionResult`，增加 `displayName/highlights`。
2. 为核心工具实现专属业务摘要和 highlights。
3. 前端工具卡优先展示 highlights，并把 final 结果做结构化展示。
4. Prompt 增加最终回答结构要求，并要求引用工具 highlights。
5. 新增 `scripts/agent-e2e-test.mjs`，用真实 SSE 验证核心场景。

