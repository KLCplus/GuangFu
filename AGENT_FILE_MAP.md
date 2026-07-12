# AGENT_FILE_MAP

审计日期：2026-07-12  
目标：给后续开发者一份可直接定位代码的文件地图。

## 1. 后端总览

```text
backend/src/main/java/com/example/pvplatform
├── PvPlatformApplication.java
├── common
├── config
├── security
├── client
├── persistence
└── module
    ├── agent
    ├── analysis
    ├── cloud
    ├── dashboard
    ├── model
    ├── news
    ├── openapi
    ├── prediction
    ├── pvoutput
    ├── station
    ├── user
    └── weather
```

## 2. Agent 模块文件地图

### Controller

| 文件 | 作用 | 后续建议 |
| - | - | - |
| `module/agent/controller/AgentController.java` | 会话 API、工具 API、SSE 主入口 | 保留路径，内部委托 `AgentRuntimeService` |
| `module/agent/controller/AgentApprovalController.java` | 审批确认/拒绝 | 增加 run resume 能力或返回续跑状态 |

### DTO

| 文件 | 作用 | 后续建议 |
| - | - | - |
| `AgentChatRequest.java` | chat stream 请求体 | 增加 `runId`、`clientRequestId`，弱化业务 context |
| `AgentChatEvent.java` | SSE envelope | 升级 v2 envelope：event/runId/stepId/data |
| `AgentSessionDTO.java` | 会话列表 DTO | 增加 summary/currentGoal 可选字段 |
| `AgentMessageDTO.java` | 消息 DTO，含 toolCalls/approvals | 增加 runs/artifacts 或单独 API |
| `AgentToolDTO.java` | 工具描述 | 增加 outputSchema/riskLevel/disabledReason/examples |
| `AgentToolCallDTO.java` | 工具调用记录 | 增加 runId/stepId/artifactId |
| `AgentApprovalDTO.java` | 审批记录 | 增加 expiresAt/riskLevel/displayFields |
| `AgentApprovalDecision.java` | 审批请求体 | 可保留 |

### Entity / Mapper

| 文件 | 表 | 作用 | 后续建议 |
| - | - | - | - |
| `AgentSessionDO.java` / `AgentSessionMapper.java` | `agent_session` | 会话 | 增加 session summary 独立表，不直接膨胀 |
| `AgentMessageDO.java` / `AgentMessageMapper.java` | `agent_message` | 消息 | 保留 |
| `AgentToolCallDO.java` / `AgentToolCallMapper.java` | `agent_tool_call` | 工具调用 | 增加 runId/stepId |
| `AgentApprovalDO.java` / `AgentApprovalMapper.java` | `agent_approval` | 用户确认 | 增加 expiresAt/riskLevel |

建议新增：

```text
entity/AgentRunDO.java
entity/AgentRunStepDO.java
entity/AgentArtifactDO.java
entity/AgentUserMemoryDO.java
entity/AgentSessionSummaryDO.java
mapper/AgentRunMapper.java
mapper/AgentRunStepMapper.java
mapper/AgentArtifactMapper.java
mapper/AgentUserMemoryMapper.java
mapper/AgentSessionSummaryMapper.java
```

### Service

| 文件 | 当前职责 | 问题 | 后续建议 |
| - | - | - | - |
| `AgentOrchestratorService.java` | 会话、意图、LLM、工具循环、审批、SSE | 职责过重 | 拆为 Runtime/Planner/Executor/Synthesizer |
| `AgentSessionService.java` | 会话 CRUD 和归属校验 | 可保留 | 增加 summary 读取 |
| `AgentMessageService.java` | 消息保存和详情加载 | 可保留 | 关联 run/artifact |
| `AgentToolService.java` | 工具调用持久化、执行、脱敏 | 执行和持久化耦合 | 拆 `ToolExecutor` |
| `AgentApprovalService.java` | 审批创建、确认、加载 | 续跑不自动 | 接入 Run 状态 |
| `SlashCommandParser.java` | preferred/slash/natural 规则意图 | 规则过多且优先级需调整 | 迁入 Planner 层 |
| `AgentToolIntent.java` | 规则意图结果 | 可保留或改为 `IntentResolution` | 增加 confidence/missingFields |
| `AgentJsonService.java` | JSON 序列化辅助 | 可保留 | 无 |
| `AgentProgressService.java` | SSE send/error | 事件语义偏旧 | 改 `AgentSsePublisher` |

建议新增：

