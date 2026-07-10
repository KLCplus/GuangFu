# 后端收束状态

更新时间：2026-07-07

## 总体结论

后端已经具备前后端联调条件。当前代码覆盖了用户认证、权限、电站、光伏数据、天气、模型、预测、分析报告、开放平台、新闻通知等核心模块。配置入口和前端接口文档已经收敛：

- 配置文件：`backend/.env.local`
- 本机真实配置：`backend/.env.local`，不提交
- 一键启动：根目录 `start-local.ps1`
- 前端接口交付：`docs/back_front_api.md`

## 已完成

### 认证、用户、权限

- 用户注册、登录、刷新、退出。
- BCrypt 密码哈希。
- JWT 签发、解析、刷新 token、token version 失效。
- 管理员 `/api/admin/**` 鉴权。
- 用户资料、密码修改、账号注销。
- 管理员用户列表、状态修改、角色修改、删除。
- 角色管理。
- 邮箱验证码发送、登录、注册、找回密码。
- GitHub/Mock OAuth 基础流程。
- 本地 mock 人脸识别流程和阿里云 Provider 接入位。

### 电站与光伏数据

- 电站列表、详情、管理员新增/修改/删除。
- 普通用户按 owner 或授权记录查看电站。
- 电站访问/管理权限校验服务。
- 最新实时数据查询。
- 历史数据按时间范围和粒度聚合查询。
- CSV/XLSX 导入、文件校验、导入任务记录。
- 重复数据策略：`SKIP`、`UPDATE`、`FAIL`。

### 天气

- `WeatherProvider` 抽象。
- `LOCAL` provider。
- `QWEATHER` provider。
- QWeather JWT：EdDSA、`kid`、`sub`、`iat`、`exp`、Ed25519 私钥签名。
- 请求头 `Authorization: Bearer <jwt>`。
- 实时天气和可配置天数预报。
- 数据库缓存和第三方失败时旧缓存降级。
- 电站缺少经纬度时返回业务错误。
- 天气调试页：`/weather-debug.html`。

### 模型与预测

- 模型列表、详情、管理员模型管理。
- 模型状态校验和上线前模型服务健康检查。
- 预测任务创建、输入快照、结果保存、历史查询。
- 模型服务异常时任务失败记录。
- 开放 API 预测复用后端预测流程。

### 分析报告

- 基于电站、光伏数据、天气和预测结果生成分析报告。
- 报告摘要、天气分析、预测分析、异常分析、建议、正文和结构化 JSON 入库。
- 报告历史分页和详情。
- 普通用户只能查看自己的报告，管理员可查看全部。

### 开放平台

- API Key 申请、列表、状态修改、删除。
- Key 只在创建时返回完整值，数据库保存 hash 和 prefix。
- OpenAPI 鉴权 Filter。
- 每分钟限流、每日额度。
- 调用日志和管理员查看。

### 新闻通知

- 新闻列表、详情。
- 管理员新闻草稿、发布、下线、修改、逻辑删除。
- 新闻按角色过滤。
- 发布后生成站内通知。
- 通知列表、未读数、单条已读、全部已读。

### 配置与性能

- Redis 可选，支持本地内存降级。
- Spring Cache 已接入热点数据。
- Tomcat、HikariCP、Redis、验证码、天气、文件上传等配置参数化。
- `start-local.ps1` 一键生成 `backend/.env.local`、检查本机 MySQL、启动后端。
- `backend/.env.local` 已覆盖 `application.yml` 中全部环境变量。

## 当前限制

- 预测任务仍是同步执行，后续可升级为后台队列。
- 光伏数据导入仍是同步执行，超大文件建议后续异步化。
- 模型预测质量取决于 `model-service` 的真实模型实现。
- 微信/QQ OAuth 未实现，当前可用 `mock` 和 `github`。
- 人脸识别默认 local provider 只适合流程演示；真实识别需要阿里云配置。
- Redis 未启用时，验证码、登录锁定和 OpenAPI 限流都是单实例内存状态。
- 生产化还需要 CI、健康检查、日志采集、监控告警和数据库迁移工具。

## 可联调判断

可以进入前端联调，但需要满足这些前置条件：

1. MySQL 已启动并执行初始化脚本。
2. 后端通过 `run-local.ps1` 或等价环境变量启动。
3. 具备管理员账号和普通用户账号。
4. 需要天气时，目标电站必须配置经纬度。
5. 需要 QWeather 时，`backend/.env.local` 中必须配置 QWeather JWT 凭证和私钥路径。
6. 需要预测时，`model-service` 必须启动并可访问。
7. 前端只按 `docs/back_front_api.md` 对接。

## 验证结果

已执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 --version
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -DskipTests compile
```

结果：

- Maven 使用 JDK 23。
- 后端编译通过。
- `backend/.env.local` 覆盖 `application.yml` 中全部 69 个环境变量。

## 收尾建议

后端代码可以进入交付联调。后续修改应遵守：

- 接口变更同步 `docs/back_front_api.md`。
- 配置变更同步 `backend/.env.local`。
- 不提交 `backend/.env.local`、私钥、JWT、数据库密码、第三方 Key。
- 部署环境启用 Redis，并对模型服务、天气服务做真实联调验收。
