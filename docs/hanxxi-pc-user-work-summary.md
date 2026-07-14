# hanxxi PC 用户端与微信小程序阶段工作总结

更新时间：2026-07-13

## 工作范围

本阶段聚焦 PC Web 用户端页面和本地联调链路，主要覆盖：

- 登录链路相关修复。
- 用户看板 `/dashboard`。
- U-04 模型广场 `/marketplace`。
- U-05 API 管理 `/api`。
- 云图预测 `/cloud-forecast`。
- U-07 新闻通知 `/news` 与 `/news/:newsId`。
- U-08 我的页面 `/profile`。
- 用户端页面所需的数据聚合与 mock 兜底。

本阶段边界：

- 未修改小程序 `miniapp`。
- 已补充用户端聚合与云图预测后端接口。
- 未修改模型服务 `model-service`。
- 未修改全局主题样式。

## 当前联调状态

- 本机 MySQL 已存在 `pv_platform` 数据库，启动脚本默认不做数据库初始化。
- Linux 联调使用根目录 `./start-local.sh`，需要重置数据库时才手动追加 `--init-db --force-db-reset`。
- 模型服务不是前后端启动必需项；云图预测和模型预测在模型服务不可用时按前端兜底逻辑展示。
- 前后端接口以 `docs/back_front_api.md` 为准，旧的临时审计、需求拆分和页面拆分文档已清理。

## 登录链路相关修复

前端登录页使用：

```http
POST /api/auth/login
```

经 Vite 代理转发到：

```text
http://localhost:8080/api/auth/login
```

已完成的修复点：

- 登录表单改为标准 submit 流程，避免按钮或回车触发表单原生刷新。
- 注册表单同步使用标准 submit 流程。
- Axios 响应拦截器对 `/auth/**` 请求返回 `401` 时不再强制刷新到 `/login`，让登录页能正常展示错误提示。
- 本地 Swagger 入口放行 `/swagger-ui.html`，方便联调查看接口文档。

本地注册命令：

```powershell
curl.exe -i -X POST "http://localhost:8080/api/auth/register" -H "Content-Type: application/json" --data '{"username":"test2","password":"Demo123456","email":"test2@example.com"}'
```

本地登录验证：

```powershell
curl.exe -i -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" --data '{"username":"test2","password":"Demo123456"}'
```

## 用户看板

页面路径：

```text
/dashboard
```

页面文件：

```text
web-frontend/src/views/Dashboard.vue
```

已实现能力：

- 看板聚合接口：优先调用 `GET /api/dashboard/overview`。
- 电站列表、当前天气、三日天气、实时光伏数据和服务资源状态由后端聚合返回。
- 后端单项数据失败时返回 `PARTIAL`，前端显示“部分接口”。
- 聚合接口不可用时，前端回退到原有电站、天气、实时数据分接口和 mock 兜底。

使用的数据方法：

- `getDashboardOverview`
- `getStations`
- `getCurrentWeather`
- `getForecast`
- `getRealtime`

## U-04 模型广场

页面路径：

```text
/marketplace
```

页面文件：

```text
web-frontend/src/views/Marketplace.vue
```

已实现能力：

- 页面改为面向浏览的多列模型卡片网格，宽屏三列、较窄窗口两列、移动端单列。
- 页面顶部只保留“展开筛选器/隐藏筛选器”按钮和模型搜索框，删除原概览统计卡片。
- 筛选器已从覆盖页面并带遮罩的全局 Drawer 改为模型广场内容区内部的可折叠侧栏，不覆盖系统主导航，也不影响其他页面。
- 桌面端展开筛选栏时，模型列表在右侧自动缩小；收起后列表铺满。窄屏下筛选栏退化为模型列表上方的页面内面板，不使用全屏遮罩。
- 模型类型、规范化架构体系、规范化能力标签为主筛选；来源机构、可用状态和发布年份放入默认折叠的“更多筛选”。
- 同一筛选组内支持多选，不同筛选组组合生效；支持清空单组、清空全部筛选和清空搜索/筛选条件。
- 每组筛选项显示由当前接口模型列表动态统计的模型数量；数量为 0 的选项不生成。模型类型默认全部展示，其他模块按频率排序并支持独立“更多/收起”。
- 已选搜索与筛选条件在模型列表上方汇总为可关闭标签，支持单项移除和“清空全部”；筛选按钮显示当前已选筛选条件数量。
- 搜索在前端对真实接口返回结果执行，匹配 `modelName`、`modelCode`、`description`、`shortDescription`、`modelFamily`、`provider` 和 `tags`。
- 搜索关键字和六组筛选条件可以同时生效，并分别处理列表为空、搜索无结果、筛选无结果和组合筛选无结果。
- 模型卡片整块可点击，卡片只展示 SVG 模型头像、`modelName`、`shortDescription`（为空时使用 `description`）和真实 `tags`；字段为空时直接隐藏。
- 卡片头像来自 `web-frontend/src/assets/model-icons/svg`，包含 19 个模型专用 SVG 和 3 个按模型类型使用的兜底 SVG，通过 `modelCode` 优先匹配。
- 删除模型版本、调用量、时延、价格、额度、试用状态、评分、排行以及卡片底部操作按钮。
- 点击卡片调用真实模型详情接口，抽屉按返回情况展示模型说明、状态、标签、家族、来源、论文/项目链接、能力、场景、优缺点、参考指标、输入输出配置和 Schema。
- 详情接口失败时保留列表接口已经返回的真实信息，展示错误和重试入口，不回退到模拟详情。
- 删除详情中的试用、购买、API 申请、调用示例和其他无真实业务来源操作。
- 首次加载使用骨架卡片；列表失败、接口空列表、筛选无结果均有独立状态和重试/清空入口。
- 筛选侧栏默认宽度为 320px，可通过纵向分隔线实时拖动到 260–520px，并限制不超过内容区的 45%。分隔线也支持键盘左右键微调。
- 侧栏最终宽度写入 `localStorage` 的 `marketplace-filter-sidebar-width`，下次进入页面时恢复；拖动结束和组件卸载时移除全局 Pointer 监听与拖动态样式。
- 侧栏使用 sticky 定位并保留独立纵向滚动；筛选按钮采用响应式 Grid，模型类型固定两列，其余组使用 `auto-fill + minmax`，长文本省略并由 Tooltip 展示完整值。
- 模型卡片网格改为 `repeat(auto-fill, minmax(300px, 1fr))`，可随侧栏拖动宽度自动改变列数。

本次涉及文件：

```text
web-frontend/src/views/Marketplace.vue
web-frontend/src/api/model.ts
web-frontend/src/data/modelFilterTaxonomy.ts
web-frontend/src/assets/model-icons/svg/*.svg
web-frontend/src/assets/model-icons/svg/index.ts
docs/hanxxi-pc-user-work-summary.md
```

真实接口与复用封装：

```http
GET /api/models
GET /api/models/{modelId}
```

- 列表直接复用 `web-frontend/src/api/model.ts` 中已有的 `getModels()`。
- 详情直接复用同一文件中的 `getModel(modelId)`。
- `model.ts` 新增与当前后端 `ModelListItemVO`、`ModelDetailVO` 对齐的 `ModelListItem`、`ModelDetail` 类型，未重复封装接口。
- 模型广场不再调用 `userPages.ts` 中原有的模型聚合与 mock 兜底方法。

页面实际使用的列表接口字段：