```text
runtime/AgentRuntimeService.java
runtime/AgentRunService.java
runtime/AgentStepService.java
runtime/AgentSsePublisher.java
planner/PlannerFacade.java
planner/SlashPlanner.java
planner/RuleBasedPlanner.java
planner/LlmPlanner.java
executor/ToolExecutor.java
memory/AgentMemoryService.java
skill/SkillRouter.java
evaluation/AgentAnswerEvaluator.java
```

### LLM

| 文件 | 当前职责 | 后续建议 |
| - | - | - |
| `AgentLlmClient.java` | LLM 决策接口 | 拆 Planner/Synthesizer/Evaluator 接口 |
| `DeepSeekAgentLlmClient.java` | DeepSeek chat/completions 调用 | 保留为 adapter，支持流式 synthesizer |
| `LlmPromptBuilder.java` | 单一系统 prompt | 拆为 `PlannerPromptBuilder`、`SynthesizerPromptBuilder`、`EvaluatorPromptBuilder` |
| `LlmToolCallParser.java` | JSON 决策解析 | 增加 schema 校验和 repair |
| `AgentLlmDecision.java` | LLM 决策对象 | 迁移为 Plan/Decision 分层 |
| `AgentToolCallSpec.java` | 工具调用描述 | 可复用到 PlanStep |

### Tool 基础

| 文件 | 当前职责 | 后续建议 |
| - | - | - |
| `AgentTool.java` | 工具接口 | 增加 `definition()` 和 output schema |
| `AgentToolRegistry.java` | Spring Bean 工具注册 | 加用户过滤、版本、disabledReason |
| `AbstractAgentTool.java` | 参数读取、admin 校验、guard | 迁移部分到 validation/executor |
| `ToolExecutionContext.java` | user/session/message/security user | 增加 runId/stepId/memory/timezone |
| `ToolExecutionResult.java` | success/data/summary/highlights/error | 增加 artifact/duration/references |
| `ToolCategory.java` | 工具分类 | 保留 |
| `ToolPermissionLevel.java` | READ_ONLY/WRITE/DANGEROUS/COST/ADMIN | 保留，补 risk policy |

### Tool 实现

| 文件 | Tool | 真实依赖 |
| - | - | - |
| `StationListTool.java` | `station.list` | `StationService` |
| `StationDetailTool.java` | `station.detail` | `StationService`、`StationPermissionService` |
| `StationCreateTool.java` | `station.create` | `StationService` |
| `StationUpdateTool.java` | `station.update` | `StationService` |
| `StationDisableTool.java` | `station.disable` | `StationService` |
| `StationDeleteTool.java` | `station.delete` | `StationService` |
| `WeatherCurrentTool.java` | `weather.current` | `WeatherService.current` |
| `WeatherLocationTool.java` | `weather.location` | `WeatherService.currentByLocation` |
| `PredictionListTool.java` | `prediction.list` | `PredictionService.history` |
| `PredictionDetailTool.java` | `prediction.detail` | `PredictionService.detail/results` |
| `ReportGenerateTool.java` | `report.generate` | `AnalysisService.report` |
| `ConversationReportTool.java` | `report.conversation` | `AgentMessageService.listWithDetails` |
| `ReportListTool.java` | `report.list` | `AnalysisService.history` |
| `ReportDetailTool.java` | `report.detail` | `AnalysisService.detail` |
| `ModelListTool.java` | `model.list` | `ModelService.list` |
| `ModelDetailTool.java` | `model.detail` | `ModelService.detail` |
| `ModelRunTool.java` | `model.run` | `PredictionService.create` |
| `CloudPredictTool.java` | `cloud.predict` | `CloudForecastService.predict` |
| `ApiListTool.java` | `api.list` | `ApiKeyService.listOwn` |
| `ApiUsageTool.java` | `api.usage` | `ApiCallLogService.ownLogs` |
| `ApiCreateTool.java` | `api.create` | `ApiKeyService.create` |
| `ApiResetTool.java` | `api.reset` | `ApiKeyService.resetOwn` |
| `ApiDeleteTool.java` | `api.delete` | `ApiKeyService.deleteOwn` |
| `WalletBalanceTool.java` | `wallet.balance` | `OpenAccountService.wallet` |
| `MarketplaceListTool.java` | `marketplace.list` | `OpenAccountService.plans` |
| `NewsListTool.java` | `news.list` | `NewsService` |
| `AdminUserApiListTool.java` | `admin.userApi.list` | `ApiKeyService.adminList` |
| `DisabledAgentTools.java` | disabled 工具 | 无真实执行 |

建议新增工具文件：

