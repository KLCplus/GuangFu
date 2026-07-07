# 后端说明

本目录是光伏平台的 Spring Boot 业务后端。前端、小程序和开放 API 客户端都通过本服务访问业务数据；FastAPI 模型服务只由后端调用。

## 当前定位

后端已经进入收束和联调阶段：

- JWT 登录、刷新、退出、权限过滤已接入。
- 用户、角色、电站、光伏数据、天气、模型、预测、分析、开放平台、新闻通知等模块已有 Controller 和 Service 实现。
- QWeather JWT 天气 Provider 已接入，支持数据库缓存和失败降级。
- Redis 是可选增强：本地可降级到内存，部署/多实例建议启用 Redis。
- 前端接口以 [../docs/back_front_api.md](../docs/back_front_api.md) 为准。

## 目录结构

```text
backend/
├─ pom.xml
├─ run-local.ps1             # 后端启动脚本，读取根目录 .env
├─ ARCHITECTURE.md
└─ src/main/
   ├─ java/com/example/pvplatform/
   │  ├─ common/             # Result、PageResult、异常、通用服务
   │  ├─ config/             # Security、Cache、Async、Jackson、WebClient 等配置
   │  ├─ module/             # 业务模块
   │  ├─ persistence/        # DO 和 Mapper
   │  └─ security/           # JWT 和安全上下文
   └─ resources/
      ├─ application.yml
      ├─ mapper/
      ├─ sql/init.sql
      └─ static/weather-debug.html
```

## 配置入口

配置文件职责：

- `src/main/resources/application.yml`：Spring Boot 属性映射，不放真实密钥。
- 根目录 `.env.example`：唯一可提交配置模板。
- 根目录 `.env`：本机真实配置，已被 `.gitignore` 忽略。
- `run-local.ps1`：设置本地默认值后读取根目录 `.env`，并启动 Maven。

不要把 `.env` 文件放进 `src/main/java`。

## 启动

```powershell
cd C:\Users\99140\Desktop\chengdu\code\pv-power-platform\backend
powershell -ExecutionPolicy Bypass -File .\run-local.ps1
```

验证 Java/Maven：

```powershell
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 --version
```

编译：

```powershell
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -DskipTests compile
```

服务地址：

```text
http://localhost:8080
http://localhost:8080/swagger-ui.html
http://localhost:8080/weather-debug.html
```

## 模块

| 模块 | 职责 | 主要接口 |
|---|---|---|
| `auth` | 注册、登录、刷新、退出、邮箱验证码、OAuth、人脸登录 | `/api/auth/**` |
| `user` | 个人资料、头像、账号注销、OAuth 绑定、人脸录入 | `/api/user/**` |
| `role` | 管理员角色管理 | `/api/admin/roles` |
| `station` | 电站列表、详情、管理员 CRUD | `/api/stations`、`/api/admin/stations` |
| `pvdata` | 实时数据、历史数据、CSV/XLSX 导入 | `/api/stations/{stationId}/realtime`、`/history`、`/data/upload` |
| `weather` | 当前天气、天气预报、QWeather/LOCAL Provider | `/api/stations/{stationId}/weather/**` |
| `model` | 模型列表、详情、管理员模型管理 | `/api/models`、`/api/admin/models` |
| `prediction` | 预测任务、结果、历史 | `/api/predictions/**` |
| `analysis` | 综合分析报告 | `/api/analysis/**` |
| `openapi` | API Key、开放预测、调用日志 | `/api/open/**`、`/openapi/v1/predict` |
| `news` | 新闻、通知 | `/api/news`、`/api/notifications` |

每个模块目录下的 `README.md` 记录了模块落地情况和限制。

## Redis 与性能

本地默认：

```env
REDIS_ENABLED=false
CACHE_TYPE=simple
```

部署建议：

```env
REDIS_ENABLED=true
CACHE_TYPE=redis
```

启用 Redis 后会共享验证码、登录失败锁定、OpenAPI 限流/额度、Spring Cache 热点数据。多实例部署必须启用 Redis，否则这些状态会退回各实例本机内存。

## 天气

天气模块支持：

- `LOCAL`：本地确定性模拟天气。
- `QWEATHER`：和风天气 JWT，使用 Ed25519 私钥签名。

配置说明见：

```text
src/main/java/com/example/pvplatform/module/weather/THIRD_PARTY_SETUP.md
```

调试页：

```text
http://localhost:8080/weather-debug.html
```

## 接口文档

- 前端接口：[../docs/back_front_api.md](../docs/back_front_api.md)
- 模型服务接口：[../docs/module_back_api.md](../docs/module_back_api.md)
- Swagger UI：`http://localhost:8080/swagger-ui.html`

## 提交前检查

```powershell
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -DskipTests compile
```

建议按需补跑测试：

```powershell
mvn test
```

提交前确认：

1. 没有提交本机 `.env`、私钥、JWT、数据库密码或第三方 Key。
2. 接口变更已同步到 `docs/back_front_api.md`。
3. 配置项变更已同步到根目录 `.env.example`。
