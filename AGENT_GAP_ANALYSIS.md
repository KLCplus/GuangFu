# AGENT_GAP_ANALYSIS

审计日期：2026-07-12  
目标：对比当前 Agent 与目标“光伏智能运维 Agent”的差距。

| 模块 | 当前状态 | 目标状态 | 需要改造 |
| - | - | - | - |
| Agent Runtime | `AgentOrchestratorService` 集中处理会话、意图、LLM、工具、审批、SSE | 独立 Runtime 管理 Run/Step/Artifact 生命周期 | 拆出 `AgentRuntimeService`、`AgentRunService`、`AgentStepService` |
| Run 模型 | 无 `agent_run`，一次请求只保存 message/tool_call/approval | 每次用户任务生成 run，步骤可持久化、可恢复、可测试 | 新增 `agent_run`、`agent_run_step` 或等价模型 |
| Planner | Slash/规则/LLM 混在 Orchestrator；综合分析有硬编码工具组合 | 可审计 Planner 输出 Plan 和每步目的 | 新增 `planner` 包，迁移 `SlashCommandParser` 和 `comprehensiveAnalysisCalls` |
| Tool Registry | 有基础 registry，Spring Bean 自动注册 | 工具有版本、schema、输出类型、风险策略、角色过滤 | 增加 `ToolDefinition`、`ToolOutputSchema`、`RiskPolicy` |
| Tool Executor | `AgentToolService` 负责 pending、execute、mask、update | Executor 支持 timeout、retry、dependency、artifact、统一错误 | 拆出 `ToolExecutor`，保留持久化服务 |
| Tool Result | 已有 `success/displayName/data/summary/highlights/error` | 增加 artifact、duration、typed output、引用字段 | 升级 `ToolExecutionResult` 和 DTO |
| 工具校验 | 分散在 `AbstractAgentTool.longArg/stringArg` | 先 schema 校验，再 execute | 引入参数校验层 |
| 权限 | Tool 内部校验 + Spring Security | Registry 按用户过滤；Executor 再强校验 | `listToolsForUser(user)`、角色/权限策略 |
| 审批 | `agent_approval` 已有；approve 后需前端再发 stream | 审批是 run 暂停/恢复机制；前端不伪造用户续跑消息 | 新增 run resume 或 approve 后直接续跑 |
| DeepSeek 调用 | 非流式 JSON mode，工具结果回灌后再调用 | Planner/Synthesizer 分离；可流式输出最终回答 | 拆 `LlmPlanner`、`LlmSynthesizer` |
| JSON Parser | 手动解析 JSON；失败转 final | JSON Schema 校验，失败进入可见错误/重试 | 增加 structured parser 和 repair 策略 |
| Prompt | 动态注入工具列表和规则 | 按角色拆：Planner prompt、Synthesizer prompt、Evaluator prompt | Prompt 模块化 |
| Memory | 最近 8 条消息 | Conversation/User/Station/Report/Tool/Knowledge 多层记忆 | 新增 memory 服务和表 |
| Conversation Memory | 保存消息，但没有摘要 | 会话摘要、当前目标、已知业务对象 | 新增 `agent_session_summary` |
| User Memory | 无 | 用户偏好、常用电站、报告格式偏好 | 新增 `agent_user_memory` |
| Station Context | request context 可带 stationId，但 UI 应独立会话 | 最近使用电站是上下文，不是会话绑定 | 新增上下文记忆并调整 UI 文案 |
| Skill | 无 Skill 抽象 | PowerAnalysis/WeatherImpact/PredictionDiagnosis/ReportGeneration 等技能 | 新增 `module/agent/skill` |
| 光伏核心分析 | 没有 `power.*` 工具；只能查天气/预测 | 能读功率历史、对比预测、判断异常 | 新增 `power.current/history/compare/anomaly` |
| 天气分析 | `weather.current/location` 已有；无 forecast tool | 当前+预报+发电影响 | 新增 `weather.forecast` |
| 预测分析 | `prediction.detail` 能查任务和结果 | 自动选择最近成功任务、趋势/异常/误差分析 | 强化 `prediction.list/detail` formatter |
| 报告生成 | `report.generate` 写 `analysis_report`；`report.conversation` 生成 Markdown | 支持从 run artifacts 生成正式报告和导出 | 报告工具接入 artifacts 和导出 |
| API 工具 | `api.usage` 查最近 20 条日志 | 汇总、趋势、按 Key/模型维度 | 改用 `usage/summary/trend/by-key/by-model` |
| Artifact | 无独立 artifact 表 | 天气摘要、功率图、预测摘要、报告草稿可持久化 | 新增 `agent_artifact` |
| SSE 协议 | `started/thinking/intent_resolved/plan/tool_call/tool_result/approval_required/final/error`，另有 `llm_decision/final_intercepted` | 产品事件和 dev 事件分离，run/step 语义明确 | 定义 v2 SSE envelope |
| 前端结构 | `AnalysisReport.vue` 单文件大组件 | Agent 工作台组件化 | 拆 `components/agent/*` |
| 前端消息流 | 能显示消息、步骤、工具、审批 | 按 run 展示进度、artifact 和最终 Markdown | 引入 `AgentRunCard`/`AgentArtifactCard` |
| Slash Command | 有菜单，上下键/Tab/Enter | 保持自然入口；后端 slash 优先 | 调整优先级和参数提示 |
| Markdown | 正则渲染 | 安全 Markdown parser + 样式 | 引入可靠 markdown 渲染库或严格白名单 |
| 调试信息 | 前端不展示主要调试事件，但后端仍推 `llm_decision` | 默认不下发 dev 事件，开发模式可开 | `devMode` 控制 |
| 测试 | 有 `scripts/agent-e2e-test.mjs` | 覆盖核心工具、审批、缺参、普通聊天、错误 | 扩展 E2E 断言 run/step/artifact |
| 评估 | 无自动 grounding/evaluator | 评估最终回答是否基于工具结果 | 新增 evaluator |
| MCP | 无 MCP Server | 工具协议 MCP-ready，未来可拆 MCP Server | 先标准化 tool schema |