| 字段 | 用途 |
|---|---|
| `modelId` | 卡片 key、详情接口路径参数 |
| `modelName` | 卡片和详情标题、搜索 |
| `modelCode` | SVG 头像映射、详情辅助标识、搜索 |
| `modelType` | 动态模型类型筛选、SVG 兜底映射、规范化分类依据 |
| `status` | 可用状态筛选、详情中的平台状态 |
| `description`、`shortDescription` | 卡片简述、详情说明、搜索 |
| `tags` | 卡片/详情保留全部真实标签、搜索、规范化能力分类依据 |
| `modelFamily` | 搜索、规范化架构体系分类依据 |
| `provider` | “更多筛选”中的动态来源机构、搜索 |
| `releaseYear` | “更多筛选”中的动态年份 |

详情接口额外使用字段：

| 字段 | 用途 |
|---|---|
| `inputWindowMinutes` | 输入窗口配置 |
| `inputFrameIntervalSeconds` | 输入间隔配置 |
| `outputSteps` | 输出步数配置 |
| `outputStepMinutes` | 输出步长配置 |
| `inputSchema` | 输入要求，JSON 字符串格式化展示 |
| `outputSchema` | 输出说明，JSON 字符串格式化展示 |
| `paperTitle`、`paperUrl`、`sourceUrl` | 论文与项目来源 |
| `capabilities`、`applicableScenarios` | 核心能力与适用场景 |
| `advantages`、`limitations` | 优势与局限 |
| `metrics` | 论文或离线参考指标 |

数据来源与当前限制：

- 普通用户模型列表由后端 `ModelService.list()` 查询数据库 `model_info`，按 `marketplaceVisible = true` 返回可展示模型，并按推荐、`sortOrder` 和 `modelId` 排序；模型是否可调用继续由 `status` 区分。
- 模型详情由 `ModelService.detail()` 查询同一张 `model_info` 表并转换为 `ModelDetailVO`。
- `model_marketplace_metadata_19_models.sql` 已向数据库补充 `short_description`、`tags`、`model_family`、`provider`、`paper_title`、`paper_url`、`source_url`、`capabilities`、`applicable_scenarios`、`advantages`、`limitations`、`supported_input_modes`、`reference_info` 等字段。
- 后端 `ModelInfoDO`、`ModelListItemVO`、`ModelDetailVO` 和转换逻辑现已映射并返回上述模型广场字段；前端列表、筛选和详情均已接入。
- `ModelDetailVO.metrics` 已返回 `model_metric` 数据；页面明确将其标注为论文或离线参考指标，不表现成平台实时实测。
- 列表接口同时返回 ONLINE 与 OFFLINE 的可展示模型，页面提供真实状态筛选，但卡片按产品要求不显示状态底栏。
- 主筛选不再直接列举全部原始 `tags` 和 `modelFamily`。低频或仅对应单个模型的专有标签仍完整显示在卡片和详情中，并参与搜索，但不会拉长主筛选列表。
- `web-frontend/src/data/modelFilterTaxonomy.ts` 集中维护当前 19 个真实 `modelCode` 的架构体系与能力分类；映射依据 SQL 中已有的 `model_family`、`tags`、`capabilities`，不根据模型显示名称猜测，也不生成模型业务数据。
- 架构体系归一为：线性/分解、Transformer、MLP/Mixer、CNN、CNN+LSTM、ConvLSTM、时空递归网络、视频预测网络；能力归一为数值预测、多步预测、外生变量、多尺度建模、图像数值融合、天空图像、时空建模、注意力机制。
- 主筛选只保留至少匹配 2 个真实模型的规范化选项；例如当前只匹配单个模型的低频能力会自动不进入主筛选，但原始元数据不受影响。
- 接口仍返回 `modelVersion`，但按本次页面要求不展示版本信息。
- 模型广场当前未使用模型业务假数据，也没有接口失败自动回退模型 mock 的行为。
- 模型广场当前不展示试用、购买、部署、API 申请等操作；远端现已补充真实模型试用接口，但是否恢复试用入口需以后续页面需求为准，模型购买仍需等待真实业务接口。
- 本次没有修改后端 Controller、DTO、VO、实体类、Service，没有修改数据库表结构、`init.sql`、`model_marketplace_metadata_19_models.sql`，也没有修改其他成员负责的页面。

## U-05 API 管理

页面路径：

```text
/api
```

页面文件：

```text
web-frontend/src/views/ApiPlatform.vue
```

已实现能力：

- 页面重构为上下两个主要区域：上半部分管理 API Key，下半部分展示使用统计与调用记录。
- API Key 区域增加安全说明，强调完整 Key 只在创建或重新生成时展示一次。
- API Key 列表展示名称、脱敏 Key、创建时间、最近使用时间和状态；列表只使用后端返回的 `apiKeyPrefix` 生成脱敏文本，不保存或重新暴露完整 Key。
- 创建 API Key 使用真实接口，提交 `keyName`、`expireDays`；成功后在一次性弹窗显示响应中的 `apiKey`，弹窗关闭后从页面状态清除完整 Key。
- API Key 启用、停用、重新生成、删除均使用真实接口；停用、重新生成、删除前均增加二次确认。
- Key 名称编辑已接入真实后端 `PUT /api/open/keys/{apiKeyId}/name`，保存后刷新 Key 列表。
- 使用统计已改为真实后端聚合接口，不再由前端读取最近 1000 条日志后自行汇总。
- 支持时间范围、API Key、模型、成功/失败状态组合筛选；筛选条件传给后端统计和日志接口执行。
- 后端聚合并返回总调用次数、成功次数、失败次数、成功率、平均响应时间、按日期调用趋势、模型调用占比和 API Key 调用次数。
- 使用项目现有 ECharts 绘制调用趋势折线图、模型占比环形图和 API Key 调用横向柱状图，没有引入新依赖。
- 调用记录表使用后端分页，展示请求时间、Key、模型、接口、状态、HTTP 状态、耗时和错误信息。
- Token 兼容字段已完成数据库字段、DO、VO、聚合 SQL 和空数据返回；当前模型调用结果没有可靠 input/output Token 字段，开放预测日志保持 `NULL`，不估算或伪造 Token。
- 统计导出已接入 `GET /api/open/call-logs/export`，前端基于后端返回的最多 10000 条筛选结果生成 CSV。
- API Key、日志或筛选结果为空时使用正常空状态；真实接口失败时展示错误和重试，不自动回退 mock。

本次 API 管理页面直接复用的前端 API 封装：

- `getApiKeys`
- `applyApiKey`
- `updateApiKeyStatus`
- `deleteApiKey`
- `resetOpenApiKey`
- `getCallLogs`
- `updateApiKeyName`
- `getUsageSummary`
- `getUsageTrend`
- `getUsageByModel`
- `getUsageByKey`
- `exportCallLogs`
- `getModels`，用于模型筛选选项的兜底来源；日志接口已返回 `modelName`。

真实接口与页面功能对应关系：

| 接口 | 页面功能 |
|---|---|
| `GET /api/open/keys` | API Key 列表、Key 筛选选项、名称与最近使用时间 |
| `POST /api/open/apply-key` | 创建 Key，并接收一次性完整 `apiKey` |
| `PUT /api/open/keys/{apiKeyId}/status` | 启用或停用 Key |
| `DELETE /api/open/keys/{apiKeyId}` | 永久删除 Key |
| `POST /api/open/keys/{apiKeyId}/reset` | 重新生成 Key，并接收一次性完整 `apiKey` |
| `GET /api/open/call-logs` | 调用记录分页 |
| `GET /api/open/usage/summary` | 使用统计汇总 |
| `GET /api/open/usage/trend` | 按日期调用趋势 |
| `GET /api/open/usage/by-model` | 按模型统计 |
| `GET /api/open/usage/by-key` | 按 API Key 统计 |
| `GET /api/open/call-logs/export` | 调用日志导出数据 |
| `GET /api/models` | 将日志 `modelId` 映射为模型名称；未返回的模型显示为 `模型 #ID` |

