# 光伏发电综合分析与预测系统

面向 PC Web、小程序和模型预测服务的 monorepo。Spring Boot 负责用户、权限、电站、光伏数据、天气、预测任务、报告、开放 API 和新闻；FastAPI 独立负责模型推理；Vue Web 和小程序只访问 Spring Boot。

> 当前为项目骨架版本：主要接口返回 mock 数据，但 Web → Spring Boot → FastAPI 的预测链路已按真实接口边界实现。未包含服务器 CPU/GPU/内存监控模块。

## 功能模块

- 登录注册与用户/管理员/API 用户权限预留
- 光伏电站管理、实时数据和历史数据
- 天气 API 接入位置
- 多模型列表、预测任务、预测结果和历史记录
- 综合分析报告
- API Key、开放预测接口和调用日志
- 新闻通知与管理端
- PC Web 基础页面、小程序基础目录

## 技术栈

| 层 | 技术 |
|---|---|
| 业务后端 | Java 17、Spring Boot 3、Spring Web、Spring Security、WebClient、MyBatis Plus、MySQL、Lombok、Springdoc OpenAPI、Maven |
| Web | Vue 3、Vite、TypeScript、Vue Router、Pinia、Axios、Element Plus、ECharts |
| 小程序 | 微信小程序原生基础结构 |
| 模型服务 | Python、FastAPI、Pydantic、uvicorn |
| 基础设施 | MySQL 8.4、Docker Compose |

## 项目结构

```text
pv-power-platform/
├── backend/                  # Spring Boot 业务后端
├── web-frontend/             # Vue 3 Web
├── miniapp/                  # 微信小程序骨架
├── model-service/            # FastAPI mock 模型服务
├── docs/
│   ├── api.md
│   ├── requirements.md
│   ├── database.md
│   └── architecture.md
├── docker-compose.yml
├── README.md
└── .gitignore
```

## 系统架构

```text
Vue Web / 小程序
        │ RESTful API
        ▼
Spring Boot 后端 ──────► MySQL
        │ HTTP POST
        ▼
Python FastAPI 模型服务
        │
        ▼
mock 推理 / 后续真实模型
```

前端不直连模型服务，以统一权限、响应格式、任务记录和调用日志，并避免暴露模型服务。详细说明见 [架构文档](docs/architecture.md)。

## 快速启动

前置环境：JDK 17、Maven 3.9+、Node.js 20+、Python 3.11+。MySQL 可选；当前 mock 接口不读数据库。

建议依次在三个终端启动：

### 1. 模型服务

```bash
cd model-service
python -m venv .venv
# Windows PowerShell: .\.venv\Scripts\Activate.ps1
# Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 9000
```

检查：`http://localhost:9000/health`；接口文档：`http://localhost:9000/docs`。

### 2. Spring Boot 后端

```bash
cd backend
mvn spring-boot:run
```

服务：`http://localhost:8080`；Swagger：`http://localhost:8080/swagger-ui.html`。

如需 MySQL：

```bash
docker compose up -d mysql
```

默认数据库为 `pv_platform`。启动前必须通过 `MYSQL_PASSWORD` 环境变量提供本机密码；Docker Compose 使用 `.env` 中的 `MYSQL_ROOT_PASSWORD`。初始化脚本位于 `backend/src/main/resources/sql/init.sql`，该脚本含 `DROP TABLE`，只应人工执行。

### 3. Vue Web

```bash
cd web-frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。开发代理会把 `/api` 和 `/openapi` 转发到 Spring Boot。

## 验证预测链路

模型服务和后端启动后：

```bash
curl -X POST http://localhost:8080/api/predictions \
  -H "Content-Type: application/json" \
  -d '{"stationId":1,"modelId":1,"inputMode":"STATION_HISTORY"}'
```

后端会模拟查询过去 30 分钟数据，调用 `http://localhost:9000/model-api/predict`，并返回 `SUCCESS`、任务 ID 和 6 个预测点。

## Docker Compose

`docker compose up -d mysql` 可直接启动 MySQL。backend、model-service、web-frontend 是 `full` profile 占位；补齐三个 Dockerfile 后再执行：

```bash
docker compose --profile full up --build
```

## 文档

- [需求分析](docs/requirements.md)
- [API 定义与示例](docs/api.md)
- [前端—Spring Boot 接口](docs/back_front_api.md)
- [Spring Boot—模型服务接口](docs/module_back_api.md)
- [数据库草案](docs/database.md)
- [系统架构](docs/architecture.md)
- 子项目说明：[backend](backend/README.md)、[web-frontend](web-frontend/README.md)、[model-service](model-service/README.md)、[miniapp](miniapp/README.md)

## 团队分工建议

| 成员 | 负责内容 |
|---|---|
| 成员 A | Spring Boot 业务后端、数据库、用户/电站/新闻模块 |
| 成员 B | 模型服务、预测接口、模型 API 平台、Spring Boot 调用模型服务 |
| 成员 C | Vue Web 前端、管理端、可视化页面 |
| 成员 D | 小程序端、移动端页面、前端接口联调 |

## 后续开发计划

1. 建表并用 MyBatis Plus 替换内存 mock 数据，补单元和集成测试。
2. 接入 JWT、密码哈希、角色授权与 API Key 哈希/配额。
3. 接入真实天气 API，增加缓存、超时和降级。
4. 将预测任务改为异步执行，持久化任务状态与预测结果。
5. 接入真实模型注册表、版本路由、输入连续性校验和错误治理。
6. 完善 Web 管理端、小程序业务、图表和端到端联调。
7. 增加 Dockerfile、CI、日志追踪和业务接口告警。

不规划服务器资源监控、人脸识别、QQ 登录或微信登录真实逻辑；第三方登录仅作为未来认证扩展点。
