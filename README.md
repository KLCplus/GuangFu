# 光伏发电综合分析与预测系统

面向 PC Web、小程序和模型服务的光伏发电平台。当前后端已经从早期 mock 骨架推进到可联调状态：Spring Boot 负责用户认证、权限、电站、光伏数据、天气、预测任务、分析报告、开放 API、新闻通知等业务；FastAPI 作为模型推理服务；前端只访问 Spring Boot。

## 当前状态

- 后端主要业务接口已经按当前代码整理到 [docs/back_front_api.md](docs/back_front_api.md)。
- 配置入口已经收敛到根目录 `.env` 和根目录 `start-local.sh`。
- 天气模块已接入 QWeather JWT 模式，并提供 `weather-debug.html` 调试页。
- Redis 已作为可选缓存/分布式状态层接入，本地开发可使用内存降级。
- PC 用户端已接入看板聚合、模型广场、API 管理、云图预测代理、新闻通知、我的页面等接口；模型服务可按需单独启动。
- 管理端用户、新闻、电站、模型、API Key 等接口已具备联调条件。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21+、Spring Boot 3、Spring Security、JWT、MyBatis Plus、MySQL、Redis、WebClient、Spring Cache |
| 模型服务 | Python、FastAPI、Pydantic、Uvicorn |
| Web | Vue 3、Vite、TypeScript、Vue Router、Pinia、Axios、Element Plus、ECharts |
| 小程序 | 微信小程序原生结构 |
| 基础设施 | MySQL 8、可选 Nginx |

## 目录

```text
pv-power-platform/
├─ backend/                  # Spring Boot 业务后端
├─ model-service/            # FastAPI 模型服务
├─ web-frontend/             # Vue Web
├─ miniapp/                  # 微信小程序
├─ docs/                     # 项目文档
└─ README.md
```

## 本地启动

### 0. Linux 一键启动

只做前后端联调时，先确认本机 MySQL 已启动且 `pv_platform` 数据库已存在，然后运行：

```bash
./start-local.sh
```

首次需要初始化数据库时再使用：

```bash
./start-local.sh --init-db --force-db-reset
```

注意：`--init-db` 会导入 `backend/src/main/resources/sql/init.sql`，该脚本包含重建表逻辑，只在确认可以重置数据时使用。

停止：

```bash
./stop-local.sh
```

### 1. 常用参数

脚本会优先停止上次由本项目脚本启动的后台进程，然后固定使用根目录 `.env` 中的端口启动。默认端口为：后端 `8080`、前端 `5173`。

```bash
# 只启动后端
./start-local.sh --backend-only

# 只启动前端
./start-local.sh --frontend-only

# 启用 Redis 缓存
./start-local.sh --with-redis
```

真实 QWeather、GitHub OAuth、阿里云人脸、OSS、邮箱发送等第三方能力只需要改根目录 `.env`。QWeather 私钥文件放到 `backend/secrets/ed25519-private.pem`。

GitHub OAuth 需要在 `.env` 同时设置前端地址 `OAUTH_CALLBACK_BASE_URL` 和 GitHub App 已登记的后端公开地址 `OAUTH_BACKEND_CALLBACK_BASE_URL`。本地开发可将后者留空，回调固定为 `http://localhost:8080/api/auth/oauth/github/callback`。

### 2. MySQL

请使用本机 MySQL 8，确认 `3306` 端口已监听，然后按需初始化数据库：

```bash
${EDITOR:-vi} .env
```

数据库名默认是 `pv_platform`。初始化脚本位于：

```text
backend/src/main/resources/sql/init.sql
```

注意：`init.sql` 包含 `DROP TABLE`，只应在确认可以清空数据时人工执行。

### 3. 模型服务

模型服务不是启动前后端的必需项。只有模型预测或云图预测需要真实推理时再启动。

Linux：

```bash
cd model-service
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 9000
```


检查：

```text
http://localhost:9000/health
http://localhost:9000/docs
```

### 4. 后端

真实本地配置统一放在根目录 `.env`。

一键启动：

```bash
./start-local.sh --backend-only
```

检查：

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/weather-debug.html
```

快速验证：

```bash
cd backend
mvn -DskipTests compile
```

### 5. Web 前端

```bash
cd web-frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

访问：

```text
http://localhost:5173
```

## 主要文档

- 文档索引：[docs/api.md](docs/api.md)
- 前后端接口交付：[docs/back_front_api.md](docs/back_front_api.md)
- 模型服务接口：[docs/module_back_api.md](docs/module_back_api.md)
- 架构说明：[docs/architecture.md](docs/architecture.md)
- 数据库说明：[docs/database.md](docs/database.md)
- DeepSeek 分析报告配置：[docs/deepseek_integration.md](docs/deepseek_integration.md)
- PC 用户端阶段进度：[docs/hanxxi-pc-user-work-summary.md](docs/hanxxi-pc-user-work-summary.md)

## 联调前置条件

1. MySQL 已启动并初始化基础数据。
2. 后端通过根目录 `./start-local.sh --backend-only` 启动。
3. `JWT_SECRET`、`MYSQL_PASSWORD` 已配置。
4. 预测和云图预测需要模型服务 `MODEL_SERVICE_BASE_URL` 可访问；普通前后端页面可不启动模型服务。
5. QWeather 需要 `WEATHER_PROVIDER=QWEATHER`、JWT 凭证、私钥路径和电站经纬度。
6. 管理端接口需要 `ROLE_ADMIN` 账号。

## 后续重点

- 使用真实用户和管理员账号跑一轮端到端验收。
- 模型服务、真实天气、DeepSeek、OAuth、人脸等第三方能力按部署环境单独配置和验证。
- 部署环境启用 Redis，并根据访问量调整 Tomcat、HikariCP 和 Redis 参数。
- 不要提交根目录 `.env`、私钥、JWT、数据库密码或第三方 Key。
