# 模型预测、云图预测与部署适配交接文档

更新时间：2026-07-13

适用范围：`web-frontend` 前端、`backend` Spring Boot 后端、`model-service` FastAPI 模型服务。

## 0. 服务器迁移与配置收敛（上线前必读）

### 0.1 本次迁移的边界

本项目的生产调用链必须保持为：浏览器 -> 前端/Nginx -> Spring Boot -> FastAPI 模型服务 -> 模型权重。浏览器、小程序和开放 API 调用方**不得直接访问** FastAPI；鉴权、用户与电站权限、预测任务记录、调用日志和计费均由 Spring Boot 负责。

当前根目录 `.env` 是唯一的本机真实配置入口，`start-local.sh` 已统一读取该文件。`backend/.env.local` 是历史兼容备份，不应再作为服务器或开发环境的第二配置源。部署时应以根 `.env` 的变量名为准，将值写入服务器的权限受控配置（如 systemd `EnvironmentFile`、CI/CD Secret 或容器 Secret），不要提交真实 `.env`、私钥、权重或数据库备份。

`start-local.sh` 用于本地开发和初始化验证；它会管理本地进程并可执行数据库检查，不应直接作为生产守护进程。生产环境需要分别由 systemd、Docker Compose、Kubernetes 或现有发布平台托管前端、后端和模型服务，并提供日志、重启策略、健康检查和网络访问控制。

### 0.2 模型同学合并前端改动的规则

模型同学后续会合并并继续维护以下两个模型相关页面：

| 场景 | 页面与文件 | 合并时必须保留的边界 |
| --- | --- | --- |
| 功率/多模态模型使用 | `/models/use`，`web-frontend/src/views/ModelPrediction.vue` | 只通过前端 API 封装请求 Spring Boot；请求字段、上传数据解析和错误展示要与后端 DTO/模型服务契约一起变更。 |
| 云图预测 | `/cloud-forecast`，`web-frontend/src/views/CloudForecast.vue` | 保持“10 张输入云图 -> 后端 -> 10 张输出云图”的链路；不要改为从浏览器直连 FastAPI。 |

合并顺序建议为：先同步主分支并处理这两个页面的冲突，再合并模型分支；随后由前端、后端、模型三方使用同一份请求/响应样例联调。不要在合并模型页面的同时重构路由、主题侧边栏、鉴权或通用请求层，以免扩大冲突范围。涉及接口字段、模型名称、输入帧数、图片尺寸、输出步数的改动，必须同一提交或同一发布批次同步更新：前端页面、前端 API 类型、Spring DTO/转发逻辑、`model_info` 元数据和 FastAPI 路由。

### 0.3 配置责任与最小权限

模型服务需要适配服务器配置，但不应把根 `.env` 中所有第三方密钥复制进模型进程。根 `.env` 是 Spring Boot 的完整业务配置；FastAPI 只接收其真正需要的模型运行配置和最小权限凭据。

