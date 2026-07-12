# Agent Improvement Report

## 本轮目标

本轮没有继续堆新工具，而是围绕“像真正项目级 Agent”做收敛：审计现状、定义验收标准、增强核心工具业务摘要、让前端工具卡展示业务要点，并补充可重复执行的 SSE E2E 测试脚本。

## 已完成改动

### 1. 审计和验收标准

- 新增 `AGENT_AUDIT_REPORT.md`，重新审计后端编排、工具调用、SSE、会话持久化、确认机制和 `/reports` 前端体验。
- 新增 `AGENT_ACCEPTANCE.md`，定义查询电站、查询天气、预测分析、综合分析、报告生成、缺参追问和普通聊天的验收标准。

### 2. ToolExecutionResult 业务化

`ToolExecutionResult` 增加：

- `displayName`
- `highlights`

保留：

- `success`
- `summary`
- `data`
- `raw`
- `errorCode`
- `errorMessage`

`AgentToolService` 的工具调用记录写入同步适配新字段，并继续对 `data/raw` 做脱敏，避免 API Key、token、密码等敏感信息进入前端或持久化结果。

### 3. 核心工具专属摘要

已增强这些工具的业务摘要和 highlights：

| 工具 | 改进 |
| --- | --- |
| `station.detail` | 展示电站名称、容量、位置、状态 |
| `station.list` | 展示可访问电站数量和前几个电站 |
| `weather.current` | 展示天气、温度、湿度、风速和发电影响判断 |
| `prediction.detail` | 展示任务状态、模型、预测点数、功率范围和趋势判断 |
| `prediction.list` | 展示最近任务数、失败任务数和最近任务状态 |
| `report.generate` | 展示报告标题、reportId、风险等级、保存状态和摘要 |
| `api.usage` | 展示最近调用次数、成功/失败次数、平均延迟和最近错误 |
| `model.list` | 展示模型数量、类别、推荐模型和前几个模型 |
| `model.detail` | 展示模型名称、类型、状态、预测范围和指标数量 |

### 4. Prompt 强化

`LlmPromptBuilder` 增加约束：

- 最终回答必须引用工具 `summary/highlights` 或关键真实字段。
- final 回答优先使用“摘要、关键发现、影响因素、预测趋势、异常判断、运维建议、后续操作”。
- 工具失败时必须说明失败原因和下一步建议。
- 增加综合分析示例，要求按需调用 station/weather/prediction，不无脑调用全部工具。

### 5. 前端工具卡展示优化

`/reports` 工具卡现在优先展示后端 `summary/highlights`：

- 折叠态显示工具名、状态、摘要。
- 展开态显示业务要点列表。
- 不展示完整 JSON。
- 不展示 payload、response、rawOutput、token、API Key。

### 6. E2E 脚本

新增 `scripts/agent-e2e-test.mjs`：

- 使用 `AGENT_TEST_TOKEN` 读取登录 token。
- 默认访问 `AGENT_BASE_URL=http://127.0.0.1:8080`。
- 测试 `/station 2`、`/weather 2`、`/predict 8`、`/report 2`、普通问题。
- 输出 SSE 事件序列、工具调用和通过情况。
- 不打印 token。

## 当前仍需后续完善

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 结构化 final 组件 | 部分完成 | Prompt 已要求结构化，前端可显示换行文本；后续可把 sections/risk/suggestions 做专门组件。 |
| 综合分析任务质量 | 部分完成 | 后端已有 ReAct 和规则兜底，质量仍依赖 LLM 对工具结果的二次总结。 |
| live E2E | 待执行 | 当前环境没有 `AGENT_TEST_TOKEN`，脚本已验证无 token 保护路径，真实接口需带 token 运行。 |
| API 使用统计 | 部分完成 | 当前使用最近日志聚合，后续可接入 summary/trend 接口获得今日调用量和错误率。 |

## 验证

| 命令 | 结果 |
| --- | --- |
| `mvn -q -DskipTests compile` | 通过 |
| `npm run build` | 通过，只有第三方 pure annotation 和 chunk size 警告 |
| `node scripts/agent-e2e-test.mjs` | 脚本可运行；因未设置 `AGENT_TEST_TOKEN` 按预期退出 |