API 管理页面当前没有业务 mock 数据。以下内容只是明确标记的页面占位或临时能力：

- 去充值：已接入真实后端 `POST /api/open/wallet/recharge`，当前联调环境使用 `MOCK` 渠道模拟支付成功并立即入账。
- 余额：接入真实钱包表 `open_wallet_account`，充值订单落 `open_recharge_order`，充值/消费流水落 `open_wallet_record`；本月消费由本月消费流水聚合，不再由调用日志临时映射。
- Token：兼容字段已完成数据库字段、DO、VO、聚合 SQL 和空数据返回；当前模型调用结果没有可靠的 input/output Token 字段，开放预测日志保持 `NULL`，页面显示“暂未接通”，不使用 mock 或估算值。

补充说明：当前页面展示“可用余额”“本月消费金额”“充值入口”和“最近流水”。充值入口会创建数据库充值订单和钱包流水；后续接入真实支付时复用同一订单和流水结构。Token 卡片在真实写入链路闭合前只能展示 `—` 和“暂未接通”。

### API 管理已补齐的后端内容

#### 1. 修改 API Key 名称

用途：允许用户修改 Key 的显示名称，不改变密钥本身。

```http
PUT /api/open/keys/{apiKeyId}/name
```

请求：

```json
{ "keyName": "生产环境预测服务" }
```

返回更新后的 `ApiKeyVO`。后端按当前登录用户校验 Key 所有权。

#### 2. API 使用统计汇总

用途：准确展示总调用次数、成功次数、失败次数、成功率和平均响应时间；Token 字段随聚合结果返回，但当前保持 0。

```http
GET /api/open/usage/summary
```

参数：`startTime`、`endTime`、`apiKeyId`、`modelId`，均可选。

返回：

```json
{
  "totalCalls": 0,
  "successCalls": 0,
  "failedCalls": 0,
  "successRate": 0,
  "avgCostTimeMs": 0,
  "inputTokens": 0,
  "outputTokens": 0,
  "totalTokens": 0
}
```

当前处理：前端直接展示后端聚合结果。

#### 3. Token 兼容字段

用途：为后续真实模型 Token 或计费单位接入预留日志与统计字段。

已补齐：`api_call_log`、`ApiCallLogDO`、`ApiCallLogVO`、统计 SQL 和空数据返回。

字段：`input_tokens`、`output_tokens`、`total_tokens`。

当前限制：模型调用结果没有可靠的 input/output Token 字段；`OpenApiService` 仍调用不带 Token 参数的日志保存重载，新日志字段保持 `NULL`。本次不估算、不伪造 Token。

#### 4. 按日期调用趋势

用途：绘制调用次数、成功/失败和平均时延趋势；Token 字段保留在响应中。

```http
GET /api/open/usage/trend
```

参数：`startTime`、`endTime`、`apiKeyId`、`modelId`、`granularity=DAY|HOUR`。

返回记录字段：`timeBucket`、`totalCalls`、`successCalls`、`failedCalls`、`avgCostTimeMs`、`totalTokens`。不使用普通分页，按时间桶数组返回。

#### 5. 按模型统计

用途：展示不同模型的调用次数、成功率和平均响应时间；Token 字段保留在响应中。

```http
GET /api/open/usage/by-model
```

参数：`startTime`、`endTime`、`apiKeyId`。

返回记录字段：`modelId`、`modelName`、`totalCalls`、`successCalls`、`failedCalls`、`avgCostTimeMs`、`totalTokens`。数据量较少时无需分页。

#### 6. 按 API Key 统计

用途：比较各 Key 的调用量、成功率和时延；Token 字段保留在响应中。

```http
GET /api/open/usage/by-key
```

参数：`startTime`、`endTime`、`modelId`。

返回记录字段：`apiKeyId`、`keyName`、`apiKeyPrefix`、`totalCalls`、`successCalls`、`failedCalls`、`avgCostTimeMs`、`totalTokens`。当前直接返回数组。

#### 7. 调用日志多条件查询

用途：服务端准确执行时间、Key、模型、状态筛选，并允许分页查看完整数据。

现有接口支持：`pageNum`、`pageSize`、`apiKeyId`、`status`、`startTime`、`endTime`、`modelId`。日志 VO 同时返回只读 `modelName`。

当前处理：前端使用服务端筛选和分页，不再批量读取最近 1000 条日志做客户端筛选。

#### 8. 统计或日志导出

用途：导出当前筛选范围的调用日志，避免前端自行下载不完整日志。

```http
GET /api/open/call-logs/export
```

参数：与日志筛选保持一致，包括 `startTime`、`endTime`、`apiKeyId`、`modelId`、`status`。导出接口不使用普通分页，后端最多返回 10000 条记录。

当前处理：前端调用导出接口，并在浏览器侧生成 CSV 文件。

已确认不属于后端缺失：创建 Key 时返回一次性完整 Key、Key 列表、脱敏前缀、最近使用时间、启停、删除和重新生成接口均已存在。

## 云图预测

页面路径：

```text
/cloud-forecast
```

页面文件：

```text
web-frontend/src/views/CloudForecast.vue
```

已实现能力：

- 上传或填充 10 张历史云图。
- 调用真实后端接口 `POST /api/cloud-forecast/predict`。
- 后端转发到模型服务 `POST /cloud-api/predict`，返回未来 10 张云图。
- 模型服务不可用时，前端提示错误并展示本地兜底结果。
- 支持单张下载和批量下载预测云图。

使用的数据方法：

- `predictCloudForecast`

## U-07 新闻通知

页面路径：

```text
/news
/news/:newsId
```

页面文件：

```text
web-frontend/src/views/NewsList.vue
web-frontend/src/views/NewsDetail.vue
```

已实现能力：

- 新闻列表：展示标题、摘要、类型、发布时间、目标角色。
- 新闻筛选：类型筛选，支持新闻、公告、模型更新、异常提醒、系统通知、行业资讯等类型。
- 关键词搜索：按标题、摘要、正文、类型进行前端过滤。
- 分页：使用后端分页参数 `pageNum`、`pageSize`。
- 新闻详情：展示类型、标题、摘要、发布时间、正文、封面图。
- 相关推荐：详情页展示更多新闻入口。
- 站内通知：展示通知列表、未读数量、单条标为已读、全部已读。
- loading、empty、error 状态。
- 真实接口失败但 mock 兜底成功时，页面仍可使用，并提示当前为模拟数据。
- 外部天气新闻聚合只做占位提示，不伪造第三方接口。

使用的数据方法：

- `loadNewsPage`
- `loadNewsDetail`
- `loadNotifications`
- `loadUnreadNotificationCount`
- `markNotificationAsRead`
- `markEveryNotificationRead`

## U-08 我的页面

页面路径：

```text
/profile
```

页面文件：

```text
web-frontend/src/views/Profile.vue
```

已实现能力：

- 用户基本资料展示：头像、昵称、用户名、角色、邮箱、手机号、性别、注册时间。
- 编辑资料：昵称、邮箱、手机号、性别，保存时调用真实资料修改接口。
- 安全设置：修改密码，接入真实密码修改接口。
- 邮箱绑定状态：基于用户资料中的邮箱字段展示；验证码绑定流程暂未单独实现。
- 人脸认证状态：读取真实人脸状态接口，仅展示状态，不在本页上传人脸。
- 第三方绑定：展示 OAuth 绑定列表，支持解绑已有绑定账号。
- API Key：展示当前用户 API Key 列表。
- API 权益：展示已购买模型、额度、到期时间，已接入开放平台权益接口。
- 钱包：展示余额、冻结余额、本月消费和流水，已接入开放平台钱包接口。
- loading、empty、error 状态。
- 真实接口失败但 mock 兜底成功时，页面仍可使用，并提示当前为模拟或混合数据。