| 配置组 | 根 `.env` 变量 | 所属服务与迁移要求 |
| --- | --- | --- |
| 基础地址与模型链路 | `BACKEND_HOST`、`SERVER_PORT`、`FRONTEND_HOST`、`FRONTEND_PORT`、`MODEL_SERVICE_BASE_URL`、`MODEL_HOST`、`MODEL_PORT` | 后端以 `MODEL_SERVICE_BASE_URL` 访问 FastAPI。服务器上应改为内网 DNS/私网地址，不能保留 `localhost`（除非后端与模型服务同机）。模型服务监听地址和端口必须与该 URL 对齐。 |
| 数据库 | `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`MYSQL_POOL_MAX_SIZE`、`MYSQL_POOL_MIN_IDLE` | 仅 Spring Boot/MySQL 初始化使用。模型服务不得直接连业务库；若未来确有需要，应创建只读或专用最小权限账号并单独评审。 |
| 登录与会话 | `JWT_SECRET`、`JWT_ACCESS_TOKEN_EXPIRATION`、`JWT_REFRESH_TOKEN_EXPIRATION`、`OAUTH_GITHUB_*`、`OAUTH_CALLBACK_BASE_URL`、`OAUTH_BACKEND_CALLBACK_BASE_URL` | 仅前端地址、Spring Boot 和 OAuth 回调相关。上线前把回调地址改成 HTTPS 公网域名，并在 GitHub OAuth App 中登记完全一致的回调地址；模型服务不需要这些密钥。 |
| 阿里云 OSS/人脸 | `OSS_*`、`ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET`、`ALIYUN_OSS_*`、`ALIYUN_FACE_*`、`FACE_*` | 默认由后端处理文件、头像、新闻附件和人脸能力。若模型服务必须从 OSS 取输入/写结果，应新建受限 RAM 策略、固定 bucket/prefix 和独立变量，不能复用全权限访问密钥。 |
| 邮件 | `MAIL_*`、`VERIFY_CODE_*` | 仅后端发送验证码和业务邮件；模型服务不应持有 SMTP 密码。服务器需检查 25/465/587 出站策略及发件域 SPF/DKIM。 |
| 外部业务能力 | `WEATHER_*`、`QWEATHER_*`、`PVOUTPUT_*`、`DEEPSEEK_*`、`ANALYSIS_LLM_*`、`AGENT_RUNTIME_*`、`AGENT_INTERNAL_TOKEN` | 分别由后端、Agent runtime 使用。模型服务只有在新增明确的直接依赖后才增加对应的专用配置，不得隐式读取或依赖无关密钥。 |
| 缓存与本地存储 | `REDIS_*`、`CACHE_*`、`AVATAR_*`、`FACE_STORAGE_DIR`、`PV_IMPORT_*` | 后端使用。容器部署时应映射持久化卷，并把路径改为容器内固定目录。 |

模型端需要完成的配置适配：将模型权重目录、设备选择（CPU/CUDA）、模型运行模式、允许的跨服务地址和可能的 OSS 输入/输出路径改为显式环境变量或部署参数；启动时打印**不含密钥**的生效配置、权重版本和设备信息。当前代码尚未实现 `MODEL_PREDICTOR_MODE`、权重路径和设备选择等统一环境变量时，不可只在服务器手改 Python 导入或硬编码路径；应由模型同学先实现并提交该配置契约，再部署。

### 0.4 数据库建立、初始化与增量迁移

数据库名为 `pv_platform`。全量基线是 `backend/src/main/resources/sql/init.sql`，它包含 `DROP TABLE`，因此只适用于全新、可清空的数据库。先备份生产库，再执行任何结构变更。

新环境的推荐顺序：

1. 创建 MySQL 8 实例、数据库和最小权限业务账号；在服务器受控配置中填入 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`，`MYSQL_ROOT_PASSWORD` 仅供初始化管理员操作使用。
2. 将当前发布版本的 `init.sql` 导入**空库**。开发/测试环境可使用 `./start-local.sh --init-db --force-db-reset`；生产环境应在维护窗口由 DBA/发布流程显式执行同一份脚本，切勿依赖应用启动自动建库。
3. 对已有环境，不执行全量 `init.sql`。先备份，再按发布版本审查并执行对应的增量 SQL/应用内 additive migration；确认 `model_info`、`prediction_*`、`file_resource`、`news`、`api_*` 等表和索引已齐全。
4. 启动后端后检查 `/swagger-ui.html`、模型列表和健康接口；再启动模型服务并完成真实样例预测；最后发布前端与 Nginx 反向代理。

上线前至少验证一次恢复：从备份恢复到隔离库，应用同版本增量迁移，确认可以启动并读取模型与预测历史。迁移脚本、数据库备份和模型权重都应带版本号与校验值。

### 0.5 发布顺序与验收

1. 在预发布环境导入/迁移数据库，并校验根 `.env` 变量名完整、密钥来源受控、回调域名为 HTTPS。
2. 部署模型服务及真实权重，访问 `/health`、`/model-api/models`、`/cloud-api/models`；确认权重哈希、CUDA/CPU 和内存满足要求。
3. 配置 Spring Boot 的 `MODEL_SERVICE_BASE_URL` 指向模型服务内网地址，启动后以 30 个数值点/30 张图片验证功率预测，以 10 张真实云图验证云图预测。
4. 构建并发布前端，Nginx 仅公开前端和 Spring Boot 所需路径；禁止将模型服务端口暴露到公网。分别回归 `/models/use`、`/cloud-forecast`、登录、OSS 上传、邮件验证码和 GitHub OAuth 回调。
5. 保留回滚包：上一版前端/后端/模型镜像或制品、数据库备份、当前与上一版权重及其校验值。发生模型接口不兼容时，优先回滚同一发布批次的模型与后端，再处理前端展示。