```text
PowerCurrentTool.java
PowerHistoryTool.java
PowerComparePredictionTool.java
PowerAnomalyTool.java
WeatherForecastTool.java
StationStatusTool.java
NotificationListTool.java
```

## 3. 业务模块文件地图

### Station

```text
module/station
├── controller/StationController.java
├── service/StationService.java
├── service/StationPermissionService.java
├── dto/StationRequest.java
├── vo/StationVO.java
└── entity/PowerStation.java
```

Agent 相关：

- `station.list/detail/create/update/delete/disable` 均依赖此模块。
- 权限必须通过 `StationPermissionService`。

### Weather

```text
module/weather
├── controller/WeatherController.java
├── controller/PublicWeatherController.java
├── service/WeatherService.java
├── client/WeatherProvider.java
├── client/QWeatherProvider.java
├── vo/CurrentWeatherVO.java
└── vo/WeatherForecastVO.java
```

Agent 相关：

- `weather.current`
- `weather.location`
- 应新增 `weather.forecast`

### Prediction

```text
module/prediction
├── controller/PredictionController.java
├── service/PredictionService.java
├── service/PredictionExecutionService.java
├── service/PredictionPersistenceService.java
├── service/PredictionInputService.java
├── dto/PredictionRequest.java
├── vo/PredictionTaskVO.java
├── vo/PredictionDetailVO.java
└── vo/PredictionResultVO.java
```

Agent 相关：

- `prediction.list`
- `prediction.detail`
- `model.run`

### Analysis

```text
module/analysis
├── controller/AnalysisController.java
├── service/AnalysisService.java
├── service/AnalysisAgentService.java
├── service/AnalysisContextService.java
├── service/ReportExportService.java
├── prompt/AnalysisPromptBuilder.java
├── llm/DeepSeekLlmClient.java
├── llm/LlmResultParser.java
├── dto/AnalysisRequest.java
└── vo/AnalysisReportVO.java
```

Agent 相关：

- `report.generate` 复用 `AnalysisService.report`。
- `report.list/detail` 复用 history/detail。
- 未来正式报告应支持从 Agent artifacts 生成。

### Model

```text
module/model
├── controller/ModelController.java
├── controller/AdminModelController.java
├── service/ModelService.java
├── service/ModelValidationService.java
├── vo/ModelListItemVO.java
├── vo/ModelDetailVO.java
└── config/ModelMarketplaceInitializer.java
```

Agent 相关：

- `model.list`
- `model.detail`
- `model.run` 实际走预测创建，不直接在 model 模块。

### OpenAPI

```text
module/openapi
├── controller/OpenApiController.java
├── service/ApiKeyService.java
├── service/ApiCallLogService.java
├── service/OpenApiService.java
├── service/OpenAccountService.java
├── service/ApiQuotaService.java
└── security/ApiKeyAuthenticationFilter.java
```

Agent 相关：

- `api.list/create/reset/delete`
- `api.usage`
- `wallet.balance`
- `marketplace.list`

### News

```text
module/news
├── controller/NewsController.java
├── controller/NotificationController.java
├── service/NewsService.java
├── service/NotificationService.java
└── vo/*
```

Agent 相关：

- `news.list`
- 通知工具尚未接入。

## 4. Persistence 文件地图

```text
persistence/entity
├── SysUserDO.java
├── SysRoleDO.java
├── PowerStationDO.java
├── UserStationPermissionDO.java
├── PvDataDO.java
├── WeatherDataDO.java
├── ModelInfoDO.java
├── PredictionTaskDO.java
├── PredictionResultDO.java
├── AnalysisReportDO.java
├── ApiKeyDO.java
├── ApiCallLogDO.java
├── OpenWalletAccountDO.java
├── NewsDO.java
└── ...

persistence/mapper
├── PowerStationMapper.java
├── PvDataMapper.java
├── WeatherDataMapper.java
├── PredictionTaskMapper.java
├── PredictionResultMapper.java
├── AnalysisReportMapper.java
├── ApiKeyMapper.java
├── ApiCallLogMapper.java
└── ...
```

Agent 重构重点：

- 新增 power 工具时优先使用 `PvDataMapper`。
- 分析报告继续使用 `AnalysisReportMapper`。
- 记忆/Run/Artifact 新表应放在 `module/agent/entity/mapper`。

## 5. SQL 文件地图

| 文件 | 作用 |
| - | - |
| `backend/src/main/resources/sql/init.sql` | 初始化全量表，包括 agent 表、业务表 |
| `backend/src/main/resources/sql/agent_tables_migration.sql` | Agent 表迁移 |
| `backend/src/main/resources/sql/api_usage_migration.sql` | API 调用统计迁移 |
| `backend/src/main/resources/sql/model_marketplace_metadata_19_models.sql` | 模型市场元数据 |
| `backend/src/main/resources/sql/backfill_external_station_coordinates.sql` | 外部电站坐标回填 |

