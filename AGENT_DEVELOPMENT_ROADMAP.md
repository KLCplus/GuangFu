# AGENT_DEVELOPMENT_ROADMAP

目标：把当前“能调用工具的聊天 Agent”升级为“光伏智能运维 Agent”。  
原则：保留现有可用接口和表，分阶段重构，不一次性推翻。

## Phase 0：文档和架构整理

### 目标

- 固化当前能力、目标架构、差距和文件地图。
- 建立验收标准和测试脚本。

### 修改文件

- 新增/维护：
  - `CURRENT_PROJECT_CAPABILITY.md`
  - `AGENT_ARCHITECTURE_ANALYSIS.md`
  - `AWESOME_LLM_APPS_REFERENCE.md`
  - `TARGET_AGENT_ARCHITECTURE.md`
  - `AGENT_GAP_ANALYSIS.md`
  - `AGENT_DEVELOPMENT_ROADMAP.md`
  - `AGENT_FILE_MAP.md`

### 数据库变化

无。

### 接口变化

无。

### 验收

- 文档能让后续开发者定位当前 Agent 真实代码。
- 明确哪些功能已有、哪些只是目标设计。

## Phase 1：Agent Runtime 重构

### 目标

建立 Run/Step 生命周期，不再仅靠 SSE 临时事件表达过程。

### 新增模块

```text
backend/src/main/java/com/example/pvplatform/module/agent/runtime
├── AgentRuntimeService.java
├── AgentRunService.java
├── AgentStepService.java
├── AgentRunContext.java
├── AgentRunState.java
└── AgentSsePublisher.java
```

### 修改文件

- `AgentController.java`
  - 保留 `/api/agent/chat/stream`。
  - 内部委托 `AgentRuntimeService`。
- `AgentOrchestratorService.java`
  - 短期保留，逐步拆分职责。
  - 最终只作为兼容 facade 或删除。
- `AgentProgressService.java`
  - 升级为产品事件发布器。
- `AgentToolCallDO.java`
  - 增加 `runId`、`stepId`。

### 数据库变化

新增：

```sql
CREATE TABLE agent_run (
    run_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    message_id BIGINT DEFAULT NULL,
    user_task LONGTEXT NOT NULL,
    intent VARCHAR(128) DEFAULT NULL,
    status VARCHAR(32) NOT NULL,
    plan_json JSON DEFAULT NULL,
    summary TEXT DEFAULT NULL,
    error_message TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME DEFAULT NULL,
    KEY idx_agent_run_session (session_id, created_at),
    KEY idx_agent_run_user (user_id, created_at)
);

CREATE TABLE agent_run_step (
    step_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    step_key VARCHAR(64) NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    purpose VARCHAR(512) DEFAULT NULL,
    tool_name VARCHAR(128) DEFAULT NULL,
    status VARCHAR(32) NOT NULL,
    input_json JSON DEFAULT NULL,
    output_json JSON DEFAULT NULL,
    summary TEXT DEFAULT NULL,
    error_message TEXT DEFAULT NULL,
    started_at DATETIME DEFAULT NULL,
    completed_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_step_run (run_id, step_id),
    KEY idx_agent_step_session (session_id, created_at)
);
```

兼容迁移：

- `agent_tool_call` 增加 `run_id BIGINT NULL`、`step_id BIGINT NULL`。
- 旧数据允许为空。

### 接口变化

新增：

- `GET /api/agent/runs/{runId}`
- `GET /api/agent/sessions/{sessionId}/runs`

保留：

- `POST /api/agent/chat/stream`
- `GET /api/agent/sessions/{sessionId}/messages`

### 验收

- 用户发起任务后，数据库有 `agent_run`。
- 每次工具调用对应 `agent_run_step`。
- 刷新页面后能恢复 Run 过程。

## Phase 2：Tool 系统升级

### 目标

工具从“Java Bean + inputSchema 字段”升级为标准化 Tool Definition + Executor + Formatter。

### 新增模块

```text
backend/src/main/java/com/example/pvplatform/module/agent/tool/definition
├── ToolDefinition.java
├── ToolInputSchema.java
├── ToolOutputSchema.java
├── ToolRiskPolicy.java
└── ToolExample.java

backend/src/main/java/com/example/pvplatform/module/agent/tool/executor
├── ToolExecutor.java
├── ToolValidationService.java
└── ToolDisplayFormatter.java
```