## 1. 总体架构

当前系统的模型相关调用链路是：

```text
浏览器
  -> web-frontend /api 代理
  -> backend Spring Boot
  -> model-service FastAPI
  -> 模型权重 / 云图权重
```

前端不应直接调用 `model-service`。所有业务权限、任务记录、调用日志、钱包扣费、API Key 校验都在 `backend` 完成。

关键服务地址：

| 服务 | 默认地址 | 说明 |
| --- | --- | --- |
| 前端开发服务 | `http://127.0.0.1:5173` | Vite，代理 `/api` 和 `/openapi` 到后端 |
| 后端服务 | `http://127.0.0.1:8080` | Spring Boot，对外业务 API |
| 模型服务 | `http://127.0.0.1:9000` | FastAPI，后端通过 `MODEL_SERVICE_BASE_URL` 调用 |

后端配置项在 `backend/src/main/resources/application.yml`：

```yaml
model-service:
  base-url: ${MODEL_SERVICE_BASE_URL:http://localhost:9000}
  response-timeout-seconds: ${MODEL_SERVICE_RESPONSE_TIMEOUT_SECONDS:120}
```

服务器部署时需要显式设置：

```env
MODEL_SERVICE_BASE_URL=http://<model-service-host>:9000
MODEL_SERVICE_RESPONSE_TIMEOUT_SECONDS=120
```

## 2. 当前真实实现状态

### 2.1 功率预测

已打通前后端和后端到 FastAPI 的接口，但 `model-service/app/services/predictor.py` 当前默认是 mock 预测器：

- 接收固定接口格式。
- 校验模型名、30 个数值输入、30 张图片输入。
- 根据最后 6 个功率点生成未来 5-30 分钟的 6 个预测值。
- 不加载真实 PyTorch 权重。

真实模型适配代码已经存在于 `model-service/app/services/model_adapter.py`，类名是 `RealPredictor`，但当前路由 `model-service/app/routers/predict.py` 仍导入：

```python
from app.services.predictor import predictor
```

队友接入真实模型时需要把路由切到真实适配器，例如在 `predictor.py` 中按环境变量选择 mock/real，或直接改为导入 `RealPredictor()`。建议保留 mock 开关，便于服务器无 GPU 时做接口联调。

### 2.2 云图预测

云图预测接口已经调用真实适配器 `model-service/cloud_prediction/cloud_predictor.py`：

- 输入 10 张历史云图。
- 模型：`SimVP_Cloud`。
- 权重路径：`model-service/cloud_prediction/checkpoints/epoch-epoch=099.ckpt`。
- 输出 10 张预测云图 base64 PNG。

注意：当前仓库内部分 `.pth/.ckpt` 文件看起来像占位小文件，部署前必须确认真实权重已放到服务器对应目录，且文件大小、校验值、加载日志正确。

### 2.3 前端预测页面现状

`web-frontend/src/views/ModelPrediction.vue` 当前还有明显联调占位逻辑：

- 上传的 Excel/CSV 还没有被解析成真实 30 个数值点。
- `buildPredictionValues()` 生成的是前端合成数值。
- `buildPredictionImages()` 当前传的是占位 base64：`data:image/png;base64,aGVsbG8=`，不是用户上传图片内容。
- 如果用户选择的云图少于 30 张，后端会拒绝，因为后端要求恰好 30 张。

真实部署前，队友需要把该页面改成：

1. 解析 CSV/XLSX 中的 30 行连续 1 分钟数据。
2. 字段至少映射为 `time` 和 `value`；如果后端要使用真实温度/辐照度，需要扩展前端和后端 DTO。
3. 读取上传的 30 张云图为 Data URI/base64，并保证时间和数值输入一一对应。
4. 接口失败时不要继续把 mock 曲线当成真实结果展示，建议明确标识“演示兜底”或移除兜底。

`web-frontend/src/views/CloudForecast.vue` 已经会把用户上传的 10 张图转成 Data URI 调用后端，云图链路相对完整。

## 3. 前端页面与 API 文件