使用的数据方法：

- `loadProfileOverview`
- `saveUserProfile`
- `updateUserPassword`
- `unbindOAuthProvider`

## 已接真实后端接口

接口以 `docs/back_front_api.md` 为准，前端统一通过已有 request/axios 封装访问。

模型与广场：

```http
GET /api/models
GET /api/models/{modelId}
```

用户看板：

```http
GET /api/dashboard/overview?stationId=1
```

云图预测：

```http
POST /api/cloud-forecast/predict
```

开放平台：

```http
POST   /api/open/apply-key
GET    /api/open/keys
PUT    /api/open/keys/{apiKeyId}/status
DELETE /api/open/keys/{apiKeyId}
POST   /api/open/keys/{apiKeyId}/reset
GET    /api/open/call-logs?pageNum=1&pageSize=10
POST   /api/open/trials
GET    /api/open/entitlements
GET    /api/open/wallet
GET    /api/open/plans
GET    /api/open/overview
POST   /openapi/v1/predict
```

新闻通知：

```http
GET /api/news?pageNum=1&pageSize=10&type=
GET /api/news/{newsId}
GET /api/notifications?pageNum=1&pageSize=10&readStatus=0
GET /api/notifications/unread-count
PUT /api/notifications/{notificationId}/read
PUT /api/notifications/read-all
```

用户资料与安全：

```http
GET    /api/user/profile
PUT    /api/user/profile
PUT    /api/user/password
GET    /api/user/oauth-accounts
DELETE /api/user/oauth-accounts/{oauthId}
GET    /api/user/face
```

登录注册：

```http
POST /api/auth/register
POST /api/auth/login
```

## mock 或占位能力

本次已补齐真实接口并同步前端连接的能力：

- 看板统计聚合、云图预测、模型免费试用、套餐展示、钱包余额与流水聚合、API Key 重置、API 权益与额度汇总。

仍只做 mock 或占位的能力：

- 模型购买。
- 外部天气新闻、第三方新闻源聚合。
- 邮箱验证码绑定流程。
- 人脸录入上传流程。
- 数据库已补充的模型标签、家族、来源、论文和能力字段尚未由后端模型接口返回，模型广场当前不展示也不伪造。

mock 兜底策略：

- 数据聚合层在 `web-frontend/src/api/userPages.ts`。
- mock 数据在 `web-frontend/src/data/mock.ts`。
- 页面优先请求真实接口。
- 真实接口失败时，使用 mock 数据并在页面提示“模拟数据”。
- 真实接口和 mock 都失败时，页面展示错误状态和重试入口。
- 上述策略仍适用于新闻、通知和个人中心等原有聚合逻辑；模型广场和 API 管理页是明确例外，已直接调用各自真实 API 封装，失败时只展示错误和重试，不使用业务 mock。

## 本地联调顺序

1. 启动 MySQL。

```powershell
确认本机 MySQL 3306 已启动
```

2. 首次或清库后导入数据库初始化脚本。

```powershell
mysql -uroot -p --default-character-set=utf8mb4 pv_platform < .\backend\src\main\resources\sql\init.sql
```

3. 启动后端。

```powershell
cd backend
./start-local.sh --backend-only
```

4. 启动前端。

```powershell
cd web-frontend
npm run dev
```

5. 注册并登录后访问：

```text
http://127.0.0.1:5173/marketplace
http://127.0.0.1:5173/api
http://127.0.0.1:5173/news
http://127.0.0.1:5173/news/1
http://127.0.0.1:5173/profile
```

## 已验证

- 2026-07-11 完成页面内筛选侧栏、规范化筛选、拖拽持久化和响应式网格后，`web-frontend` 执行 `npm run build` 通过（2347 个模块完成转换）。
- 2026-07-11 接入 19 个模型 SVG、六组真实字段筛选并精简模型卡片后，`web-frontend` 再次执行 `npm run build` 通过。
- 2026-07-11 对后端新增模型广场 DO/VO/Service/指标映射执行 `run-local.ps1 -DskipTests compile`，Maven `BUILD SUCCESS`。
- 本次模型广场改造完成后，`web-frontend` 执行 `npm run build` 通过（Vue TypeScript 检查和 Vite 生产构建均成功）。
- 本次 API 管理页面重构完成后再次执行 `npm run build`，Vue TypeScript 检查、ECharts 页面编译和 Vite 生产构建均成功。
- 构建仅出现依赖包 pure annotation 和现有大 chunk 的警告，没有编译错误。
- 后端编译曾执行 `mvn -DskipTests compile` 通过。

## U-05 API 管理联调完善（2026-07-11）

### 本次修改范围

- 前端页面：`web-frontend/src/views/ApiPlatform.vue`。
- 后端兼容迁移：`backend/src/main/java/com/example/pvplatform/config/ApiUsageSchemaMigration.java`。
- 后端空数据处理：`backend/src/main/java/com/example/pvplatform/module/openapi/service/ApiCallLogService.java`。
- 数据库增量脚本：`backend/src/main/resources/sql/api_usage_migration.sql`。
- 配置：`backend/src/main/resources/application.yml`，允许通过 `API_USAGE_SCHEMA_MIGRATION_ENABLED` 控制增量迁移，默认启用。

### 已接入真实后端的功能

- API Key 列表、创建、名称修改、启停、重新生成、删除继续复用 `web-frontend/src/api/open.ts`：`GET /api/open/keys`、`POST /api/open/apply-key`、`PUT /api/open/keys/{apiKeyId}/name`、`PUT /api/open/keys/{apiKeyId}/status`、`POST /api/open/keys/{apiKeyId}/reset`、`DELETE /api/open/keys/{apiKeyId}`。
- 使用统计继续使用真实聚合接口：`GET /api/open/usage/summary`、`GET /api/open/usage/trend`、`GET /api/open/usage/by-model`、`GET /api/open/usage/by-key`、`GET /api/open/call-logs`、`GET /api/open/call-logs/export`。
- 余额区域接入 `GET /api/open/wallet`，没有使用 `src/data/mock.ts` 中的模拟余额。

### 数据库与统计 500 修复

统计接口原先因本地旧版 `api_call_log` 缺少 `input_tokens`、`output_tokens`、`total_tokens` 而返回 500。新增启动迁移会查询 `information_schema`，只添加缺失列及 `idx_call_user_time`、`idx_call_user_model`、`idx_call_user_key` 三个索引；已存在时跳过，不执行 `init.sql`，不删除或重建业务表。手工迁移脚本也已改为可重复执行。

汇总接口在无日志时返回全 0 对象，趋势、按模型、按 Key 返回空数组，调用日志返回 `total=0, records=[]`，空数据不作为异常处理。

### 前端错误隔离与图表状态

- 四个统计请求由 `Promise.all` 改为 `Promise.allSettled`：单个接口失败只影响对应卡片或图表，其余真实数据继续展示。
- 趋势折线图、模型占比环形图、API Key 柱状图分别依据自己的返回数组展示，不再统一依赖调用日志总数。
- 每个图表分别展示加载失败、真实空数据或真实图表；数据出现后在 `nextTick` 中初始化并执行 `resize`，避免隐藏容器初始化导致尺寸为 0。
- 调用日志保留独立错误提示和空列表状态，不使用假日志兜底。

### 余额、充值与 Token 的真实程度

