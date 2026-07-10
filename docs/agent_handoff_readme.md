# 光伏预测平台 Agent 交接 README

本文档用于把当前项目状态、已完成工作、启动方式、联调账号、已知问题和后续计划交给下一个 Agent 继续开发。

更新时间：2026-07-09

## 1. 项目路径

项目根目录：

```powershell
C:\Users\99140\Desktop\chengdu\code\pv-power-platform
```

主要模块：

```text
backend/        Spring Boot 后端
web-frontend/   Vue 3 + TypeScript + Element Plus 前端
model-service/  Python 模型服务，使用 conda d2l 环境
docs/           文档
tools/          本地辅助工具
logs/           本地启动日志和 pid 文件
```

## 2. 当前核心目标

当前阶段不是继续做普通后台页面，而是把 `/reports` 做成“光伏平台智能 Agent 工作台”。

业务定位：

- 以对话为中心
- 支持工具调用入口
- 支持综合分析报告生成
- 支持后续接入预测、天气、电站、模型、API 状态等工具
- 保留真实后端联调，不用假数据伪装成功

当前重点：

1. 保证前端、后端、模型服务能一键启动。
2. 保证登录注册能和后端对齐。
3. 保证 `/reports` 能真实调用综合分析接口。
4. 如果接口失败，要能在页面上暴露错误，辅助排查后端。

## 3. 已完成工作概览

### 3.1 `/reports` 页面

已修改：

```text
web-frontend/src/views/AnalysisReport.vue
```

当前状态：

- 已从传统“报告管理页”重构为 Codex / ChatGPT 风格的 Agent 工作台。
- 中间是对话工作区和 command box。
- 左侧是可收起的会话列表。
- 右侧是可收起的 Inspector。
- Inspector 包含：
  - 上下文
  - 工具
  - 结果
  - 调试
- 输入框支持 slash command：
  - `/report`
  - `/predict`
  - `/weather`
  - `/station`
  - `/api`
- 保留真实接口联调：
  - 查询报告历史
  - 查询报告详情
  - 生成报告
- 页面没有用 mock 报告、mock 历史、mock 成功结果。
- 初始提示、空态文案、按钮文案属于静态 UI，不是伪造后端数据。
- 接口失败时会把错误展示到消息流和右侧调试 Tab。

### 3.2 综合分析接口封装

已检查：

```text
web-frontend/src/api/analysis.ts
```

当前接口路径正确，依赖 `request.ts` 的 `baseURL: '/api'`：

```ts
POST /analysis/report
GET  /analysis/reports
GET  /analysis/reports/{reportId}
```

不要改成：

```text
/api/analysis/...
http://localhost:8080/api/...
```

`analysis.ts` 已包含类型：

- `CreateAnalysisReportRequest`
- `AnalysisReportListItem`
- `AnalysisReportDetail`
- `AnalysisReportQuery`
- `normalizeReportId`

### 3.3 一键启动脚本

已修改：

```text
start-local.ps1
```

当前脚本会尝试启动：

- 后端：`http://localhost:8080`
- 前端：`http://localhost:5173`
- 模型服务：`http://localhost:9000`
- Docker MySQL：如果安装了 Docker 且没有传 `-SkipDocker`

当前机器上检测到 Docker 不可用，所以建议优先使用本地 MySQL，并带 `-SkipDocker` 启动。

推荐启动命令：

```powershell
cd C:\Users\99140\Desktop\chengdu\code\pv-power-platform
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -SkipDocker
```

脚本日志位置：

```text
logs/backend.out.log
logs/backend.err.log
logs/frontend.out.log
logs/frontend.err.log
logs/model-service.out.log
logs/model-service.err.log
```

pid 文件：

```text
logs/backend.pid
logs/frontend.pid
logs/model-service.pid
```

停止服务示例：

```powershell
Stop-Process -Id (Get-Content .\logs\backend.pid) -Force
Stop-Process -Id (Get-Content .\logs\frontend.pid) -Force
Stop-Process -Id (Get-Content .\logs\model-service.pid) -Force
```

## 4. 本地环境配置

当前 `.env` 关键配置：

```env
SERVER_PORT=8080
MYSQL_URL=jdbc:mysql://localhost:3306/pv_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=123456789
REDIS_ENABLED=false
CACHE_TYPE=simple
MODEL_SERVICE_BASE_URL=http://localhost:9000
WEATHER_PROVIDER=LOCAL
MAIL_ENABLED=false
OAUTH_GITHUB_ENABLED=false
```

用户说明：本地 MySQL 密码是 `123456789`。

启动前确认 MySQL：

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $_.LocalPort -eq 3306 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

如果 3306 没有监听，后端大概率启动失败。需要先启动本机 MySQL 服务，或安装 Docker 后让脚本拉起 MySQL 容器。