| 页面 | 路由 | 文件 | 说明 |
| --- | --- | --- | --- |
| 功率/多模态预测 | `/models/use` | `web-frontend/src/views/ModelPrediction.vue` | 创建预测任务并展示结果 |
| 云图预测 | `/cloud-forecast` | `web-frontend/src/views/CloudForecast.vue` | 上传 10 张云图，展示 10 张预测云图 |
| 预测历史 | `/predictions` | `web-frontend/src/views/PredictionHistory.vue` | 查询预测任务历史 |
| 预测详情 | `/predictions/:taskId` | `web-frontend/src/views/PredictionDetail.vue` | 查看任务详情和结果 |
| API 管理 | `/api` | `web-frontend/src/views/ApiPlatform.vue` | API Key、调用日志、统计图表、钱包 |
| 管理员模型管理 | `/admin/models` | `web-frontend/src/views/admin/ModelManage.vue` | 管理模型元数据、状态 |
| 天气 | `/weather` | `web-frontend/src/views/Weather.vue` | 按电站经纬度取实时天气和预报 |

前端 API 封装：

| 文件 | 主要接口 |
| --- | --- |
| `web-frontend/src/api/prediction.ts` | `/predictions`、`/predictions/{taskId}`、`/predictions/history` |
| `web-frontend/src/api/cloudForecast.ts` | `/cloud-forecast/predict` |
| `web-frontend/src/api/model.ts` | `/models`、`/models/{modelId}`、管理员模型接口 |
| `web-frontend/src/api/open.ts` | `/open/*`、`/openapi/v1/predict`、统计接口 |
| `web-frontend/src/api/weather.ts` | `/stations/{stationId}/weather/*`、`/weather/*` |

## 4. 后端统一响应格式

后端业务接口一般返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

前端 `web-frontend/src/api/request.ts` 会自动解包 `data`。因此本文档的“前端收到的数据”默认指解包后的 `data`。

## 5. 内部功率预测接口

### 5.1 创建预测任务

```http
POST /api/predictions
Authorization: Bearer <jwt>
Content-Type: application/json
```

后端入口：`backend/src/main/java/com/example/pvplatform/module/prediction/controller/PredictionController.java`

请求体：

```json
{
  "stationId": 1,
  "modelId": 101,
  "inputMode": "MANUAL_MULTIMODAL",
  "inputStartTime": "2026-07-13 10:00:00",
  "inputEndTime": "2026-07-13 10:29:00",
  "numericValues": [
    { "time": "2026-07-13 10:00:00", "value": 520.4 }
  ],
  "inputImages": [
    { "time": "2026-07-13 10:00:00", "image": "data:image/png;base64,..." }
  ]
}
```

字段要求：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `stationId` | number | 是 | 必须存在，且当前用户有查看权限 |
| `modelId` | number | 是 | 必须存在，且模型状态为 `ONLINE` |
| `inputMode` | string | 否 | 当前实际只支持 `MANUAL_MULTIMODAL`，为空会默认该值 |
| `numericValues` | array | 是 | 必须恰好 30 个 |
| `numericValues[].time` | string | 是 | `yyyy-MM-dd HH:mm:ss` |
| `numericValues[].value` | number | 是 | 有限数值，后端会同时映射成 power/temperature/irradiance |
| `inputImages` | array | 是 | 必须恰好 30 个 |
| `inputImages[].time` | string | 是 | 必须与同下标数值时间一致 |
| `inputImages[].image` | string | 是 | base64/Data URI/服务端可读路径 |

强校验：

- 30 个数值点和 30 张图必须一一对应。
- 时间必须严格升序。
- 相邻时间必须相差 60 秒。
- 图片时间必须和数值时间完全一致。
- 模型必须 `ONLINE`。

返回：

```json
{
  "taskId": 123
}
```

### 5.2 查询任务

```http
GET /api/predictions/{taskId}
```

返回字段：

```json
{
  "taskId": 123,
  "taskNo": "PRED-...",
  "taskStatus": "SUCCESS",
  "modelName": "iTransformer",
  "modelCode": "iTransformer",
  "stationId": 1,
  "inputMode": "MANUAL_MULTIMODAL",
  "createdAt": "2026-07-13 10:31:00",
  "costTime": 430,
  "predictions": []
}
```

### 5.3 查询预测结果