## 最高优先级差距

### 1. 缺少 Run/Step

当前过程只在 SSE 中短暂出现，刷新后主要依赖 message/toolCalls 重建，不足以支撑 Codex 风格工作流。

影响：

- 用户看不到稳定任务进度。
- 测试只能断言 SSE 事件，不易审计。
- 审批恢复和失败重试不自然。

改造：

- 新增 `agent_run`。
- 新增 `agent_run_step`。
- `tool_call` 关联 `run_id/step_id`。

### 2. 缺少 Planner

当前综合分析的多工具选择由 `comprehensiveAnalysisCalls` 规则完成，不是真正计划。

影响：

- 复杂任务扩展困难。
- 工具调用理由不可审计。
- 业务流程和 Orchestrator 强耦合。

改造：

- 建立 `Plan` 对象。
- 建立 `PlannerFacade`。
- 把 slash/rule/LLM 都归一成 Plan。

### 3. 缺少功率数据工具

用户最常见问题是“发电下降/功率波动/异常原因”，但当前没有 Agent 工具读取 `pv_data`。

影响：

- 分析只能依赖天气和预测，容易泛泛而谈。
- 无法验证“波动”是否真实发生。

改造：

- `power.current`
- `power.history`
- `power.comparePrediction`
- `power.anomaly`

### 4. 缺少 Memory

用户希望 Agent “自己工作”，但当前每次缺参都容易追问，或者依赖前端 context。

影响：

- 体验像一次性问答。
- 无法记住常用电站/任务/报告格式。

改造：

- 会话摘要。
- 用户偏好。
- 最近业务对象。

### 5. 前端组件化不足

`AnalysisReport.vue` 过重，任何体验调整都会影响大范围逻辑。

影响：

- UI 迭代慢。
- 消息滚动、Slash、工具卡、签名、报告都耦合。

改造：

- 拆 Agent 组件。
- 使用 run/step 状态驱动 UI。

## 当前可保留的核心代码

| 代码 | 保留理由 | 改造方式 |
| - | - | - |
| `AgentController` | API 已基本完整 | 保留路径，内部委托 Runtime |
| `AgentSessionService` | 会话 CRUD 可用 | 增加摘要/最近 run，不重写 |
| `AgentMessageService` | 消息持久化可用 | 关联 run/artifact |
| `AgentToolService` | 工具记录和脱敏有价值 | 拆执行和持久化职责 |
| `AgentApprovalService` | 用户确认基本正确 | 增加 run resume |
| `AgentToolRegistry` | Bean 注册简单有效 | 升级定义、过滤、schema |
| 现有 AgentTool 实现 | 已接真实业务 Service | 加 output schema/formatter |
| `DeepSeekAgentLlmClient` | 可继续作为 LLM Adapter | 拆 planner/synthesizer 调用 |
| `LlmPromptBuilder` | 规则已有积累 | 分拆多个 prompt |
| `streamAgentChat` | fetch SSE 正确方向 | 适配 v2 events |

## 不建议继续做的事

1. 不要继续把复杂业务流程塞进 `AgentOrchestratorService`。
2. 不要用更多关键词 if/else 替代 Planner。
3. 不要前端固定调用所有工具来制造“Agent 在工作”的假象。
4. 不要把调试 JSON 重新暴露给正式用户。
5. 不要让报告生成永远依赖 stationId；会话报告和电站报告应是两类能力。
6. 不要用 mock 数据填补功率分析，必须接真实 `pv_data`。

## 结论

当前 Agent 的基础闭环已经存在，下一步不是推翻重写，而是做架构分层：

```text
AgentController
  -> AgentRuntime
      -> Planner
      -> Memory
      -> Skill Router
      -> Tool Executor
      -> Evaluator
      -> SSE Publisher
```

只有完成这些分层，前端才能从“聊天框 + 工具卡”升级为真正的 Agent 工作台。