- `/api/open/wallet` 读取真实钱包账户和流水：`balance`、`frozenBalance` 来自 `open_wallet_account`，本月消费来自 `open_wallet_record` 中本月 `CONSUME` 流水聚合，最近流水来自同一张流水表。
- `/api/open/wallet/recharge` 创建 `open_recharge_order` 充值订单，当前 `MOCK` 渠道模拟支付成功，随后写入 `RECHARGE` 钱包流水并更新余额。开放 API 调用成功后写入 `CONSUME` 流水并扣减 0.01 元，余额不足时返回 402。
- Token 字段、DO、VO、统计 SQL 和空数据返回已经闭合，但当前模型调用结果没有可靠的 input/output Token 字段；`OpenApiService` 调用不带 Token 参数的日志保存重载，新字段保持 `NULL`。
- API 管理当前未使用 API Key、统计、余额、充值或 Token 业务假数据。

### 后续仍需补齐

- 若未来接入模型服务原生 Token 或其他计费单位，需要另行明确字段语义和计费规则，不能用输入帧/输出点估算替代。
- 正式支付渠道仍需要接入支付发起、查询、回调验签和幂等入账；当前数据库订单和流水结构已就绪，`MOCK` 渠道只用于本地联调。

### 验证结果

- `web-frontend` 执行 `npm run build` 成功，Vue TypeScript 检查和 Vite 生产构建通过。
- `backend` 执行 `run-local.ps1 -DskipTests compile` 成功。
- API 管理相关 `ApiCallLogServiceTest`、`ApiKeyServiceTest`、`ApiQuotaServiceTest` 共 6 个测试全部通过。
- 全量后端测试共执行 64 个：0 个断言失败、1 个错误、1 个跳过；唯一错误来自既有 `PhaseFourServiceTest` 缺少 DeepSeek API Key，与本次 API 管理修改无关。
- 本机检查时 MySQL、后端和前端均未运行，因此没有用真实登录态完成 HTTP 运行联调；应用下次连接现有 MySQL 启动时会自动执行幂等迁移。

## 微信小程序首期用户端（2026-07-13）

### 原有结构判断

`miniapp` 原本不是空目录，已有全局配置、样式、最简请求函数，以及首页、电站、预测、新闻、我的 5 个页面。除请求函数外，各页面均为占位骨架：没有底部导航、登录入口、统一响应解包、分页、字段转换或错误状态。原小程序没有业务 mock，也没有“后端不可用时自动回退 mock”的机制；本次保留了电站占位页及全部原文件，没有删除已有页面或工具。

本次只修改小程序与本工作总结，没有修改后端业务代码、数据库结构/初始化数据、PC 页面、`.env` 或本地私密配置。

### 新增和修改文件

修改：

```text
miniapp/app.js
miniapp/app.json
miniapp/app.wxss
miniapp/README.md
miniapp/utils/request.js
miniapp/pages/index/index.*
miniapp/pages/prediction/prediction.*
miniapp/pages/news/news.*
miniapp/pages/profile/profile.*
docs/hanxxi-pc-user-work-summary.md
```

新增：

```text
miniapp/utils/api.js
miniapp/utils/format.js
miniapp/pages/login/login.*
miniapp/pages/model-detail/model-detail.*
miniapp/pages/news-detail/news-detail.*
```

### 公共基础设施与四栏导航

- `utils/request.js` 继续作为唯一底层请求入口，新增查询参数编码、Spring Boot `Result` 的 `code/message/data` 解包、Bearer Token、HTTP/业务错误对象和 401 本地登录态清理。
- `utils/api.js` 只封装当前 PC API 和后端 Controller 已存在的路径，没有发明接口；`utils/format.js` 统一处理金额、数量、日期和错误文案。
- 新增账号连接页，复用 `POST /api/auth/login`；只保存后端返回的 Token、Refresh Token 和用户摘要，不保存用户名密码，不内置测试账号。
- `app.json` 使用原有 `index`、`prediction`、`news`、`profile` 页面建立“概览 / 模型 / 资讯 / 我的”四栏原生 `tabBar`。原 `station` 页面仍保留并注册为普通页面。
- 全局样式统一为浅色背景、蓝色主色、圆角卡片；识别元素使用与光伏监测相关的“调用脉冲/轨迹”，没有新增 UI 框架或依赖。

### 概览页

复用 PC `ApiPlatform.vue` 的 API Key、钱包、统计和调用日志逻辑，并按手机空间改造成纵向卡片：

- 顶部展示近 30 天总调用、成功率和平均响应时间。
- 钱包展示可用余额、冻结金额、本月消费和最近 3 条流水。
- API Key 展示名称、基于后端 `apiKeyPrefix` 生成的掩码、启用状态和最近使用时间；支持创建、启停、重命名、重置、删除。
- 创建/重置时只有后端响应确实包含 `apiKey` 才一次性弹出完整值，关闭后不写入页面持久状态或本地存储。
- 调用趋势使用轻量柱形脉冲展示最近时间桶，不引入 ECharts；最近调用展示模型、接口、状态、错误和耗时。
- 钱包、Key、统计、趋势、日志使用独立请求结果，单项失败只影响对应区域；真实空数组展示空状态。
- Token 为 0 时明确显示“暂未接通”，不估算或伪造。
- 小程序充值只展示状态说明，不调用充值接口。后端当前 `MOCK` 渠道会模拟支付成功，不适合作为小程序真实充值流程。

真实接口：

```http
GET    /api/open/keys
POST   /api/open/apply-key
PUT    /api/open/keys/{apiKeyId}/status
PUT    /api/open/keys/{apiKeyId}/name
POST   /api/open/keys/{apiKeyId}/reset
DELETE /api/open/keys/{apiKeyId}
GET    /api/open/wallet
GET    /api/open/usage/summary
GET    /api/open/usage/trend
GET    /api/open/call-logs
```

### 模型页

复用 PC 模型广场的真实字段与状态判断，移动端采用顶部搜索、横向类型标签、状态筛选浮层和单列卡片：

- 搜索使用后端实际返回的名称、Code、说明、家族、机构和标签；模型类型选项由当前真实列表动态生成。
- 状态只使用 `ONLINE`、`TESTING`、`OFFLINE` 等真实值，不创建额外筛选字段。
- 详情页展示说明、标签、输入输出配置、能力、场景、优缺点等后端实际返回内容；缺失字段直接不展示。
- 购买与试用仅显示“等待真实业务接口”的说明，不发起购买，也不模拟成功。

真实接口：

```http
GET /api/models
GET /api/models/{modelId}
```

### 资讯页

复用 PC 新闻通知页，以移动端顶部切换合并“平台资讯”和“站内通知”：

- 新闻支持真实类型筛选、当前已加载记录的关键词搜索、后端分页、上拉加载更多、下拉刷新与详情页。
- 新闻与通知使用各自真实接口，没有合并成虚构接口。
- 通知支持全部/未读切换、未读计数、单条标为已读和全部已读。
- 新闻和通知分别处理加载、真实空数据、失败和刷新；接口失败不会显示成“暂无数据”。
- 新闻详情按 `content` 换行拆段，封面仅在 `coverUrl` 存在时展示。

真实接口：

```http
GET /api/news
GET /api/news/{newsId}
GET /api/notifications
GET /api/notifications/unread-count
PUT /api/notifications/{notificationId}/read
PUT /api/notifications/read-all
```

### 我的页

按本期边界只提供平台账号连接状态、角色、当前服务地址、退出登录和“功能完善中”说明。没有复用旧 PC 页扩展个人资料、密码、人脸、OAuth 或邮箱绑定功能。

### 真实接口、mock 与占位分类