```http
GET /api/predictions/{taskId}/results
```

返回：

```json
[
  {
    "timeOffset": 5,
    "predictTime": "2026-07-13 10:34:00",
    "predictPower": 538.12,
    "actualPowerKw": null,
    "errorValue": null,
    "errorRate": null
  }
]
```

说明：

- 正常应返回 6 条：`timeOffset = 5,10,15,20,25,30`。
- `actualPowerKw/errorValue/errorRate` 当前只有预测后真实发电数据回填时才会有值。

### 5.4 查询预测历史

```http
GET /api/predictions/history?pageNum=1&pageSize=10&stationId=1&modelId=101&status=SUCCESS
```

返回分页结构：

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 10
}
```

## 6. 后端调用 FastAPI 功率预测接口

后端不是把前端原始请求直接透传到 FastAPI，而是转换为：

```http
POST {MODEL_SERVICE_BASE_URL}/model-api/predict
Content-Type: application/json
```

请求体：

```json
{
  "modelName": "iTransformer",
  "input": [
    {
      "time": "2026-07-13 10:00:00",
      "power": 520.4,
      "temperature": 520.4,
      "irradiance": 520.4
    }
  ],
  "inputImages": [
    {
      "time": "2026-07-13 10:00:00",
      "image": "data:image/png;base64,..."
    }
  ]
}
```

重要现状：

- `modelName` 来自数据库 `model_info.service_model_name`。
- 内部预测接口的前端 DTO 目前只有 `numericValues[].value`，后端会把同一个值同时填入 `power`、`temperature`、`irradiance`。
- 如果真实模型需要真实温度、辐照度，建议把 `PredictionRequest.NumericValue` 扩展为 `power/temperature/irradiance`，同时更新 `web-frontend/src/api/prediction.ts` 和 `ModelPrediction.vue`。

FastAPI 返回必须满足后端校验：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "modelName": "iTransformer",
    "predictions": [
      { "timeOffset": 5, "predictPower": 538.12 },
      { "timeOffset": 10, "predictPower": 541.3 },
      { "timeOffset": 15, "predictPower": 545.0 },
      { "timeOffset": 20, "predictPower": 550.2 },
      { "timeOffset": 25, "predictPower": 552.6 },
      { "timeOffset": 30, "predictPower": 556.1 }
    ],
    "costTime": 430
  }
}
```

后端会严格检查：

- `data.modelName` 必须等于期望模型名。
- `predictions.length` 必须等于 `model_info.output_steps`，默认 6。
- `timeOffset` 必须是 5 到 30 内的 5 分钟倍数。
- `predictPower` 必须是有限非负数。
- `costTime` 必须非负。

## 7. FastAPI 功率模型服务

服务入口：`model-service/app/main.py`

接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/health` | 健康检查 |
| GET | `/model-api/models` | 查询模型服务支持的模型 |
| POST | `/model-api/predict` | 功率预测 |

启动命令：

```bash
cd model-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 9000
```

建议服务器增加 systemd 或 supervisor，并在服务启动后检查：

```bash
curl http://127.0.0.1:9000/health
curl http://127.0.0.1:9000/model-api/models
```

真实模型接入任务：

1. 准备真实权重到 `model-service/checkpoints/`。
2. 确认 `model_adapter.py` 内每个模型的 checkpoint 文件名和架构参数正确。
3. 让路由使用 `RealPredictor`。
4. 跑通 `POST /model-api/predict`，确认返回 6 个预测点。
5. 再通过后端 `/api/predictions` 验证任务落库和结果展示。

## 8. 云图预测接口

### 8.1 前端调用后端

```http
POST /api/cloud-forecast/predict
Authorization: Bearer <jwt>
Content-Type: application/json
```

后端入口：`backend/src/main/java/com/example/pvplatform/module/cloud/controller/CloudForecastController.java`

请求体：

```json
{
  "modelName": "SimVP_Cloud",
  "inputImages": [
    "data:image/png;base64,..."
  ]
}
```

字段要求：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `modelName` | string | 是 | 当前为 `SimVP_Cloud` |
| `inputImages` | array[string] | 是 | 必须恰好 10 张 |

后端返回给前端：

```json
{
  "modelName": "SimVP_Cloud",
  "predictions": [
    {
      "frameIndex": 0,
      "timeOffset": 5,
      "image": "data:image/png;base64,...",
      "confidence": 96.0,
      "cloudCoverage": null
    }
  ],
  "costTime": 350
}
```

说明：

- 后端会把 FastAPI 返回的纯 base64 自动补成 `data:image/png;base64,...`。
- `confidence` 目前是后端按帧序号生成的展示值，不是模型真实置信度。
- `cloudCoverage` 目前为 `null`。如果真实模型能返回云量，需要扩展 FastAPI `CloudPredictResponse.Prediction` 和后端 `CloudPredictResponse`、`CloudForecastFrameVO` 映射。

### 8.2 后端调用 FastAPI 云图预测

```http
POST {MODEL_SERVICE_BASE_URL}/cloud-api/predict
Content-Type: application/json
```

请求体：

```json
{
  "modelName": "SimVP_Cloud",
  "inputImages": [
    "data:image/png;base64,..."
  ]
}
```

FastAPI 返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "modelName": "SimVP_Cloud",
    "predictions": [
      {
        "frameIndex": 0,
        "image": "<base64-png>"
      }
    ],
    "costTime": 350
  }
}
```

