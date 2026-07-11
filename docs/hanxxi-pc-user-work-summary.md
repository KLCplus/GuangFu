# hanxxi PC 用户端阶段工作总结

更新时间：2026-07-10

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
- 点击筛选按钮从左侧打开筛选抽屉；类型选项及数量由当前真实列表中的 `modelType` 动态去重统计生成。
- 支持多类型组合筛选、清空类型筛选、清空全部搜索/筛选条件。
- 搜索在前端对真实接口返回结果执行，匹配 `modelName`，并同时支持匹配 `modelCode`、`description`。
- 搜索关键字和类型筛选可同时生效，并分别处理列表为空、搜索无结果、类型筛选无结果和组合筛选无结果。
- 模型卡片整块可点击，展示真实接口返回的模型名称、模型编码、模型类型和描述；字段为空时直接隐藏。
- 删除模型版本、调用量、时延、价格、额度、试用状态、评分、排行以及卡片底部操作按钮。
- 点击卡片调用真实模型详情接口，抽屉按返回情况展示模型说明、状态、输入窗口、输入间隔、输出步数、输出步长、`inputSchema` 和 `outputSchema`。
- 详情接口失败时保留列表接口已经返回的真实信息，展示错误和重试入口，不回退到模拟详情。
- 删除详情中的试用、购买、API 申请、调用示例和其他无真实业务来源操作。
- 首次加载使用骨架卡片；列表失败、接口空列表、筛选无结果均有独立状态和重试/清空入口。

本次涉及文件：

```text
web-frontend/src/views/Marketplace.vue
web-frontend/src/api/model.ts
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
| `modelCode` | 卡片/详情辅助标识、搜索 |
| `modelType` | 卡片类型标签、动态类型筛选 |
| `status` | 详情中的平台状态 |
| `description` | 卡片简介、详情说明、搜索 |

详情接口额外使用字段：

| 字段 | 用途 |
|---|---|
| `inputWindowMinutes` | 输入窗口配置 |
| `inputFrameIntervalSeconds` | 输入间隔配置 |
| `outputSteps` | 输出步数配置 |
| `outputStepMinutes` | 输出步长配置 |
| `inputSchema` | 输入要求，JSON 字符串格式化展示 |
| `outputSchema` | 输出说明，JSON 字符串格式化展示 |

数据来源与当前限制：

- 普通用户模型列表由后端 `ModelService.list()` 查询数据库 `model_info`，只返回 `status = ONLINE` 的记录。
- 模型详情由 `ModelService.detail()` 查询同一张 `model_info` 表并转换为 `ModelDetailVO`。
- `model_marketplace_metadata_19_models.sql` 已向数据库补充 `short_description`、`tags`、`model_family`、`provider`、`paper_title`、`paper_url`、`source_url`、`capabilities`、`applicable_scenarios`、`advantages`、`limitations`、`supported_input_modes`、`reference_info` 等字段。
- 但当前后端 `ModelInfoDO`、`ModelListItemVO`、`ModelDetailVO` 和转换逻辑没有映射或返回上述新增字段，所以页面未展示模型标签、家族、来源机构、论文、能力、适用场景、优势和局限，也没有伪造对应内容或筛选项。
- `model_metric` 当前只有表、DO 和 Mapper，没有面向前端的查询接口；因此页面未展示论文参考指标或平台实测指标。
- 列表接口只返回在线模型，状态筛选没有实际区分度，因此本次未提供状态筛选。
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

- API Key 总览：Key 数量、今日调用、错误率、平均时延。
- API Key 列表：名称、前缀、状态、限流、日额度、到期时间、最后调用时间。
- API Key 申请弹窗：提交 `keyName` 和 `expireDays`。
- API Key 启用、停用、删除。
- API 调用日志：按 Key、状态筛选，支持分页。
- 开放预测接口示例：展示 curl、请求 JSON、响应 JSON，并支持复制。
- 余额、套餐展示、Key 重置已接入真实开放平台接口；购买/续费流程仍保留后续接口衔接。

使用的数据方法：

- `loadApiKeys`
- `createApiKey`
- `loadApiCallLogs`
- `loadApiUsageStats`
- `setApiKeyEnabled`
- `removeApiKey`
- `resetApiKey`

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
- 上述策略仍适用于原有 API、新闻、通知和个人中心聚合逻辑；模型广场是明确例外，现已直接调用 `model.ts` 的真实接口封装，失败时只展示错误和重试，不使用模型 mock。

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
powershell -ExecutionPolicy Bypass -File .\run-local.ps1
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

- 本次模型广场改造完成后，`web-frontend` 执行 `npm run build` 通过（Vue TypeScript 检查和 Vite 生产构建均成功）。
- 构建仅出现依赖包 pure annotation 和现有大 chunk 的警告，没有编译错误。
- 后端编译曾执行 `run-local.ps1 -DskipTests compile` 通过。