### 修改文件

- `AgentTool.java`
  - 增加 `definition()` 或逐步兼容旧方法。
- `AgentToolRegistry.java`
  - 增加 `listToolsForUser(SecurityUser user)`。
  - 增加 `enabledReason/disabledReason`。
- `ToolExecutionResult.java`
  - 增加 `artifact`、`durationMs`、`references`。
- `AgentToolService.java`
  - 只负责持久化和脱敏；执行逻辑迁到 `ToolExecutor`。

### 第一批新增工具

| Tool | 数据来源 | 说明 |
| - | - | - |
| `power.current` | `pv_data` 最新记录 | 当前功率、今日发电量 |
| `power.history` | `pv_data` 时间范围 | 今日/指定时间段功率曲线 |
| `power.comparePrediction` | `pv_data` + `prediction_result` | 实际和预测对比 |
| `power.anomaly` | `pv_data` | 波动、下滑、离群检测 |
| `weather.forecast` | `WeatherService.forecast` | 天气预报 |
| `station.status` | `power_station` + 最近数据 | 电站运行摘要 |

### 升级现有工具

- `prediction.list`：返回最新成功任务 highlight。
- `prediction.detail`：增加波动幅度、峰值、趋势和异常点。
- `api.usage`：改用 usage summary/trend/by-key。
- `report.generate`：支持从 artifacts 填充上下文。

### 数据库变化

- `agent_tool_call.result_json` 保持。
- 可选新增 `agent_tool_definition_snapshot` 保存执行时工具定义快照。

### 接口变化

- `GET /api/agent/tools` 增加 outputSchema/riskLevel/disabledReason。

### 验收

- `/weather 2` 只调用天气工具。
- “分析 2 号电站功率波动”会调用 power + weather + prediction。
- 工具失败保留 FAILED 记录并前端可见。

## Phase 3：Memory

### 目标

让 Agent 能记住会话目标、最近业务对象、用户偏好和报告风格。

### 新增模块

```text
backend/src/main/java/com/example/pvplatform/module/agent/memory
├── AgentMemoryService.java
├── ConversationMemoryService.java
├── UserMemoryService.java
├── StationContextMemoryService.java
├── ToolResultMemoryService.java
└── MemoryCompactor.java
```

### 数据库变化

新增：

```sql
CREATE TABLE agent_session_summary (
    session_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    summary TEXT DEFAULT NULL,
    current_goal VARCHAR(512) DEFAULT NULL,
    context_json JSON DEFAULT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE agent_user_memory (
    memory_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    memory_key VARCHAR(128) NOT NULL,
    memory_value_json JSON NOT NULL,
    source VARCHAR(64) DEFAULT NULL,
    confidence DECIMAL(5,4) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_memory_key (user_id, memory_key)
);
```

可选：

```sql
CREATE TABLE agent_business_context (
    context_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id BIGINT DEFAULT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id BIGINT DEFAULT NULL,
    context_json JSON NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_agent_business_context_user (user_id, object_type, updated_at)
);
```

### 接口变化

内部优先，无需先暴露给前端。

可选：

- `GET /api/agent/memory/profile`
- `PUT /api/agent/memory/preferences`

### 验收

- 用户刚查询 2 号电站后，再问“分析这个电站天气”，Agent 可以使用最近上下文，或清晰提示“我将使用刚才的 2 号电站”。
- 会话刷新后仍保留会话摘要。

## Phase 4：Skill 系统

### 目标

把高频业务流程封装成 Skill，减少 Planner 和 Orchestrator 中的硬编码。

### 新增模块

```text
backend/src/main/java/com/example/pvplatform/module/agent/skill
├── AgentSkill.java
├── SkillDefinition.java
├── SkillRouter.java
├── SkillPlan.java
├── SkillResult.java
└── impl
    ├── PowerAnalysisSkill.java
    ├── WeatherImpactSkill.java
    ├── PredictionDiagnosisSkill.java
    ├── ReportGenerationSkill.java
    ├── ModelSelectionSkill.java
    └── ApiUsageSkill.java
```

### Skill 优先级