FastAPI 云图模型细节：

- 代码：`model-service/cloud_prediction/cloud_predictor.py`
- 输入：10 帧，按时间从早到晚。
- 预处理：转灰度、遮挡时间戳/相机信息、resize 到 128x128、归一化。
- 输出：10 帧 128x128 PNG base64。
- 权重：`model-service/cloud_prediction/checkpoints/epoch-epoch=099.ckpt`。

## 9. OpenAPI 对外预测接口

对外 API 用于购买/申请 API Key 后直接调用模型预测。

```http
POST /openapi/v1/predict
X-API-KEY: <api-key>
Content-Type: application/json
```

请求体：

```json
{
  "stationId": 1,
  "modelName": "iTransformer",
  "input": [
    {
      "time": "2026-07-13 10:00:00",
      "power": 520.4,
      "temperature": 31.2,
      "irradiance": 820.5
    }
  ],
  "inputImages": [
    {
      "time": "2026-07-13 10:00:00",
      "image": "data:image/png;base64,..."
    }
  ]
}
```

约束：

- `input` 必须 30 个。
- `inputImages` 必须 30 个。
- `modelName` 可以匹配 `model_info.service_model_name` 或 `model_info.model_code`。
- 时间格式必须 `yyyy-MM-dd HH:mm:ss`。
- 相邻输入必须 60 秒间隔。
- API Key 必须有效，并且账户余额足够。

返回：

```json
{
  "taskId": 123,
  "taskNo": "PRED-...",
  "status": "SUCCESS",
  "modelName": "iTransformer",
  "predictions": [
    { "timeOffset": 5, "predictPower": 538.12 }
  ],
  "costTime": 430
}
```

日志与统计：

- 每次调用都会写入 `api_call_log`。
- 成功后会写入 `prediction_task` 和 `prediction_result`。
- 成功调用会扣钱包余额，当前固定按后端账户服务配置计费。
- `inputTokens/outputTokens/totalTokens` 当前是兼容字段，实际含义是输入帧数、输出点数及总量，不是大模型 Token。

## 10. API 管理与图表统计

前端页面：`http://127.0.0.1:5173/api`