- 真实接口：上述登录、开放平台、模型、新闻和通知接口，路径与字段已同时核对 PC 封装、`docs/back_front_api.md`、Controller 和 VO。
- mock：小程序没有新增业务 mock，也没有复制 PC `userPages.ts` 的新闻/通知 mock。后端返回空数组时保持真实空状态。
- 占位：小程序充值入口只说明当前支付状态；模型购买/试用只说明等待接口；“我的”完整功能暂缓；原电站页维持历史占位。
- 后端暂不完整支持：正式微信/支付宝支付、模型购买、可靠 Token 用量写入。后端本地 `MOCK` 充值渠道不是正式支付。

### 本地运行与测试步骤

1. 启动 MySQL 和 Spring Boot 后端，确认 `http://127.0.0.1:8080` 可访问。
2. 使用微信开发者工具导入项目根目录的 `miniapp` 目录。
3. 本地调试在“详情 → 本地设置”勾选“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”。
4. 编译后进入“我的”，用现有平台账号连接；不要写入测试账号或 Token。
5. 依次验证概览下拉刷新、Key 操作、模型搜索/筛选/详情、资讯分类/加载更多/详情、通知已读操作。
6. 验证接口返回空数组时的空状态；停止后端后验证错误与重试状态；清除 Storage 后验证四个页面的未登录状态。
7. 真机或体验版使用已备案 HTTPS 后端域名，并在微信公众平台配置 request 合法域名。调试环境可通过 `wx.setStorageSync('apiBaseUrl', 'https://your-domain.example')` 覆盖默认地址后重启，不写死个人电脑路径。

静态检查：

```powershell
node --check miniapp 下所有 JavaScript 文件
ConvertFrom-Json 检查 miniapp 下所有 JSON 文件
```

JavaScript 与 JSON 语法检查均通过。微信开发者工具需要开发者本地完成最终编译、网络域名和真机视觉验证。

### 已知问题与后续协作

- 当前安全配置要求 `/api/**` 认证，因此模型和新闻页也必须先连接平台账号；若产品希望公开浏览，需要后端与安全策略共同确认，不能由小程序绕过。
- 小程序没有微信 `code` 换取平台身份的后端接口，目前沿用用户名密码登录。后续若接入微信身份，应新增后端授权绑定方案并处理隐私合规。
- 正式支付需要后端提供微信支付下单、回调验签、订单查询和幂等入账；在此之前小程序不发起充值。
- Token 统计字段存在，但模型调用链尚无可靠 Token 数据；页面保持“暂未接通”。
- 当前未提供微信开发者工具 CLI/项目配置文件，命令行无法替代开发者工具完成 WXML/WXSS 编译和真机预览。

## 微信小程序“我的 / 账户设置”完善（2026-07-13）

### 本次范围

以 PC `web-frontend/src/views/Profile.vue`、`web-frontend/src/api/user.ts`、`web-frontend/src/api/open.ts` 及现有后端 Controller/VO 为接口依据，将原小程序“我的”占位页改为移动端账户中心。本次没有修改后端、PC 页面、数据库和本地环境配置，没有复制 PC `userPages.ts` 的账户 mock 兜底。

修改文件：

```text
miniapp/app.json
miniapp/README.md
miniapp/utils/request.js
miniapp/utils/api.js
miniapp/utils/format.js
miniapp/pages/profile/profile.js
miniapp/pages/profile/profile.wxml
miniapp/pages/profile/profile.wxss
docs/hanxxi-pc-user-work-summary.md
```

新增页面：

```text
miniapp/pages/account-profile/account-profile.*
miniapp/pages/account-security/account-security.*
miniapp/pages/account-wallet/account-wallet.*
miniapp/pages/account-entitlements/account-entitlements.*
```

### 移动端结构

- “我的”首页使用用户信息卡、余额/API Key/API 权益三项核心数据、四个功能入口和退出登录，不复制 PC 顶部长条、左侧五栏导航或大型表格。
- 用户名、邮箱、昵称和权益名称均使用移动端溢出处理；页面底部加入安全区域留白。
- 未登录时只显示登录说明与入口，不请求用户、钱包、Key 或权益接口；登录后首次进入、间隔超过 20 秒、下拉刷新或资料更新返回时重新同步。
- 首页四项请求使用 `Promise.allSettled` 隔离失败。余额、Key 或权益单项失败时显示 `--` 和“加载失败”，不显示虚构的 0。

### 已接真实接口并可操作

```http
GET    /api/user/profile
PUT    /api/user/profile
POST   /api/user/avatar
DELETE /api/user/avatar
PUT    /api/user/password
```

- 个人资料展示头像、用户名、昵称、邮箱、手机号和性别。
- 用户名只读；昵称、邮箱、手机号、性别通过现有资料更新接口保存。邮箱和手机号只是资料字段更新，后端没有验证码绑定流程，小程序没有伪造验证码页面。
- 头像使用真实 multipart 上传和删除接口。上传成功后重新读取资料；不使用临时本地图片假装保存成功。头像上传依赖当前后端 OSS/文件存储配置。
- 修改密码校验原密码、新密码、确认密码和 8–64 位长度。后端修改成功会递增 Token 版本，小程序立即清除 Token、Refresh Token、用户缓存并引导重新登录。

### 已接真实接口但仅展示

```http
GET /api/open/wallet
GET /api/open/keys
GET /api/open/entitlements
```

- 钱包展示真实可用余额、冻结余额、本月消费和接口返回的最近资金流水；没有独立钱包流水分页接口，因此不制作虚假的“查看全部”。
- API 权益页展示 Key 总数、启用数量、权益数量、Key 名称、脱敏前缀、状态和有效期；不会显示完整密钥。
- 权益以纵向卡片展示模型名称、关联 Key、已使用/总额度、进度和有效期。总额度为空、为 0 或非法时进度按 0 处理并显示“未设上限”，不会产生 `NaN`、`Infinity` 或进度溢出。

### 后端暂不支持或不适合小程序，因此未实现

- 邮箱验证码绑定、手机短信绑定：没有独立真实绑定接口，安全页仅按资料字段展示状态，不提供“立即绑定”按钮。
- 正式充值：`POST /api/open/wallet/recharge` 当前仅使用 `MOCK` 渠道模拟支付成功和入账，不是微信支付；小程序不调用该接口，只明确显示“充值暂未开放”。
- 第三方账号：按需求移除，不制作空白入口或页面。
- 人脸录入与认证：按需求移除，不调用相册或摄像头包装成人脸功能。
- API Key 创建和复杂管理：概览页已有真实管理能力，账户权益页只提供只读概要，避免重复实现。

### 数据、错误与登录态

- 所有账户数据都来自现有后端接口，没有复制 `test2`、固定余额、固定 Key、固定权益或 PC mock。
- `utils/request.js` 新增复用统一响应解包和 401 清理的 multipart 上传方法，并集中提供登录态清理函数；页面不记录或输出密码。
- 资料修改、头像更新后同步 `userInfo` 本地用户摘要，并设置刷新标记，使“我的”首页返回时刷新。
- 钱包无流水、权益为空、Key 为空、接口失败、未登录和功能暂未开放均使用不同状态文案。

### 微信开发者工具验证

1. 启动 MySQL 和 Spring Boot，使用微信开发者工具导入 `miniapp`。
2. 在“详情 → 本地设置”勾选“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”。
3. 清除 Storage 后进入“我的”，确认只显示登录入口且没有账户接口连发。
4. 登录后核对资料、余额、API Key 数量和权益数量与 PC 端及数据库真实账号一致。
5. 验证资料修改和头像上传/删除；头像依赖 OSS 配置，失败时应显示真实后端错误，不在前端保留临时成功状态。
6. 验证修改密码后自动退出，再使用新密码登录。
7. 验证钱包空流水、Key/权益空列表、断开后端、长邮箱/昵称及小屏幕布局。
8. 确认 Key 始终脱敏，充值只显示未开放说明，页面不存在第三方账号和人脸入口。