| Skill | 首批必须做 | 说明 |
| - | - | - |
| `PowerAnalysisSkill` | 是 | 解决功率波动/发电下降核心场景 |
| `WeatherImpactSkill` | 是 | 天气对发电影响 |
| `PredictionDiagnosisSkill` | 是 | 预测结果解释 |
| `ReportGenerationSkill` | 是 | 正式报告和会话报告 |
| `ModelSelectionSkill` | 否 | 模型推荐 |
| `ApiUsageSkill` | 否 | API 使用解释 |

### 修改文件

- `AgentOrchestratorService` 中 `comprehensiveAnalysisCalls` 迁入 `PowerAnalysisSkill`。
- `SlashCommandParser` 只负责 command -> intent，不负责完整业务编排。
- `LlmPromptBuilder` 增加 skill 描述。

### 验收

- “分析 2 号电站今天功率波动”由 `PowerAnalysisSkill` 生成计划。
- 不再在 Orchestrator 里硬编码综合分析工具序列。

## Phase 5：Agent UI 升级

### 目标

从单文件聊天页升级为 Codex 风格 Agent 工作台。

### 新增组件

```text
web-frontend/src/components/agent
├── AgentWorkbench.vue
├── AgentSessionSidebar.vue
├── AgentMessageList.vue
├── AgentMessageBubble.vue
├── AgentRunCard.vue
├── AgentStepList.vue
├── AgentToolCard.vue
├── AgentApprovalCard.vue
├── AgentArtifactCard.vue
├── AgentMarkdown.vue
├── AgentComposer.vue
└── AgentSignatureDialog.vue
```

### 修改文件

- `web-frontend/src/views/AnalysisReport.vue`
  - 改成容器页面，移出主要逻辑。
- `web-frontend/src/api/agent.ts`
  - 增加 run/artifact API 类型。
  - 适配 v2 SSE events。

### UI 原则

- 左侧会话只表示用户会话，不绑定电站。
- 中间消息流展示 run steps。
- 工具结果只显示业务摘要。
- Markdown 使用正式 parser 或严格白名单。
- 签名、打印作为报告 artifact 的操作。
- 不展示 payload/response/rawOutput/sessionId/token。

### 验收

- 消息区可正常滚动。
- `/` 菜单支持上下键、Enter 选择。
- 工具过程在 assistant 消息上方实时替换/更新。
- report approval 卡片产品化。
- 刷新页面恢复 run 和消息。

## Phase 6：测试和评估

### 目标

建立可重复验证的 Agent 质量门槛。

### 修改文件

- `scripts/agent-e2e-test.mjs`
  - 扩展为 run/step 断言。
- 新增：
  - `scripts/agent-regression-cases.json`
  - `AGENT_EVALUATION.md`

### 核心场景

| 输入 | 预期 |
| - | - |
| `查看 2 号电站信息` | `station.detail` |
| `/weather 2` | `weather.current` |
| `解释任务 8 的预测结果` | `prediction.detail` |
| `分析 2 号电站今天功率波动，结合天气和预测` | `PowerAnalysisSkill` + power/weather/prediction |
| `生成 2 号电站综合分析报告` | approval_required -> report.generate |
| `分析这个电站天气` | 使用最近上下文或追问 |
| `什么是光伏功率预测？` | 不调用工具，直接回答 |
| `解释任务 999999 的预测结果` | 工具失败可见，最终说明失败原因 |

### 评估项

- 是否调用预期工具。
- 是否无脑调用多余工具。
- 是否缺参追问。
- 是否基于工具结果回答。
- 是否泄露敏感字段。
- 是否有可读业务摘要。
- 写操作是否确认。

### 验收

- E2E 脚本可在 `AGENT_TEST_TOKEN` 下运行。
- 每次重构后能快速回归。

## 推荐执行顺序

1. Phase 1 先做 Run/Step，因为它影响后端、前端和测试。
2. Phase 2 补 power/weather forecast 工具，否则业务分析仍然空。
3. Phase 4 做 PowerAnalysisSkill，替换硬编码综合分析。
4. Phase 3 Memory 与 Phase 5 UI 可以并行，但 UI 最终应基于 Run/Step。
5. Phase 6 贯穿每一阶段，不要最后才补测试。

## 回退策略

- 保留 `/api/agent/chat/stream` 路径。
- 保留旧 `started/thinking/tool_call/tool_result/final` 事件一段时间。
- 新增 v2 events 时前端可 feature flag 切换。
- 新表允许空关联，不破坏旧消息。
- 工具改造先兼容旧 `AgentTool`，再逐步迁移。