后端接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/open/apply-key` | 申请 API Key |
| GET | `/api/open/keys` | 查询自己的 API Key |
| PUT | `/api/open/keys/{apiKeyId}/status` | 启停 Key |
| PUT | `/api/open/keys/{apiKeyId}/name` | 修改 Key 名称 |
| DELETE | `/api/open/keys/{apiKeyId}` | 删除 Key |
| POST | `/api/open/keys/{apiKeyId}/reset` | 重置 Key |
| GET | `/api/open/call-logs` | 分页查询调用日志 |
| GET | `/api/open/call-logs/export` | 导出调用日志 |
| GET | `/api/open/usage/summary` | 汇总卡片 |
| GET | `/api/open/usage/trend` | 趋势图 |
| GET | `/api/open/usage/by-model` | 模型分布图 |
| GET | `/api/open/usage/by-key` | Key 分布图 |
| GET | `/api/open/wallet` | 钱包和流水 |
| POST | `/api/open/wallet/recharge` | 充值，本地为 mock 支付 |

统计接口筛选参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `startTime` | string | `yyyy-MM-dd HH:mm:ss` |
| `endTime` | string | `yyyy-MM-dd HH:mm:ss` |
| `apiKeyId` | number | 可选 |
| `modelId` | number | 可选 |
| `granularity` | string | `DAY` 或 `HOUR`，仅趋势接口 |

`/api` 图表使用 ECharts，数据来自后端聚合，不是前端临时计算。部署后要确认 `api_call_log` 表存在，且启动迁移补齐了 `input_tokens/output_tokens/total_tokens` 和统计索引。

## 11. 模型元数据与模型管理

普通用户模型列表：

```http
GET /api/models?type=NUMERIC
GET /api/models/{modelId}
```

管理员模型管理：

```http
GET /api/admin/models
POST /api/admin/models
PUT /api/admin/models/{modelId}
PUT /api/admin/models/{modelId}/status
```

核心表：`model_info`

关键字段：

| 字段 | 说明 |
| --- | --- |
| `model_id` | 后端业务模型 ID，前端内部预测用它 |
| `model_code` | 业务模型编码，可用于 OpenAPI `modelName` |
| `model_name` | 展示名称 |
| `model_type` | `NUMERIC`、`MULTIMODAL`、`FUSION`、`CLOUD_PREDICTION` 等 |
| `service_model_name` | FastAPI 中的真实模型名，必须与 `/model-api/models` 对齐 |
| `status` | 只有 `ONLINE` 可调用 |
| `input_window_minutes` | 当前默认 30 |
| `input_frame_interval_seconds` | 当前默认 60 |
| `output_steps` | 当前默认 6 |
| `output_step_minutes` | 当前默认 5 |
| `api_path` | 当前默认 `/model-api/predict` |

对齐要求：

- `model_info.service_model_name` 必须等于 FastAPI 返回的 `modelName`。
- `model_info.output_steps` 必须等于 FastAPI 返回的预测点数量。
- 如果新增云图纯预测模型到模型广场，需要确认它是否走 `/cloud-api/predict`，不要误配成 `/model-api/predict`。

## 12. 天气接口与电站经纬度

天气模块已支持两类查询：

### 12.1 按电站查询

```http
GET /api/stations/{stationId}/weather/current
GET /api/stations/{stationId}/weather/forecast
```

前端 `/weather` 页面当前使用这两个接口。

要求：

- 电站存在。
- 当前用户有电站查看权限。
- `power_station.longitude` 和 `power_station.latitude` 不能为空。

如果电站没有经纬度，后端返回：

```text
电站未配置经纬度，无法获取天气
```

### 12.2 按地点或经纬度查询

```http
GET /api/weather/current?location=成都
GET /api/weather/forecast?location=成都
GET /api/weather/current?longitude=104.0668&latitude=30.5728
GET /api/weather/forecast?longitude=104.0668&latitude=30.5728
```

说明：

- `location` 会通过当前 WeatherProvider 解析位置。
- 经纬度参数顺序是 `longitude, latitude`。
- 直接按经纬度查询不依赖电站权限，也不写入某个电站缓存。

返回当前天气：

```json
{
  "stationId": null,
  "weather": "晴",
  "temperature": 31.2,
  "humidity": 58,
  "windDirection": "东南风",
  "windPower": "3级",
  "windSpeed": 3.2,
  "reportTime": "2026-07-13 14:00:00",
  "source": "QWEATHER",
  "cached": false
}
```

返回预报：

```json
[
  {
    "date": "2026-07-13",
    "dayWeather": "多云",
    "nightWeather": "阴",
    "dayTemp": 33,
    "nightTemp": 25,
    "humidity": 62,
    "windDirection": "东南风",
    "windPower": "3级",
    "source": "QWEATHER",
    "cached": false
  }
]
```

天气 Provider 配置：

```env
WEATHER_PROVIDER=QWEATHER
WEATHER_AUTH_TYPE=JWT
WEATHER_BASE_URL=https://devapi.qweather.com
QWEATHER_PROJECT_ID=<project-id>
QWEATHER_KEY_ID=<jwt-key-id>
QWEATHER_PRIVATE_KEY_PATH=/path/to/ed25519-private.pem
WEATHER_CACHE_MINUTES=10
WEATHER_FORECAST_DAYS=3
```

排错重点：

- 本地默认 `WEATHER_PROVIDER=LOCAL`，返回模拟天气，不是真实天气。
- 真实天气必须设置 `QWEATHER` 和 JWT 私钥。
- 和风天气 location 参数必须是 `longitude,latitude`，代码已按这个顺序生成。
- 部分电站拿不到天气时，先查 `power_station.longitude/latitude` 是否为空、是否经纬度写反、是否超出范围。
- 第三方失败时，按电站查询会尝试返回旧缓存；无缓存则返回 502。

## 13. 数据库相关表

模型和预测：

| 表 | 用途 |
| --- | --- |
| `model_info` | 模型元数据、服务模型名、状态、输入输出窗口 |
| `model_metric` | 模型评估指标 |
| `model_file` | 模型文件路径记录，当前可选 |
| `prediction_task` | 预测任务主表 |
| `prediction_input_snapshot` | 预测输入快照 |
| `prediction_result` | 预测结果，通常每任务 6 条 |

天气：

| 表 | 用途 |
| --- | --- |
| `power_station` | 电站基础信息，包含经纬度 |
| `weather_data` | 天气缓存，当前和预报都存在这张表 |

开放 API：

| 表 | 用途 |
| --- | --- |
| `api_key` | API Key 哈希、状态、限额 |
| `api_call_log` | API 调用日志和统计来源 |
| `open_wallet_account` | 开放平台钱包账户 |
| `open_recharge_order` | 充值订单 |
| `open_wallet_record` | 钱包流水 |

初始化脚本：

- `backend/src/main/resources/sql/init.sql`：全量初始化，包含 `DROP TABLE`，只能人工初始化新库时使用。
- `backend/src/main/resources/sql/api_usage_migration.sql`：API 统计字段和索引补丁。
- `backend/src/main/resources/sql/model_marketplace_metadata_19_models.sql`：19 个模型广场元数据补丁。

生产环境不要让应用启动时自动执行 `init.sql`。

## 14. 真实部署验收清单

### 后端

1. MySQL 初始化完成，`model_info`、`prediction_*`、`api_*`、`weather_data` 表存在。
2. `JWT_SECRET`、`MYSQL_*`、`MODEL_SERVICE_BASE_URL`、天气密钥已配置。
3. `/swagger-ui.html` 和 `/v3/api-docs` 可访问。
4. `/api/models` 能返回模型列表，且 `serviceModelName` 与 FastAPI 对齐。
5. `/api/open/usage/summary` 在无数据时也能返回 0 聚合，不报 500。

### 模型服务

1. `GET /health` 返回 ok。
2. `GET /model-api/models` 返回全部真实可用模型。
3. `POST /model-api/predict` 用 30 个点和 30 张图能返回 6 个点。
4. `GET /cloud-api/models` 返回 `SimVP_Cloud`。
5. `POST /cloud-api/predict` 用 10 张真实图能返回 10 张可展示 PNG。
6. GPU/CPU、CUDA、PyTorch、torchvision 版本和权重加载日志确认无误。

### 前端

1. 生产 Nginx 把 `/api/` 和 `/openapi/` 反向代理到后端。
2. `/models/use` 不再使用合成数值和占位 base64。
3. `/cloud-forecast` 上传真实 10 张图后能展示模型返回图。
4. `/api` 统计图表和调用日志能加载真实后端聚合数据。
5. `/weather` 对有经纬度电站显示真实天气，对缺经纬度电站显示明确错误。

## 15. 建议优先改造点

1. `ModelPrediction.vue` 解析真实 CSV/XLSX，并读取真实 30 张云图。
2. 后端 `PredictionRequest.NumericValue` 从单值扩展为真实 `power/temperature/irradiance`，或明确真实模型只依赖 `power`。
3. `model-service` 增加 `MODEL_PREDICTOR_MODE=mock|real` 环境变量，避免手改导入。
4. 云图接口增加真实 `cloudCoverage/confidence` 时，前后端 DTO 一起扩展。
5. 生产部署脚本中加入权重文件校验，避免占位权重导致线上启动失败。
6. 前端移除预测失败后的“演示结果”默认展示，避免用户把 mock 结果当成真实预测。

