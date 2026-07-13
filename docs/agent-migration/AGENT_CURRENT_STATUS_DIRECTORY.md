# Agent 当前现状盘点目录

更新时间：2026-07-13  
当前 Git 分支：`main`

本文用于快速接手当前光伏平台 Agent：看目录、环境、接口、运行流程、能力进度、数据落库情况和剩余风险。结论以当前代码为准，不沿用旧口头总结。

## 1. 总体结论

当前项目已经不是单纯文本聊天，已经具备：

- `/reports` Agent 工作台：会话列表、SSE 流式消息、工具卡、确认卡、白名单 Generative UI。
- Spring Boot Agent Gateway：认证、会话、消息、工具调用、确认、Memory、内部 LLM 网关、内部工具网关。
- 两套 Runtime：
  - `legacy`：默认模式，Spring Boot 内置规则 + LLM JSON 决策 + 工具循环。
  - `migrated`：Python `agent-runtime`，参考 LangGraph/Generative UI 思路实现阶段计划、Skill、Tool Router、Memory、流式事件。
- 真实业务工具：电站、公开电站/PVOutput、天气、预测、功率、报告、模型、云图、API、新闻通知、钱包、套餐、个人资料、部分管理员能力。
- 写操作确认：报告生成、API Key 创建/重置/删除、模型运行、云图预测、个人资料修改、通知已读、市场购买、站点写操作等。
- 数据落库：会话、消息、工具调用、确认、长期记忆、用户资料修改、业务工具自身数据均走数据库或业务 Service。

当前仍需注意：

- 默认 `AGENT_RUNTIME_MODE` 仍是 `legacy`，`migrated` 需要单独启动 Python runtime 并配置 `AGENT_INTERNAL_TOKEN`。
- SSE 中的部分执行步骤是前端即时状态，不是全部独立落库；最终消息、工具调用、确认、业务结果已经落库。
- `agent-web` 独立 Next.js 子应用尚未完整迁入，当前 Generative UI 是 Vue 版白名单组件机制。
- “项目全部接口都能自动管家式调用”还没有达到 100%；当前已接入主要业务面，仍有部分 admin/复杂写操作是禁用或需要明确参数。

## 2. 代码目录索引

### 后端 Agent

|目录/文件|职责|状态|
|---|---|---|
|`backend/src/main/java/com/example/pvplatform/module/agent/controller/AgentController.java`|Agent HTTP/SSE 入口，按 `AGENT_RUNTIME_MODE` 分发 legacy/migrated|已启用|
|`AgentOrchestratorService.java`|legacy Agent 循环：Slash/NLU 规则、LLM JSON 决策、工具循环、确认|已启用，默认模式|
|`MigratedAgentRuntimeProxyService.java`|Spring 到 Python `agent-runtime` 的代理，传用户、角色、最近会话历史、确认续跑|已启用，需配置|
|`SlashCommandParser.java`|legacy 自然语言/Slash 工具路由兜底|持续增强中|
|`AgentToolRegistry.java`|Spring Bean 工具注册、查询、启用过滤|已启用|
|`AgentToolService.java`|工具调用落库、执行、耗时、结果脱敏|已启用|
|`AgentApprovalService.java`|写操作确认记录、同意/拒绝、自然语言确认绑定|已启用|
|`AgentSessionService.java`|会话创建、归档、置顶、重命名、删除、touch|已启用|
|`AgentMessageService.java`|消息和工具/确认详情查询|已启用|
|`AgentMemoryService.java`|长期记忆 MySQL 读写|已启用|
|`InternalAgentGatewayController.java`|Python runtime 内部工具、LLM、Memory 网关|已启用|

### 后端工具实现

|目录/文件|主要工具|
|---|---|
|`tool/impl/Station*Tool.java`|`station.list/detail/create/update/disable/delete`|
|`tool/impl/PvOutputTools.java`|`pvoutput.station.*`、`pvoutput.status.*`、`pvoutput.weather.*`|
|`tool/impl/PlatformReadTools.java`|dashboard、实时/历史功率、天气预报、新闻详情、通知|
|`tool/impl/Prediction*Tool.java`|预测任务列表、预测详情|
|`tool/impl/ReportGenerateTool.java`|报告生成，写操作需确认|
|`tool/impl/ConversationReportTool.java`|当前会话工作报告|
|`tool/impl/Model*Tool.java`|模型列表、模型详情、模型运行|
|`tool/impl/CloudPredictTool.java`|云图预测|
|`tool/impl/Api*Tool.java`|API Key 查询、用量、创建、重置、删除|
|`tool/impl/UserProfileTool.java`、`PlatformWriteTools.java`|个人资料查询/修改、通知已读|
|`tool/impl/WalletBalanceTool.java`、`Marketplace*Tool.java`|钱包、套餐、购买|
|`tool/impl/AdminUserApiListTool.java`、`DisabledAgentTools.java`|部分管理员工具和禁用占位工具|

