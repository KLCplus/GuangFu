# PC 用户端登录与模型广场开发说明

## 本次提交范围

本次提交聚焦 PC Web 用户端的登录链路修复、模型广场页面和模型广场所需的数据能力。

不包含小程序改动，不包含模型服务改动，也不包含新的业务后端能力开发。

## 登录链路修复

前端登录页使用 `POST /api/auth/login`，经 Vite 代理转发到后端 `http://localhost:8080/api/auth/login`。

本次修复点：

- 登录表单改为标准 submit 流程，避免按钮或回车触发表单原生刷新。
- 注册表单同步使用标准 submit 流程。
- Axios 响应拦截器对 `/auth/**` 请求返回 `401` 时不再强制刷新到 `/login`，让登录页能正常展示错误提示。
- 后端放行 `/swagger-ui.html`，方便本地联调查看接口文档入口。

本地可用账号可通过注册接口创建：

```powershell
curl.exe -i -X POST "http://localhost:8080/api/auth/register" -H "Content-Type: application/json" --data '{"username":"test2","password":"Demo123456","email":"test2@example.com"}'
```

登录验证：

```powershell
curl.exe -i -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" --data '{"username":"test2","password":"Demo123456"}'
```

## 模型广场页面

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
- 免费试用：调用数据层试用方法，当前为 mock/占位能力。
- 申请 API：优先调用真实 API Key 申请接口，失败时使用 mock 兜底。
- 复制调用示例、请求 JSON、响应 JSON、API Key。

## 数据层对齐

模型广场优先复用 `web-frontend/src/api/userPages.ts` 中的数据聚合方法：

- `loadMarketplaceModels`
- `loadMarketplaceModelDetail`
- `getMarketplaceModelCategories`
- `requestMarketplaceTrial`
- `applyMarketplaceApi`

真实接口对齐 `docs/back_front_api.md`：

- `GET /api/models`
- `GET /api/models/{modelId}`
- `POST /api/open/apply-key`
- `GET /api/open/keys`
- `GET /api/open/call-logs`
- `PUT /api/open/keys/{apiKeyId}/status`
- `DELETE /api/open/keys/{apiKeyId}`
- `POST /openapi/v1/predict`

仅 mock/占位的能力：

- 免费试用。
- 模型购买。
- 套餐权益。
- 钱包余额与流水。
- API Key 重置。
- 模型价格、标签、平均时延、今日调用量等展示字段。

## 本地联调顺序

1. 启动 MySQL。

```powershell
docker compose up -d mysql
```

2. 首次或清库后导入数据库初始化脚本。

```powershell
docker compose cp .\backend\src\main\resources\sql\init.sql mysql:/tmp/init.sql; docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 pv_platform < /tmp/init.sql'
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
```

## 已验证

- 前端 `npm run build` 通过。
- 后端 `run-local.ps1 -DskipTests compile` 通过。

