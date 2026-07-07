# 光伏发电综合分析与预测系统

面向 PC Web、小程序和模型服务的光伏发电平台。当前后端已经从早期 mock 骨架推进到可联调状态：Spring Boot 负责用户认证、权限、电站、光伏数据、天气、预测任务、分析报告、开放 API、新闻通知等业务；FastAPI 作为模型推理服务；前端只访问 Spring Boot。

## 当前状态

- 后端主要业务接口已经按当前代码整理到 [docs/back_front_api.md](docs/back_front_api.md)。
- 配置入口已经收敛到根目录 `.env.example`、本机 `.env` 和 `start-local.ps1`。
- 天气模块已接入 QWeather JWT 模式，并提供 `weather-debug.html` 调试页。
- Redis 已作为可选缓存/分布式状态层接入，本地开发可使用内存降级。
- 模型预测链路已打通 Spring Boot 到 FastAPI，但模型服务当前仍以项目内模型服务能力为准。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21+、Spring Boot 3、Spring Security、JWT、MyBatis Plus、MySQL、Redis、WebClient、Spring Cache |
| 模型服务 | Python、FastAPI、Pydantic、Uvicorn |
| Web | Vue 3、Vite、TypeScript、Vue Router、Pinia、Axios、Element Plus、ECharts |
| 小程序 | 微信小程序原生结构 |
| 基础设施 | MySQL 8、Docker Compose、可选 Nginx |

## 目录

```text
pv-power-platform/
├─ backend/                  # Spring Boot 业务后端
├─ model-service/            # FastAPI 模型服务
├─ web-frontend/             # Vue Web
├─ miniapp/                  # 微信小程序
├─ docs/                     # 项目文档
├─ docker-compose.yml
├─ .env.example              # 唯一可提交配置模板
└─ README.md
```

## 本地启动

### 0. 队友一键启动

如果只是做前后端联调，可以直接运行根目录脚本。它会生成本机 `.env`，默认启动 Docker MySQL，并使用 `LOCAL` 天气，不需要任何第三方私钥：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-local.ps1
```

真实 QWeather、GitHub OAuth、阿里云人脸、邮箱发送等第三方能力只需要改根目录 `.env`。QWeather 私钥文件放到 `backend/secrets/ed25519-private.pem`。

后端脚本仍兼容旧的 `backend/.env.local`，但新同学只需要看根目录 `.env`。

### 1. MySQL

如果只想单独启动 MySQL：

```powershell
copy .env.example .env
docker compose up -d mysql
```

数据库名默认是 `pv_platform`。初始化脚本位于：

```text
backend/src/main/resources/sql/init.sql
```

注意：`init.sql` 包含 `DROP TABLE`，只应在确认可以清空数据时人工执行。

### 2. 模型服务

```powershell
cd model-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 9000
```

检查：

```text
http://localhost:9000/health
http://localhost:9000/docs
```

### 3. 后端

真实本地配置放在根目录 `.env`，模板见根目录 `.env.example`。

一键启动：

```powershell
cd backend
powershell -ExecutionPolicy Bypass -File .\run-local.ps1
```

检查：

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/weather-debug.html
```

快速验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 --version
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -DskipTests compile
```

### 4. Web 前端

```powershell
cd web-frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

## 主要文档

- 前端接口交付：[docs/back_front_api.md](docs/back_front_api.md)
- 模型服务接口：[docs/module_back_api.md](docs/module_back_api.md)
- 架构说明：[docs/architecture.md](docs/architecture.md)
- 后端收束状态：[docs/backend_completion_status.md](docs/backend_completion_status.md)
- 数据库说明：[docs/database.md](docs/database.md)

## 联调前置条件

1. MySQL 已启动并初始化基础数据。
2. 后端通过 `backend/run-local.ps1` 或等价环境变量启动。
3. `JWT_SECRET`、`MYSQL_PASSWORD` 已配置。
4. 预测功能需要模型服务 `MODEL_SERVICE_BASE_URL` 可访问。
5. QWeather 需要 `WEATHER_PROVIDER=QWEATHER`、JWT 凭证、私钥路径和电站经纬度。
6. 管理端接口需要 `ROLE_ADMIN` 账号。

## 后续重点

- 前端按 [docs/back_front_api.md](docs/back_front_api.md) 联调并补齐页面状态。
- 部署环境启用 Redis，并根据访问量调整 Tomcat、HikariCP 和 Redis 参数。
- 不要提交本机 `.env`、私钥、JWT、数据库密码或第三方 Key。
- 真实模型、真实第三方服务和端到端验收应按部署环境重新验证。