### Python Runtime

|目录/文件|职责|状态|
|---|---|---|
|`agent-runtime/server.py`|Python SSE runtime 服务，`/health`、`/run/stream`|已实现|
|`pv_agent_runtime/runtime.py`|计划、阶段执行、UI 指令、LLM 总结、短期上下文、Memory 写入|已实现|
|`tool_router.py`|工具能力元数据、错误隔离、调用 Spring Tool Gateway|已实现|
|`gateway.py`|调用 Spring `/api/internal/agent/tools/{toolName}/execute`|已实现|
|`llm_gateway.py`|调用 Spring 内部 LLM Gateway|已实现|
|`memory.py`|记忆候选提取、安全过滤、Spring Memory Gateway|已实现|
|`skill_loader.py`|加载 `skills/*/SKILL.md`，选择 Skill|已实现|
|`skills/*/SKILL.md`|光伏业务技能定义|已建立|

### 前端

|目录/文件|职责|状态|
|---|---|---|
|`web-frontend/src/views/AnalysisReport.vue`|当前 `/reports` Agent 工作台|已启用|
|`web-frontend/src/api/agent.ts`|Agent API、SSE fetch、会话/工具/确认接口|已启用|
|`web-frontend/src/components/agent/PvGenerativeUi.vue`|白名单 Generative UI 动态渲染|已启用|
|`web-frontend/src/store/user.ts`|用户资料缓存；Agent 修改资料后刷新|已增强|
|`web-frontend/src/views/Profile.vue`|“我的”页面；监听 Agent 资料更新事件并重新加载|已增强|

## 3. 环境变量和启动方式

### 必需基础环境

|变量|用途|默认/说明|
|---|---|---|
|`MYSQL_URL`|MySQL 连接|默认 `jdbc:mysql://localhost:3306/pv_platform...`|
|`MYSQL_USERNAME` / `MYSQL_PASSWORD`|数据库认证|`MYSQL_PASSWORD` 无默认值|
|`JWT_SECRET`|平台 JWT|开发默认值存在，生产必须覆盖|
|`DEEPSEEK_API_KEY`|DeepSeek Key|不得提交仓库|
|`DEEPSEEK_MODEL`|LLM 模型|默认 `deepseek-v4-flash`|
|`MODEL_SERVICE_BASE_URL`|自研模型服务|默认 `http://localhost:9000`|
|`WEATHER_PROVIDER`|天气 Provider|默认 `LOCAL`，真实天气可设 `QWEATHER`|
|`PVOUTPUT_ENABLED`|公开电站 PVOutput|默认 `true`|

### Agent 专用环境

|变量|用途|默认/说明|
|---|---|---|
|`AGENT_RUNTIME_MODE`|`legacy` 或 `migrated`|默认 `legacy`|
|`agent.runtime.url` / `AGENT_RUNTIME_URL`|Spring 调 Python runtime 地址|代码默认 `http://127.0.0.1:9101`|
|`AGENT_INTERNAL_TOKEN`|Spring 内部 LLM/Tool/Memory 网关鉴权|`migrated` 必需|
|`AGENT_RUNTIME_HOST`|Python runtime host|默认 `127.0.0.1`|
|`AGENT_RUNTIME_PORT`|Python runtime port|默认 `9101`|
|`SPRING_AGENT_GATEWAY_URL`|Python runtime 调 Spring 地址|默认 `http://127.0.0.1:8080`|

