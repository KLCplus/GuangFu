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

- 顶部概览卡片：上线模型数、可试用模型数、今日调用量、平均响应时延。
- 关键词搜索：按模型名称、描述、标签过滤。
- 分类筛选：全部、时序基线、云图/视觉融合、视频时空递归。
- 状态筛选：全部、在线、维护中/离线。
- 权益筛选：全部、免费试用、按量计费、套餐。
- 模型卡片：展示名称、分类、状态、版本、简介、标签、计费说明、调用量、平均时延、试用状态。
- 模型详情抽屉：展示输入说明、输出说明、适用场景、计费方式、开放路径、调用示例、请求示例、响应示例。
- 免费试用：已接入 `POST /api/open/trials`，失败时保留 mock 兜底。
- 申请 API：优先调用真实 API Key 申请接口，失败时使用 mock 兜底。
- 复制调用示例、请求 JSON、响应 JSON、API Key。

使用的数据方法：

- `loadMarketplaceModels`
- `loadMarketplaceModelDetail`
- `getMarketplaceModelCategories`
- `requestMarketplaceTrial`
- `applyMarketplaceApi`

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
- 部分模型展示字段：价格、标签、平均时延、今日调用量等。

mock 兜底策略：

- 数据聚合层在 `web-frontend/src/api/userPages.ts`。
- mock 数据在 `web-frontend/src/data/mock.ts`。
- 页面优先请求真实接口。
- 真实接口失败时，使用 mock 数据并在页面提示“模拟数据”。
- 真实接口和 mock 都失败时，页面展示错误状态和重试入口。

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

- `web-frontend` 执行 `npm run build` 通过。
- 后端编译曾执行 `run-local.ps1 -DskipTests compile` 通过。