## 5. 登录注册与测试账号

当前已创建过一个测试账号：

```text
username: codex_auth_0709101550
password: Demo123456
```

注意：

- 如果数据库被重置，该账号可能不存在。
- 如果登录报 502，优先检查后端是否启动成功，以及前端 Vite proxy 是否能转发到 `localhost:8080`。
- 如果登录报 401/403，优先检查账号密码、token、后端安全配置和接口权限。
- 如果后端启动日志出现 Spring Security generated password，说明自定义安全配置可能没有生效或后端启动到了异常路径，需要继续排查。

## 6. 综合分析联调数据

已有本地 seed 脚本：

```text
backend/src/main/resources/sql/local-analysis-seed.sql
```

脚本用途：

- 创建本地联调用光伏电站
- 给所有未删除用户授权该电站
- 插入 PV 功率数据
- 插入天气数据
- 插入预测任务和预测结果

已知联调数据：

```text
stationId: 2
taskId: 8
reports: 1, 2
```

注意：这些 ID 依赖当前数据库状态。如果换库、清库、重复导入，实际 ID 可能变化。以后端接口返回为准。

## 7. 当前后端接口逻辑

综合分析已有 3 个接口：

```text
POST /api/analysis/report
GET  /api/analysis/reports
GET  /api/analysis/reports/{reportId}
```

前端由于 `request.ts` 已设置 `baseURL: '/api'`，所以调用时写：

```text
POST /analysis/report
GET  /analysis/reports
GET  /analysis/reports/{reportId}
```

生成报告 payload：

```json
{
  "stationId": 2,
  "taskId": 8,
  "title": "电站 2 综合分析报告",
  "includeWeather": true,
  "includePrediction": true
}
```

`/reports` 页面当前逻辑：

1. 页面加载调用 `getReports()`。
2. 左侧会话列表显示真实报告历史。
3. 点击历史项调用 `getReport(reportId)`。
4. 输入任务并运行时调用 `createReport(payload)`。
5. 成功后把后端返回内容加入消息流和 Inspector 结果。
6. 成功后刷新报告历史。
7. 失败后显示错误，不使用 mock fallback。

## 8. 模型服务

模型服务目录：

```text
model-service/
```

启动命令由 `start-local.ps1` 执行：

```powershell
C:\Users\99140\.conda\envs\d2l\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 9000
```

要求：

- conda 环境名：`d2l`
- Python 路径：`C:\Users\99140\.conda\envs\d2l\python.exe`
- 服务端口：`9000`

如果模型服务启动失败，先看：

```powershell
Get-Content -Tail 120 -Encoding UTF8 .\logs\model-service.err.log
Get-Content -Tail 120 -Encoding UTF8 .\logs\model-service.out.log
```

当前阶段用户要求“后端和模型接口全都用 mock 数据进行前后端联调”。这里的含义是：后端内部可对模型/天气等外部依赖使用本地或 mock 逻辑，但 `/reports` 前端页面不要伪造报告成功结果。

## 9. 常见启动和联调问题

### 9.1 端口 8080 被占用

现象：

```text
Web server failed to start. Port 8080 was already in use.
```

排查：

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $_.LocalPort -eq 8080 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

结束占用进程：

```powershell
Stop-Process -Id <PID> -Force
```

### 9.2 登录报 502

大概率原因：

- 后端没启动
- 后端启动失败
- 前端代理转发不到后端
- 8080 被别的进程占用

排查：

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $_.LocalPort -in 8080,5173 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

查看后端日志：

```powershell
Get-Content -Tail 160 -Encoding UTF8 .\logs\backend.err.log
Get-Content -Tail 160 -Encoding UTF8 .\logs\backend.out.log
```

### 9.3 后端启动失败

先看：

```powershell
Get-Content -Tail 160 -Encoding UTF8 .\logs\backend.err.log
```

重点检查：

- MySQL 是否在 3306
- 数据库 `pv_platform` 是否存在
- `.env` 里的 `MYSQL_PASSWORD=123456789`
- Java 版本是否兼容。`pom.xml` 配置 Java 21，之前机器上看到 Java 23，也能尝试跑，但如遇兼容问题建议切到 JDK 21。
- 是否缺表或 migration 没执行

### 9.4 `git status` 报 dubious ownership

当前 `git status` 曾报：

```text
fatal: detected dubious ownership in repository
```

如果需要使用 git 命令，可执行：

```powershell
git config --global --add safe.directory C:/Users/99140/Desktop/chengdu/code/pv-power-platform
```

这个是本机 Git 安全目录问题，不是代码问题。

## 10. 当前 `/reports` 前端设计原则

继续开发时请保持这些约束：