### 本地启动

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd web-frontend
npm run dev
```

Python migrated runtime：

```bash
cd agent-runtime
AGENT_INTERNAL_TOKEN=本地内部token \
SPRING_AGENT_GATEWAY_URL=http://127.0.0.1:8080 \
python server.py --port 9101
```

启用 migrated：

```bash
AGENT_RUNTIME_MODE=migrated
AGENT_INTERNAL_TOKEN=同一个内部token
```

## 4. 外部/内部接口清单

### 前端可调用 Agent API

|接口|说明|
|---|---|
|`POST /api/agent/sessions`|创建会话|
|`GET /api/agent/sessions`|分页查询会话，支持归档、置顶、关键词|
|`GET /api/agent/sessions/{sessionId}/messages`|查询会话消息、工具调用、确认|
|`GET /api/agent/sessions/{sessionId}/tool-calls`|查询工具调用|
|`POST /api/agent/sessions/{sessionId}/archive` / `unarchive`|归档/取消归档|
|`POST /api/agent/sessions/{sessionId}/pin` / `unpin`|置顶/取消置顶|
|`PUT /api/agent/sessions/{sessionId}`|重命名|
|`DELETE /api/agent/sessions/{sessionId}`|删除|
|`GET /api/agent/tools`|查询工具列表|
|`POST /api/agent/approvals/{approvalId}/approve`|确认或拒绝写操作|
|`POST /api/agent/chat/stream`|SSE 对话主入口|

### Python runtime 内部接口

|接口|说明|
|---|---|
|`GET /health`|runtime 健康检查|
|`POST /run/stream`|接收任务和上下文，输出 SSE|

### Spring 内部 Agent Gateway

|接口|说明|
|---|---|
|`POST /api/internal/agent/tools/{toolName}/execute`|受控执行业务工具|
|`POST /api/internal/agent/memory/list`|读取长期记忆|
|`POST /api/internal/agent/memory/write`|写入长期记忆|
|`POST /api/internal/llm/v1/chat/completions`|OpenAI-compatible LLM 代理到 DeepSeek|

内部接口要求 `X-Agent-Internal-Token`，不应暴露给普通用户。

## 5. 当前工具能力目录

|类别|工具|写操作确认|说明|
|---|---|---:|---|
|内部电站|`station.list`、`station.detail`|否|用户可访问电站、公开 owner 为空电站、授权电站|
|内部电站写|`station.create`、`station.update`、`station.disable`、`station.delete`|是|管理/写操作，需权限|
|公开电站 PVOutput|`pvoutput.station.list`、`pvoutput.station.detail`|否|公开电站页面同源数据|
|公开电站状态/天气|`pvoutput.status.latest`、`pvoutput.status.history`、`pvoutput.weather.current`、`pvoutput.weather.forecast`|否|缺 ID、外部系统 ID、经纬度时返回业务错误|
|天气|`weather.current`、`weather.forecast`、`weather.location`、`weather.locationForecast`|否|电站天气和地点天气|
|功率|`pv.realtime`、`pv.history`|否|实时/历史功率|
|预测|`prediction.list`、`prediction.detail`|否|列表后可跟进查询详情|
|报告|`report.list`、`report.detail`、`report.conversation`|否|读取和会话报告|
|报告生成|`report.generate`|是|生成并保存分析报告|
|模型|`model.list`、`model.detail`|否|模型查询|
|模型运行|`model.run`|是|需要完整输入，消耗资源时确认|
|云图|`cloud.predict`|是|需要完整图片输入|
|API Key|`api.list`、`api.usage`|否|查询|
|API Key 写|`api.create`、`api.reset`、`api.delete`|是|敏感写操作|
|个人资料|`user.profile`|否|当前用户资料|
|个人资料写|`user.profile.update`|是|昵称、邮箱、手机号、头像、性别；已修正落库和前端刷新|
|钱包/套餐|`wallet.balance`、`marketplace.list`|否|查询|
|购买|`marketplace.purchase`|是|写操作|
|新闻通知|`news.list`、`news.detail`、`notification.list`、`notification.unreadCount`|否|查询|
|通知写|`notification.markRead`、`notification.markAllRead`|是|更新通知状态|
|管理员|`admin.userApi.list`|是|部分管理员能力|
|禁用占位|`admin.station.manage`、`admin.model.manage`|是| broad admin 工具禁用，避免危险泛化|

## 6. Runtime 流程

### legacy 流程

1. 前端调用 `POST /api/agent/chat/stream`。
2. `AgentController` 判断 `AGENT_RUNTIME_MODE != migrated`，进入 `AgentOrchestratorService`。
3. 保存用户消息到 `agent_message`。
4. `SlashCommandParser` 先做 Slash command / 自然语言规则路由。
5. 如果缺参数，直接保存 assistant 问题并 SSE 返回。
6. 如果规则明确工具，跳过 LLM 初始决策，直接构造工具调用。
7. 否则构造系统 Prompt，带最近 8 条历史给 LLM。
8. LLM 返回 JSON 决策：工具调用、追问、最终回答。
9. 工具调用先写 `agent_tool_call`，写操作进入 `agent_approval`。
10. 读工具直接执行；写工具等待确认。
11. 工具结果落 `agent_tool_call.result_json`，assistant 最终回答落 `agent_message`。

### migrated 流程

1. 前端调用同一个 `/api/agent/chat/stream`。
2. `AgentController` 判断 `AGENT_RUNTIME_MODE=migrated`，进入 `MigratedAgentRuntimeProxyService`。
3. Spring 保存用户消息，并取最近 12 条会话作为 `conversationHistory`。
4. 自然语言 `确定/确认/取消/拒绝` 会绑定当前会话最新 pending approval。
5. Spring 用 `AGENT_INTERNAL_TOKEN` 调 Python `/run/stream`。
6. Python runtime 加载 Skill、长期记忆、短期历史，生成 Plan。
7. `ToolRouter` 调 Spring 内部 Tool Gateway。
8. Spring Tool Gateway 用当前用户身份和权限执行业务工具。
9. Python 输出 `run_started`、`step_started`、`tool_result`、`ui_instruction`、`approval_required`、`run_completed`。
10. Spring 转发 SSE，并在 `run_completed` 时保存 assistant 最终消息。

## 7. 数据落库现状

|数据|落库位置|现状|
|---|---|---|
|Agent 会话|`agent_session`|已落库|
|用户/助手消息|`agent_message`|已落库|
|工具调用参数、结果、耗时、状态|`agent_tool_call`|已落库，敏感 key 脱敏|
|写操作确认|`agent_approval`|已落库|
|长期记忆|`agent_memory`|已落库，类型和敏感过滤受控|
|用户资料修改|`sys_user`|已落库，更新后重新查库返回|
|报告生成|`analysis_report` 等业务表|走业务 Service 落库|
|API Key 创建/重置/删除|API 业务表|走业务 Service 落库|
|通知已读|通知业务表|走业务 Service 落库|
|公开电站/PVOutput|`external_pv_station`、`external_pv_station_status`|读数据库；同步由 PVOutput 服务负责|
|SSE step 状态|前端运行态|不是全部独立落库；可由消息/工具调用重建大部分过程|
|Generative UI 指令|assistant 消息 metadata 或前端运行态|部分持久化依赖 final/tool result；尚未单独建表|

如果目标是“Agent 工作中所有 UI 步骤也完全可恢复”，还需要新增 `agent_event` 或 `agent_run_step` 表，将 `started/step_started/step_completed/ui_instruction` 逐条落库。

## 8. Memory 和上下文

短期上下文：

- legacy：向 LLM 附加最近 8 条会话消息。
- migrated：Spring 传最近 12 条 `conversationHistory`；Python runtime 用它理解“刚才那个电站”“继续”“确定”等追问。

长期记忆：

- 支持类型：`user_preference`、`default_station`、`report_format`、`frequent_metric`、`recent_context`、`station_focus`、`ops_preference`。
- 当前自动提取较保守：
  - “默认 2 号电站” -> `default_station`
  - “报告格式/模板...” -> `report_format`
- 敏感信息过滤：包含 `api_key`、`token`、`password`、`secret`、`private_key`、`credential` 时拒绝写入。

## 9. Generative UI 现状

当前已实现 Vue 白名单渲染，不允许模型输出任意 HTML/JS。

已接入/规划组件：

- `StationSummaryCard`
- `WeatherImpactCard`
- `PredictionTrendCard`
- `PowerMetricCard`
- `RiskAssessmentCard`
- `MaintenanceRecommendationCard`
- `ReportPreviewCard`
- `ApprovalActionCard`
- `ToolProgressCard`
- `ErrorRecoveryCard`

已验证的流式事件：

- `started`
- `thinking`
- `intent_resolved`
- `plan`
- `tool_call`
- `tool_result`
- `step_started`
- `step_completed`
- `ui_instruction`
- `approval_required`
- `token`
- `final`
- `run_completed`
- `error`

## 10. 权限和安全

- 所有用户态 Agent API 走 JWT。
- 会话、消息、确认按 `userId` 归属校验。
- 工具执行通过 `ToolExecutionContext` 绑定 `userId/sessionId/messageId/SecurityUser`。
- 电站读取：
  - 管理员可读全部。
  - owner 可读写自己的电站。
  - `ownerUserId == null` 的内部公开电站可读。
  - 授权表中存在权限的电站可读/按权限管理。
- PVOutput 公开电站走公开电站服务，不套内部电站 owner 权限。
- 写操作需确认，未确认不执行。
- DeepSeek Key 只在 Spring Boot 后端使用，不交给前端或 Python runtime。
- Python runtime 只通过内部 token 调 Spring Tool/LLM/Memory Gateway，不直连 MySQL。

## 11. 第一条垂直链路状态

目标输入：

> 分析 1 号电站当前运行情况，结合天气和最近预测结果。

当前 migrated runtime 计划：

1. 加载 `station-inspection` Skill。
2. `station.detail`
3. `weather.current`
4. `prediction.list`
5. 如果列表有记录，自动追加 `prediction.detail`
6. 数据完整性和风险由 LLM synthesis 或降级摘要生成
7. 前端显示步骤、工具卡、UI 卡片、最终结论

状态：单元测试通过；真实运行依赖当前用户对电站有权限、天气/预测数据存在、DeepSeek 或 fallback 可用。

## 12. 测试现状

近期已跑通的关键测试：

- `python -m pytest -q agent-runtime/tests/test_runtime_foundation.py`
- `mvn -q -Dtest=SlashCommandParserTest,UserServiceTest test`
- `mvn -q -Dtest=SlashCommandParserTest,StationPermissionServiceTest test`
- `mvn -q -DskipTests compile`
- `npm run build`

前端构建存在已有依赖警告：

- `@vueuse/core` pure annotation warning。
- chunk size warning。

这些警告不导致构建失败。

## 13. 进度盘点

|阶段|状态|说明|
|---|---|---|
|Phase 0 基线确认|基本完成|已读 Agent 文档和真实代码，多次按实际问题修复|
|Phase 1 参考模板运行/评估|部分完成|文档已记录参考仓库和迁移方向；不是所有参考子项目都作为生产模块保留|
|Phase 2 迁移设计|完成度较高|`docs/agent-migration/*` 已有架构、映射、测试计划|
|Phase 3 Runtime 基础|已落地|Python runtime、ToolRouter、SkillLoader、MemoryGateway、LLMGateway|
|Phase 4 Generative UI|Vue 版已落地|未完整迁入 Next `agent-web`，但有白名单组件协议|
|Phase 5 第一条垂直链路|已具备|station/weather/prediction/report approval 基本链路可跑|
|Phase 6 Memory/Skills|已具备基础|Skill 加载真实使用；Memory 自动提取保守|
|Phase 7 确认和报告|已落地|Approval 卡、确认续跑、报告生成确认|
|Phase 8 扩展工具|持续推进|模型、云图、API、新闻、钱包、公开电站、个人资料已接入|
|Phase 9 清理切换|未完成|默认仍 legacy；migrated 尚需更多 E2E 和事件落库|

## 14. 当前主要风险和待办

### 必须优先补

1. 新增 Agent 事件落库表：保存每个 step、UI instruction、run lifecycle，解决“刷新后执行轨迹完整恢复”。
2. 完整 E2E：登录真实用户后覆盖资料修改、公开电站、内部电站权限、报告确认、LLM 失败、Tool 失败。
3. migrated 作为默认前，需要持续跑真实业务数据，而不只跑单元测试。
4. 完善工具参数提取：复杂写操作仍应通过结构化表单/确认卡补参，不应只靠正则。

### 中期增强

1. `agent-web` 是否继续独立迁入，需要重新决策；当前 Vue 版已经可用，但不是参考 Next 项目完整迁移。
2. Memory 提取可以从保守规则升级为 LLM 提取，但必须保留敏感信息过滤和用户确认策略。
3. 将 Spring Agent Gateway 暴露为 MCP 或 MCP-compatible adapter。
4. Admin 工具要按具体接口逐个开放，不能启用 broad manage 工具。
5. 给公开电站工具补 UI 卡片，例如 `PvOutputStationCard`、`PvOutputStatusCard`。

## 15. 最近关键修复记录

- `fix: preserve migrated agent conversation context`：migrated runtime 带最近 12 条上下文，支持自然语言确认。
- `feat: connect agent public stations and profile updates`：接入 PVOutput 公开电站工具，Agent 修改资料后刷新用户缓存。
- `fix: persist agent profile updates and harden public station reads`：用户资料更新后重新查库返回；公开电站空值防护。
- `fix: sync agent email profile updates`：支持“把我的邮箱改为...”并通知资料页重载。

## 16. 接手建议

下一步不要继续盲目加工具。建议按这个顺序推进：

1. 先补 `agent_event`/`agent_run_step` 落库，保证 Agent 全过程可恢复。
2. 固定 10 条真实 E2E 用例，覆盖用户资料、公开电站、内部电站、天气、预测、报告确认、拒绝、无权限、LLM 失败、刷新恢复。
3. 再逐个补缺失业务接口，新增一个工具就补一个工具测试和一个前端渲染策略。
4. 最后再把 `AGENT_RUNTIME_MODE` 默认切到 `migrated`。