本次仍没有账户业务 mock 或固定占位数据。功能性占位只有正式充值“暂未开放”，其原因是现有后端只有本地 `MOCK` 模拟支付，没有真实微信支付链路。

## 2026-07-13 新闻通知模块两阶段完善

### 内容边界与分类

- “新闻与公告”使用 `news` 表，面向匿名用户公开的分类统一为：气象预警、灾害动态、政策标准、行业动态、企业资讯、平台资讯。
- “站内通知”使用 `user_notification` 表，仅当前登录用户可读，包含公告通知、模型更新、异常提醒和系统通知，并保留单条已读、全部已读和未读统计。
- 一篇平台公开公告仍是公开内容；发布时生成的用户提醒是另一条站内通知，二者不再混用。
- 旧 `NEWS`、`NOTICE`、`INDUSTRY_NEWS` 通过查询兼容和增量迁移保留；模型更新、异常提醒、系统通知不再出现在公开新闻列表。

### 后端和数据库

主要修改：

- `NewsDO`、`NewsVO`、`NewsRequest`：增加分类、来源、原文、外部 ID、原始发布时间、抓取时间和预警扩展字段。
- `NewsService`、`SecurityConfig`：同时放开匿名公开列表/详情的安全规则和业务逻辑；草稿、下线、非 `ALL` 目标仍不可匿名读取。
- `NotificationService`、`NotificationController`：增加通知类型筛选，通知接口仍要求登录且只操作当前用户。
- `ExternalNewsSyncService`、`NewsSyncScheduler`、`NewsSyncProperties`：增加隔离的网页来源采集、去重、统计、超时和可配置调度。
- `QWeatherProvider`、`WeatherWarningSyncService`：复用现有 QWeather JWT 认证，按启用电站坐标同步预警。
- `OssNewsSchemaMigration` 和新增 `news_source_migration.sql`：为旧库增量补齐字段、索引与旧类型映射，没有改写既有 SQL。

定时任务默认关闭：`NEWS_SYNC_ENABLED=false`；普通网页默认每 6 小时、天气预警默认每 20 分钟，可分别开关来源。后端启动不依赖外部网站成功，单一来源失败不会中断其他来源。管理员可用 `/api/admin/news/sync?source=...` 手动验证，匿名和普通用户不可触发。

### 外部来源真实验证结果

- 隆基绿能：列表与详情可由当前 Java 运行环境稳定解析。首次同步解析并写入 9 条企业资讯；第二次同步识别 9 条重复、写入 0 条，数据库记录未重复增加。已正式接入。
- 应急管理部：浏览器环境可提取至少 20 条列表项，并抽查 2 个详情页；当前 Java 运行环境请求时报 `PKIX path building failed`，代码保留但当前环境不能写成可用来源。
- 国家能源局：浏览器环境可提取列表及至少 2 个相关详情页；当前 Java 运行环境同样因证书链校验失败，代码保留但当前环境未完成正式入库验证。
- 阳光电源：当前地址只返回依赖动态渲染的页面外壳，无法稳定获得标题、日期和详情链接，未写入采集器。
- QWeather：已经接入现有配置、客户端和认证方式；本次手动同步返回 0 条生效预警，因此只属于“代码已接、依赖有效配置和实际预警”的来源，不能声称已采到预警。

外部内容只保存标题、摘要、清洗后的正文片段、来源、日期和原文链接；清理脚本、样式、iframe、事件属性及导航页脚，不复制完整企业文章。没有添加静态假新闻，也没有用平台原创冒充外部来源。本次未额外插入平台原创演示文章，既有管理员原创和发布能力继续保留。

### PC 端

- 保留现有顶部信息、双页签、按日期分组列表和右侧导航结构，没有恢复旧统计卡片页面。
- 新闻侧栏显示六类公开分类；通知侧栏按全部、未读和通知类型切换，不再把两套菜单混在一起。
- 未登录只请求公开新闻；登录后才请求未读数和通知。“全部已读”只在通知页签且有未读时可用。
- 新闻支持服务端搜索、分类、分页、刷新、来源和预警信息；详情展示清洗内容、安全原文链接和来源归因。
- 接口失败显示失败与重试，不回退到 `mockNews`。管理端仍保留草稿、编辑、发布、下线、删除和发布通知能力。

相关 PC 文件：`src/api/news.ts`、`src/api/notification.ts`、`src/views/NewsList.vue`、`src/views/NewsDetail.vue`、`src/views/admin/NewsManage.vue`。

### 微信小程序

- 第二阶段在 PC 接口和分类验证后修改 `pages/news` 与 `pages/news-detail`，复用 `/api/news` 和 `/api/notifications`，没有在小程序实现采集、外部 API 认证或网页解析。
- 新闻资讯允许匿名浏览，支持搜索、横向分类、下拉刷新、触底分页、真实来源/预警信息及详情；分类与 PC 完全一致。
- 站内通知未登录时不请求接口，只显示登录入口；登录后支持全部/未读、类型筛选、单条已读、全部已读和关联新闻跳转。
- 详情页只显示后端清洗后的文本，不直接渲染外部 HTML；原文链接使用复制方式交给用户，不伪造小程序 `web-view` 业务域名能力。
- 加载中、空数据、失败、未登录分别显示；没有新闻或通知 mock，不影响原有四栏 Tab。

### 验证与当前限制

- 后端 Maven 编译通过；`PhaseFourServiceTest` 和 `NewsSecurityTest` 共 4 个测试通过。标准安全配置下匿名新闻返回成功，匿名通知和管理接口返回 401。
- 后端连接本地 MySQL 启动成功，增量字段迁移成功；匿名列表、详情、关键词和分类查询已实测。
- PC 已通过 `vue-tsc` 和 Vite 生产构建。
- 小程序全部 JavaScript 通过 `node --check`，全部 JSON 可解析；仍需在微信开发者工具中执行最终编译和手机尺寸视觉检查，不能将静态检查写成开发者工具已验证。
- 当前遗留：MEM/NEA 需要修复目标 JVM 信任链后再进行入库验收；QWeather 需要有效配置且电站附近确有生效预警；阳光电源需要稳定的非动态数据入口或浏览器渲染方案。

微信开发者工具验证：本地启动后端并关闭合法域名校验；清除 Storage 后确认匿名新闻可读、通知不发请求；登录后验证未读和已读操作；分别检查搜索、六类筛选、下拉刷新、触底加载、详情、断网失败状态、长标题/来源以及小屏幕布局。

## 2026-07-14 新闻来源与分类数据补充

### PKIX 原因与处理

- 系统命令行默认 `java` 是 Java 8u331，但 Maven 和 Spring Boot 实际由 `JAVA_HOME=D:\JDK-21` 的 Oracle JDK 21.0.5 运行，因此以 JDK 21 的信任库为准排查。
- 应急管理部和国家能源局均返回完整的“站点证书 → CFCA OCA → CFCA EV ROOT”证书链；JDK 21 默认 `cacerts` 没有 CFCA 根证书，导致 `PKIX path building failed`。HTTP/HTTPS 环境变量虽已设置代理，但 Java 进程未使用代理系统属性，隆基和政府来源均为直接连接，代理不是本次原因。
- 项目增加 `certificates/cfca-ev-root.pem` 和 `NewsTlsSupport`。根证书 SHA-256 为 `5CC3D78E4E1D5E45547A04E6873E64F90CF9536D1CCC2EF800F355C4C5FD70FD`，与 CFCA 公开资料一致；启动时再次校验指纹。
- 扩展只用于 `www.mem.gov.cn` 和 `www.nea.gov.cn`，并与 JDK 默认信任管理器组合。没有修改全局 truststore，没有信任所有证书，也没有关闭主机名、有效期或证书链校验。