- 不要改回传统后台三栏大卡片布局。
- 不要让顶部表单成为页面主导。
- 页面重心必须是消息流和底部 command box。
- 左右面板是辅助信息，必须可收起。
- 不要用 mock 数据伪装后端成功。
- 后端没返回的字段显示空态或“不存在”，不要编造。
- 调试 Tab 可以保留，但默认不要抢主视觉。
- 继续使用 Vue 3 + TypeScript + Element Plus。
- 不引入重型新依赖。

允许继续修改：

```text
web-frontend/src/views/AnalysisReport.vue
web-frontend/src/api/analysis.ts
start-local.ps1
docs/*
```

除非有明确任务，不要修改：

```text
web-frontend/src/router/index.ts
web-frontend/src/store/user.ts
backend 的 auth/user/prediction/model/weather 等无关模块
```

## 11. 下一步开发计划

### P0：把本地环境跑稳定

1. 启动 MySQL，确认 3306 监听。
2. 执行：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -SkipDocker
   ```

3. 确认端口：

   ```text
   5173 前端
   8080 后端
   9000 模型服务
   ```

4. 打开：

   ```text
   http://localhost:5173
   ```

5. 用测试账号登录，或注册新账号。

### P0：确认登录注册链路

需要验证：

- 注册接口是否成功写入数据库
- 登录接口返回 token
- 前端是否保存 token
- `request.ts` 是否自动带 `Authorization: Bearer token`
- 登录后访问 `/reports` 是否不再 401

如果仍然登录失败，先不要改 `/reports`，优先排查 auth 接口和前端登录页 payload 是否对齐。

### P0：确认综合分析接口

登录后在 `/reports` 使用：

```text
stationId: 2
taskId: 8
includeWeather: true
includePrediction: true
```

验证：

- `GET /analysis/reports`
- `GET /analysis/reports/{reportId}`
- `POST /analysis/report`

如果失败，看 `/reports` 右侧 Inspector 的“调试”Tab，再结合后端日志定位。

### P1：补齐后端 mock 依赖

如果生成报告依赖天气、模型、预测任务，而本地服务不稳定，建议在后端内部做可控 mock：

- `WEATHER_PROVIDER=LOCAL` 时使用本地天气数据
- 模型服务不可用时返回明确错误或走 mock model result
- 分析服务需要检查 station/task 权限和数据缺失时，错误信息要清晰

注意：这属于后端内部 mock，不是前端 mock。

### P1：完善 `/reports` Agent 能力

在真实接口稳定后继续扩展：

- slash command 真正映射工具
- `/report` 调综合分析
- `/predict` 调预测任务接口
- `/weather` 调天气上下文接口
- `/station` 查询电站上下文
- `/api` 查询平台 API 状态
- 会话归档、置顶、重命名持久化
- 右侧结果支持 Markdown 渲染和导出

### P2：产品化

- 把 Agent 会话抽象为后端实体
- 新增真正的 chat/task API
- 支持工具调用日志持久化
- 支持报告导出 Markdown/HTML/PDF
- 支持用户中心里的会话管理
- 接入真实模型服务和长期记忆前先把权限、审计、错误边界做好

## 12. 给下一个 Agent 的注意事项

1. 先跑环境，再改代码。
2. 前端 502 先看后端是否起来，不要盲目改前端。
3. `/reports` 的接口调试面板是排错入口，不要删。
4. 前端页面不要重新塞 mock 报告。
5. 后端如果需要 mock，应该在后端 service 层或配置层做，并返回真实接口响应。
6. 如果要动登录注册，先对照后端 Controller 的路径、字段名和前端登录页 payload。
7. 如果要动启动脚本，保留日志输出和 pid 文件，方便用户排查。
8. 用户希望“一键启动”，所以新增服务时也要写进 `start-local.ps1`。

## 13. 快速命令备忘

启动全部：

```powershell
cd C:\Users\99140\Desktop\chengdu\code\pv-power-platform
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -SkipDocker
```

查看端口：

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $_.LocalPort -in 3306,8080,5173,9000 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

查看后端日志：

```powershell
Get-Content -Tail 160 -Encoding UTF8 .\logs\backend.err.log
Get-Content -Tail 160 -Encoding UTF8 .\logs\backend.out.log
```

查看前端日志：

```powershell
Get-Content -Tail 120 -Encoding UTF8 .\logs\frontend.err.log
Get-Content -Tail 120 -Encoding UTF8 .\logs\frontend.out.log
```

查看模型日志：

```powershell
Get-Content -Tail 120 -Encoding UTF8 .\logs\model-service.err.log
Get-Content -Tail 120 -Encoding UTF8 .\logs\model-service.out.log
```

前端构建：

```powershell
cd .\web-frontend
npm run build
```

后端启动：

```powershell
cd .\backend
.\run-local.ps1
```

模型服务启动：

```powershell
cd .\model-service
C:\Users\99140\.conda\envs\d2l\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 9000
```