后续新增建议：

```text
backend/src/main/resources/sql/agent_runtime_migration.sql
backend/src/main/resources/sql/agent_memory_migration.sql
backend/src/main/resources/sql/agent_artifact_migration.sql
```

## 6. 前端文件地图

```text
web-frontend/src
├── api
│   ├── agent.ts
│   ├── analysis.ts
│   ├── station.ts
│   ├── weather.ts
│   ├── prediction.ts
│   ├── model.ts
│   ├── open.ts
│   └── request.ts
├── views
│   ├── AnalysisReport.vue
│   ├── StationList.vue
│   ├── StationDetail.vue
│   ├── Weather.vue
│   ├── ModelPrediction.vue
│   ├── PredictionHistory.vue
│   ├── PredictionDetail.vue
│   ├── ApiPlatform.vue
│   └── ...
├── store
│   └── user.ts
└── router
    └── index.ts
```

### Agent 前端关键文件

| 文件 | 当前职责 | 后续建议 |
| - | - | - |
| `views/AnalysisReport.vue` | 整个 Agent 工作台 | 拆成容器页 |
| `api/agent.ts` | Agent DTO/API/SSE parser | 增加 run/artifact types |
| `api/analysis.ts` | 报告接口 | 保留 |
| `api/station.ts` | 电站接口 | 工具不直接用前端接口 |
| `api/weather.ts` | 天气接口 | 保留页面能力 |
| `api/prediction.ts` | 预测接口 | 保留页面能力 |
| `api/model.ts` | 模型接口 | 保留页面能力 |
| `api/open.ts` | 开放平台接口 | 保留页面能力 |

建议新增：

```text
web-frontend/src/components/agent
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

web-frontend/src/composables
├── useAgentSessions.ts
├── useAgentStream.ts
├── useAgentMessages.ts
└── useSlashCommand.ts
```

## 7. 测试文件地图

| 文件 | 当前作用 | 后续建议 |
| - | - | - |
| `scripts/agent-e2e-test.mjs` | Agent SSE 场景测试 | 扩展 run/step/artifact 断言 |
| `scripts/api-smoke-test.mjs` | API 冒烟 | 补 station/weather/prediction/report 依赖检查 |

建议新增：

```text
scripts/agent-regression-cases.json
scripts/agent-run-e2e-test.mjs
backend/src/test/java/.../module/agent/*
web-frontend/src/components/agent/*.spec.ts
```

## 8. 后续开发入口建议

如果开始重构，建议按这个入口读代码：

1. `AgentController.java`
2. `AgentOrchestratorService.java`
3. `SlashCommandParser.java`
4. `AgentToolRegistry.java`
5. `AgentToolService.java`
6. `LlmPromptBuilder.java`
7. `DeepSeekAgentLlmClient.java`
8. `AnalysisReport.vue`
9. `web-frontend/src/api/agent.ts`
10. `agent_tables_migration.sql`

然后再读核心工具：

1. `StationDetailTool.java`
2. `WeatherCurrentTool.java`
3. `PredictionDetailTool.java`
4. `ReportGenerateTool.java`
5. `ConversationReportTool.java`
6. `ApiUsageTool.java`

## 9. 高风险文件

| 文件 | 风险 |
| - | - |
| `AgentOrchestratorService.java` | 职责集中，任何改动可能影响 SSE、工具、审批、LLM |
| `AnalysisReport.vue` | 单文件过大，UI/状态/SSE 耦合 |
| `AgentToolService.java` | 工具执行和脱敏在一起，改错会影响敏感信息 |
| `ReportGenerateTool.java` | 写数据库，必须保持审批 |
| `DeepSeekAgentLlmClient.java` | 不得泄露 API Key，不要打印完整请求 |
| `SlashCommandParser.java` | 规则过多，容易误识别业务意图 |

## 10. 保留约束

后续开发必须保留：

- `/api/agent/chat/stream`
- `/api/agent/sessions`
- `/api/agent/tools`
- `/api/agent/approvals/{id}/approve`
- `/api/analysis/report`
- `/api/analysis/reports`
- `/api/analysis/reports/{id}`

不要破坏：

- 登录态和 JWT。
- 用户电站权限。
- API Key 脱敏。
- report.generate 的确认流程。
- 当前会话消息持久化。