### 实际同步结果

- 应急管理部：修复 TLS 后又发现列表链接为相对 `.shtml` 地址，原选择器无法匹配；调整后首次解析并写入 10 条灾害动态，已有记录补齐官网真实日期与时分后，再次同步全部识别为重复，新增 0、失败 0。
- 国家能源局：首次解析 10 条候选内容，写入 8 条；根据标题归为 4 条政策标准和 4 条行业动态。第二次同步没有新增，随后按列表真实日期更新 8 条早期记录；再次同步可按外部 URL 去重。
- 隆基绿能：原有 9 条企业资讯保持不变，未受政府来源修复影响。
- 阳光电源：仍只有动态页面外壳，没有引入 Selenium 或浏览器自动化，继续不接入。

### QWeather 核查

- 当前本地配置确实为 QWeather + JWT，项目 ID、Key ID 和私钥路径均已配置，私钥文件存在；8 个启用电站都有合法经纬度。
- 使用同一配置调用实时天气成功返回真实 QWeather 数据，说明域名、JWT 和基础天气权限正常。
- `/v7/warning/now` 对 8 个坐标均返回 HTTP 403 `No permission to request this data`，因此本次不是“附近没有预警”，而是当前账号没有天气预警接口权限。同步结果明确记录 `failed=8`，没有写入假预警。
- 代码仍保留官方预警 ID 去重、更新、取消和过期处理；需要在 QWeather 开通预警接口权限后，才能继续验证真实空数组或真实预警入库。

### 平台资讯与 PC 分类

- 新增幂等的 `PlatformNewsSeedMigration`，首次启动写入 4 条平台原创内容，第二次启动识别已有 4 条并写入 0 条。可通过 `NEWS_PLATFORM_SEED_ENABLED=false` 关闭。
- 内容包括暴雨后巡检、高温效率与检查、强对流安全检查、地质灾害风险地区运维。来源统一为“光伏智云平台”，分类统一为“平台资讯”，不包含外部来源链接，也不冒充政府或企业新闻。
- 当前公开分类实际数量：气象预警 0、灾害动态 10、政策标准 4、行业动态 4、企业资讯 9、平台资讯 4，总计 31。
- PC 端保留原六类和现有双栏结构，新增 `/api/news/category-counts` 真实数量；空分类显示 `0`，列表仍使用正常空状态和清除筛选入口，没有合并后端分类或加入 mock。
- 本轮没有修改 `miniapp`；待 QWeather 权限或分类再次稳定后再决定是否同步小程序展示。

## 2026-07-14 资讯与消息术语、富文本及附件完善

### 内容边界与术语

- PC 与小程序顶部统一为“资讯中心 / 消息中心”，统计统一为“资讯 / 未读消息”。“全部已读”只在消息中心显示，且没有未读消息时禁用或隐藏。
- 后端分类值 `PLATFORM` 保持不变，避免迁移历史数据和破坏接口兼容；PC、管理端和小程序对外统一显示为“运维指南”。既有 4 篇平台原创文章仍来自“光伏智云平台”，没有重复生成 SQL 或新增假内容。
- 消息中心继续只展示公告提醒、模型更新、异常提醒和系统通知。公开公告正文保留在资讯中心，消息通过 `relatedType=NEWS`、`relatedId` 跳转完整文章。

### 正文格式与附件

- 原正文丢失排版的根因是 `ExternalNewsSyncService` 在采集层调用 `Element.text()`，段落、列表和表格在写入数据库前已经被压平，不是单纯的前端 CSS 问题。
- 采集改为保存经过 Jsoup 白名单清洗的 HTML，只保留 `p/br/h1-h4/ul/ol/li/strong/em/blockquote/table/thead/tbody/tr/th/td/a`；链接只允许 HTTP/HTTPS，并移除脚本、样式、iframe、表单、事件属性、导航、广告、推荐和页脚。
- 新增幂等增量脚本 `news_attachment_migration.sql`，为新闻增加主要附件名称、类型和 URL。PDF、Word、Excel 链接从正文中分离，不做 OCR、不猜测附件表格行列；原网页自身存在的 HTML 表格仍保留并横向滚动展示。
- 重同步继续按来源类型和外部 URL 哈希更新已有记录，不改变发布状态或原发布时间。PC 详情用安全富文本展示；小程序使用 `rich-text` 展示后端已清洗内容，复杂表格容器允许横向滚动，并分别提供“复制原文链接”和“复制附件链接”。

### 实际同步与数据检查

- NEA：首次重同步原位更新 8 条，第二次为重复 8、写入 0、失败 0；其中 4 条公告识别到独立 DOCX 附件，4 条网页本身存在原生 HTML 表格。
- 当前稳定采集批次没有 PDF 附件，实际验证的是 DOCX；代码按同一安全附件流程支持 PDF，但未把“未抓到 PDF”写成已验证成功，也没有从附件提取或拼接扁平正文。
- MEM：本次来源列表出现一篇新的公开动态，因此首次新增 1、更新 9；第二次为重复 10、写入 0、失败 0。当前灾害动态 11 条，这是新来源内容，不是重复插入。
- 隆基：首次更新 7 条正文格式，第二次为重复 7、写入 0、失败 0；既有 9 条企业资讯数量不变。
- 当前公开数据共 32 条：气象预警 0、灾害动态 11、政策标准 4、行业动态 4、企业资讯 9、运维指南 4。18 条外部正文保留段落结构、4 条含原生 HTML 表格、4 条带独立 DOCX 附件。本批来源正文没有可保留的 `h1-h4` 或 `ul/ol`，没有人为构造标题或列表。

### QWeather 与模拟数据

- 管理员手动同步已实测 8 个电站坐标：全部返回 HTTP 403 `No permission to request this data`。同步接口现在返回 `PERMISSION_DENIED`，并分别统计权限不足、配置缺失、网络失败、正常空预警和成功同步；日志保留原始 403 详情。
- 正式模式仍未写入任何气象预警。此次未实现可选模拟演练预警，页面没有把普通 SQL 或静态数据冒充和风天气实时预警。

### PC 与小程序文件

- 后端：`NewsDO`、`NewsVO`、`NewsService`、`ExternalNewsSyncService`、`WeatherWarningSyncService`、`news_attachment_migration.sql`、`test-schema.sql`。
- PC：`src/api/news.ts`、`src/views/NewsList.vue`、`src/views/NewsDetail.vue`、`src/views/admin/NewsManage.vue`。管理端可继续创建 `PLATFORM` 分类，界面名称显示“运维指南”。
- 小程序：`pages/news/news.js|wxml`、`pages/news-detail/news-detail.js|wxml|wxss`。没有在小程序中加入采集、QWeather 认证、网页解析或 mock。

### 验证结果

- 后端 Maven 编译通过；`NewsSecurityTest` 2/2、`PhaseFourServiceTest` 2/2 通过。
- PC `vue-tsc` 与 Vite 正式构建通过。公开分类和详情接口在本地 8080 实测正常，附件字段可返回。
- 小程序新闻相关 JavaScript 通过 `node --check`，`app.json` 可解析；尚未在本次命令行环境中实际启动微信开发者工具，因此不能声称开发者工具编译和真机富文本视觉已经验证。
- 微信开发者工具验证重点：资讯/消息术语、匿名资讯、登录消息、运维指南、含表格的 NEA 详情、DOCX 附件复制、长链接换行、小屏幕横向滚动、QWeather 空状态及断网失败状态。
