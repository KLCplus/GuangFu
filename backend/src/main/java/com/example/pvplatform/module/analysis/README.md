# 第四阶段：分析报告、开放平台与新闻通知开发指导

本文用于指导 Agent 完成第四阶段，范围包括：

```text
module/analysis
module/openapi
module/news
persistence/entity/AnalysisReportDO.java
persistence/entity/ApiKeyDO.java
persistence/entity/ApiCallLogDO.java
persistence/entity/NewsDO.java
persistence/entity/UserNotificationDO.java
persistence/mapper/AnalysisReportMapper.java
persistence/mapper/ApiKeyMapper.java
persistence/mapper/ApiCallLogMapper.java
persistence/mapper/NewsMapper.java
persistence/mapper/UserNotificationMapper.java
docs/back_front_api.md
docs/module_back_api.md
```

本阶段依赖前面阶段提供真实用户、电站、光伏数据、天气和预测结果。不要用新 mock 掩盖上游缺失。

## 1. 当前现状

### 已完成

- 五张业务表及 DO、Mapper 已建立。
- `POST /api/analysis/report` 路由存在。
- API Key 申请、日志查询和开放预测路由存在。
- 开放预测可以转发 FastAPI。
- 新闻列表、详情、创建、修改和逻辑删除已接数据库。

### 当前临时实现和问题

- 分析报告内容固定，不查询业务数据，也不写 `analysis_report`。
- API Key 是固定 mock 字符串。
- API Key 没有生成、哈希、验证、过期、禁用和额度。
- `/openapi/v1/predict` 当前不强制校验 `X-API-KEY`。
- 调用日志为 mock，没有写 `api_call_log`。
- 开放预测没有输入长度、模型状态和配额校验。
- 新闻创建直接发布，没有草稿和下线流程。
- 新闻列表没有分页、类型和目标角色过滤。
- 没有 `user_notification` 业务。
- 管理接口依赖前一阶段 ADMIN 权限完成。

## 2. 本阶段必须完成

### 分析报告

1. 读取真实电站、历史光伏数据、天气和预测结果。
2. 生成结构化模板报告。
3. 写入 `analysis_report`。
4. 查询报告详情和历史。
5. 用户和电站权限隔离。

### 开放平台

1. 安全生成 API Key。
2. 只保存 Key 哈希和前缀。
3. API Key 鉴权过滤器。
4. 状态、过期时间、每分钟限流和每日额度。
5. 开放预测调用业务后端预测逻辑。
6. 所有调用写 `api_call_log`。
7. 用户查看自己的 Key 和调用日志。
8. 管理员启用/禁用 Key。

### 新闻通知

1. 新闻草稿、发布、下线和逻辑删除。
2. 新闻分页、类型和目标角色过滤。
3. 用户站内通知列表、未读数、已读。
4. 发布新闻时按策略生成通知。
5. 管理端权限和输入校验。

## 3. 本阶段不做

- 大语言模型自动写报告。
- 复杂模型微调。
- 短信、邮件、微信模板消息真实发送。
- 计费支付。
- 公网网关和商业 SLA。
- 服务器资源监控。

报告第一版使用可解释的模板规则，不调用未知外部 AI 服务。

## 4. 推荐目录

```text
module/
├── analysis/
│   ├── controller/AnalysisController.java
│   ├── dto/AnalysisRequest.java
│   ├── service/
│   │   ├── AnalysisService.java
│   │   ├── AnalysisDataService.java
│   │   └── ReportTemplateService.java
│   └── vo/
│       ├── AnalysisReportVO.java
│       └── AnalysisReportListItemVO.java
├── openapi/
│   ├── controller/
│   │   ├── OpenApiController.java
│   │   └── ApiKeyController.java
│   ├── dto/
│   ├── service/
│   │   ├── ApiKeyService.java
│   │   ├── ApiQuotaService.java
│   │   ├── ApiCallLogService.java
│   │   └── OpenPredictionService.java
│   ├── security/
│   │   ├── ApiKeyAuthenticationFilter.java
│   │   └── ApiKeyPrincipal.java
│   └── vo/
└── news/
    ├── controller/
    │   ├── NewsController.java
    │   └── NotificationController.java
    ├── dto/
    ├── service/
    │   ├── NewsService.java
    │   └── NotificationService.java
    └── vo/
```

开放平台的 API Key 认证与普通 JWT 认证分开，不要将 API Key 当成用户 JWT。

## 5. 综合分析报告

### 5.1 数据来源

报告至少使用：

| 数据 | 来源 |
|---|---|
| 电站信息 | `power_station` |
| 最近功率趋势 | `pv_data` |
| 当前/预报天气 | `weather_data` |
| 预测任务 | `prediction_task` |
| 未来预测点 | `prediction_result` |
| 报告创建人 | SecurityContext |

用户传入的 `stationId` 和 `taskId` 不能直接信任，必须验证：

- 电站存在。
- 用户有电站权限。
- taskId 属于当前用户或用户有权访问。
- 预测任务属于同一 stationId。
- 任务状态为 SUCCESS。

### 5.2 模板规则

第一版建议计算：

- 最近 30 分钟平均功率。
- 最大、最小功率。
- 首末功率变化率。
- 波动程度。
- 辐照度与功率趋势。
- 未来 30 分钟预测增减趋势。
- 当前天气影响。
- 缺失数据和异常值提示。

示例规则：

```text
预测末值比首值上升超过 5%
→ 未来 30 分钟功率呈上升趋势

预测末值比首值下降超过 5%
→ 未来 30 分钟功率可能下降，请关注天气和设备状态

连续数据断档
→ 数据完整性不足，本报告结论可信度降低

阴雨且辐照度低
→ 天气条件可能限制光伏输出
```

阈值集中配置，不要散落硬编码。

### 5.3 报告持久化

`analysis_report`：

- `title`：后端生成或校验用户输入。
- `summary`：综合摘要。
- `weather_analysis`：天气部分。
- `prediction_analysis`：预测部分。
- `abnormal_analysis`：异常和数据质量。
- `suggestion`：运维建议。
- `report_content`：完整可展示文本。
- `report_json`：结构化指标。

生成成功后再写入完整报告。若生成过程失败，不要写入半成品记录。

### 5.4 目标接口

```http
POST /api/analysis/report
GET  /api/analysis/reports?pageNum=1&pageSize=10&stationId=1
GET  /api/analysis/reports/{reportId}
```

历史和详情只允许报告创建者或管理员访问。

## 6. API Key 安全设计

### 6.1 Key 格式

建议：

```text
pv_<publicPrefix>_<randomSecret>
```

例如：

```text
pv_a1b2c3d4_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

生成要求：

- 使用 `SecureRandom`。
- Secret 至少 256 bit 随机熵。
- 不使用 UUID 截断作为全部密钥。
- 完整 Key 只在创建时返回一次。
- 数据库只保存 `api_key_prefix` 和 `api_key_hash`。
- 日志不打印完整 Key。

高熵随机 API Key 可以使用 SHA-256 哈希后查询。不要把明文 Key 加密后存库作为默认方案。

### 6.2 api_key 字段

| 字段 | 规则 |
|---|---|
| `key_name` | 用户可识别名称 |
| `api_key_prefix` | 展示和定位 |
| `api_key_hash` | 唯一哈希 |
| `status` | `ACTIVE/DISABLED/EXPIRED` |
| `rate_limit_per_minute` | 每分钟额度 |
| `daily_quota` | 每日额度 |
| `expire_time` | 可为空 |
| `last_used_at` | 成功认证后更新 |

Key 用户只能操作自己的记录；管理员可禁用全部用户的 Key。

### 6.3 申请接口

```http
POST /api/open/apply-key
Authorization: Bearer <jwt>
```

建议请求：

```json
{
  "keyName": "毕业设计联调",
  "expireDays": 90
}
```

成功响应只出现一次完整 Key：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "apiKeyId": 1,
    "apiKey": "pv_a1b2c3d4_xxx",
    "apiKeyPrefix": "pv_a1b2c3d4",
    "expireTime": "2026-10-04 10:00:00"
  }
}
```

后续查询 Key 列表不能再次返回完整 Key。

## 7. API Key 鉴权

`ApiKeyAuthenticationFilter` 只处理：

```text
/openapi/**
```

流程：

```text
读取 X-API-KEY
    ↓
格式校验并计算哈希
    ↓
查询 api_key
    ↓
校验 ACTIVE
    ↓
校验 expire_time
    ↓
检查每分钟限流和每日额度
    ↓
设置 ApiKeyPrincipal
    ↓
继续请求
```

不要将开放接口设置为真正无鉴权。Spring Security 可允许请求进入 API Key Filter，但 Filter 必须强制认证。

统一失败：

| 场景 | HTTP/业务码 | message |
|---|---:|---|
| 缺少 Key | 401 | 缺少 API Key |
| Key 无效 | 401 | API Key 无效 |
| Key 禁用/过期 | 403 | API Key 已禁用或过期 |
| 每分钟超限 | 429 | 请求过于频繁 |
| 每日额度耗尽 | 429 | 今日调用额度已用完 |

不要向攻击者区分“不存在”和“哈希不匹配”的内部细节。

## 8. 限流与额度

单机课程项目可先使用 Caffeine 或内存计数，但必须说明：

- 服务重启计数丢失。
- 多实例计数不一致。

更可靠方案使用 Redis：

```text
rate:{apiKeyId}:{yyyyMMddHHmm}
quota:{apiKeyId}:{yyyyMMdd}
```

使用原子自增并设置过期时间。

数据库的 `api_call_log` 不适合每次请求实时 COUNT 作为限流器，数据量大时性能差。

## 9. 开放预测

```http
POST /openapi/v1/predict
X-API-KEY: <key>
```

流程：

1. API Key 鉴权和额度检查。
2. 校验 modelName 对应 ONLINE 模型。
3. 校验 30 帧输入和时间连续性。
4. 创建 `OPEN_API` 模式预测任务。
5. 调用统一 PredictionExecutionService。
6. 保存结果。
7. 写调用日志。
8. 返回统一结果。

开放接口不能绕过业务层直接调用 `ModelServiceClient`。当前 `OpenApiService` 的直接转发方式需要重构，否则不会产生任务、结果和审计记录。

## 10. API 调用日志

`api_call_log` 每次开放接口都写入，无论成功失败。

记录：

- userId/apiKeyId/modelId。
- requestPath/method/IP。
- requestTime/responseTime/costTime。
- HTTP 状态和业务状态。
- 安全的错误摘要。
- 脱敏请求、响应摘要。

不记录：

- 完整 API Key。
- 完整 30 帧敏感输入。
- 密码、JWT。
- 内部堆栈。

建议在 `finally` 或专用拦截器中记录，避免异常路径漏日志。

日志写入失败不应覆盖原业务响应，但必须有服务端错误日志和监控。

## 11. 开放平台接口目标

```http
POST   /api/open/apply-key
GET    /api/open/keys
PUT    /api/open/keys/{apiKeyId}/status
DELETE /api/open/keys/{apiKeyId}
GET    /api/open/call-logs?pageNum=1&pageSize=10
POST   /openapi/v1/predict
```

管理员可增加：

```http
GET /api/admin/api-keys
PUT /api/admin/api-keys/{apiKeyId}/status
GET /api/admin/api-call-logs
```

## 12. 新闻状态机

```text
DRAFT → PUBLISHED → OFFLINE
  ↓          ↓
逻辑删除   逻辑删除
```

规则：

- 新建默认 DRAFT。
- 发布时写 `published_at`。
- 已发布内容修改后可直接更新或重新审核，项目必须统一选择。
- OFFLINE 新闻不在普通用户列表展示。
- 删除使用逻辑删除。
- 普通用户只能看到与自己角色匹配的 PUBLISHED 新闻。

当前直接创建 PUBLISHED 的实现应改为显式发布流程。

## 13. 新闻接口目标

用户：

```http
GET /api/news?pageNum=1&pageSize=10&type=NOTICE
GET /api/news/{newsId}
```

管理员：

```http
POST   /api/admin/news
PUT    /api/admin/news/{newsId}
PUT    /api/admin/news/{newsId}/publish
PUT    /api/admin/news/{newsId}/offline
DELETE /api/admin/news/{newsId}
GET    /api/admin/news?pageNum=1&pageSize=10&status=DRAFT
```

新增请求建议：

```json
{
  "title": "模型更新通知",
  "summary": "新增 LSTM v2",
  "content": "详细内容",
  "coverUrl": "https://example.com/cover.png",
  "newsType": "MODEL_UPDATE",
  "targetRole": "ALL"
}
```

允许类型：

```text
NEWS
NOTICE
MODEL_UPDATE
ALERT
```

允许目标：

```text
ALL
USER
ADMIN
API_USER
```

使用枚举或白名单校验，不能接受任意字符串。

## 14. 用户站内通知

目标接口：

```http
GET /api/notifications?pageNum=1&pageSize=10&readStatus=0
GET /api/notifications/unread-count
PUT /api/notifications/{notificationId}/read
PUT /api/notifications/read-all
```

规则：

- 只能查询和修改自己的通知。
- 单条已读更新条件必须包含 notificationId 和 currentUserId。
- `read_status` 从 0 改为 1 时写 `read_time`。
- 重复标记已读应幂等。

### 新闻发布与通知

课程项目可选择：

1. 发布时按角色批量生成用户通知；
2. 新闻列表直接按角色查询，仅对重要公告生成通知。

不建议为每篇普通新闻给所有用户生成大量通知。建议只对 `NOTICE/ALERT/MODEL_UPDATE` 生成。

批量生成必须使用批处理，不能 N 次单条插入。

## 15. 报告、Key、新闻权限

| 功能 | USER | API_USER | ADMIN |
|---|---|---|---|
| 生成自己的报告 | 是 | 是 | 是 |
| 查看自己的报告 | 是 | 是 | 是 |
| 查看全部报告 | 否 | 否 | 是 |
| 申请自己的 API Key | 可按产品规则 | 是 | 是 |
| 查看自己的调用日志 | 是 | 是 | 是 |
| 禁用任意 Key | 否 | 否 | 是 |
| 查看已发布新闻 | 是 | 是 | 是 |
| 新闻管理 | 否 | 否 | 是 |
| 查看自己的通知 | 是 | 是 | 是 |

所有 userId 从认证上下文获得，不能接受前端指定。

## 16. 事务边界

需要事务：

- 报告生成结果和报告记录保存。
- 修改新闻状态并生成通知。
- 修改 API Key 状态和相关业务记录。
- 开放预测创建任务、快照和结果应复用第三阶段事务策略。

外部模型调用不放在长数据库事务中。

调用日志建议独立保存，必要时使用新事务，确保失败调用也有记录。

## 17. 异常规范

| 场景 | code | message |
|---|---:|---|
| 报告数据不足 | 400 | 数据不足，无法生成报告 |
| 预测任务未成功 | 400 | 预测任务尚未成功 |
| 报告不存在/无权访问 | 404 | 报告不存在 |
| API Key 缺失或无效 | 401 | API Key 无效 |
| API Key 禁用或过期 | 403 | API Key 已禁用或过期 |
| 限流/额度 | 429 | 请求过于频繁 |
| 新闻不存在 | 404 | 新闻不存在 |
| 非法新闻状态转换 | 400 | 新闻状态不允许该操作 |
| 模型服务异常 | 502 | 模型服务暂不可用 |

全局异常系统需要支持 429 业务码和合适 HTTP 状态。

## 18. 测试要求

### Analysis

- 数据完整时生成正确结构并入库。
- 电站、任务不匹配时失败。
- 任务未 SUCCESS 时失败。
- 无天气时报告能明确降级。
- 无权限访问报告返回 404/403。
- 模板阈值边界测试。

### ApiKey

- 生成 Key 随机且唯一。
- 数据库不出现完整 Key。
- 正确 Key 鉴权成功。
- 错误、禁用、过期 Key 失败。
- 每分钟限流和每日额度生效。
- 用户不能操作他人 Key。
- 日志和异常不包含完整 Key。

### Open API

- 成功预测产生任务、结果和调用日志。
- 输入错误也记录失败日志。
- 模型服务异常记录 FAILED。
- 配额耗尽不调用模型。
- 30 帧和模型 ONLINE 校验生效。

### News

- 新建默认 DRAFT。
- 发布和下线状态转换正确。
- 普通用户只看到 PUBLISHED 且角色匹配内容。
- 逻辑删除后不可见。
- 管理员接口权限正确。

### Notification

- 只查询当前用户通知。
- 单条已读不能修改他人通知。
- read-all 只更新当前用户。
- 未读数正确。
- 重复标记幂等。

## 19. 实施顺序

### 第 1 步：新闻状态和分页

- DTO/VO 完整化。
- 草稿、发布、下线。
- 用户和管理员分页。
- 类型、角色和权限过滤。

### 第 2 步：站内通知

- 通知列表、未读数、单条和全部已读。
- 新闻发布通知策略。
- 批量插入和权限测试。

### 第 3 步：分析报告

- 聚合上游数据。
- 实现模板规则。
- 报告持久化。
- 历史和详情。

### 第 4 步：API Key

- 安全生成与哈希。
- Key 列表、禁用、删除。
- API Key Filter。
- 过期和状态校验。

### 第 5 步：限流与开放预测

- 每分钟和每日额度。
- 复用统一预测服务。
- 调用日志。
- 异常路径审计。

### 第 6 步：测试和文档

- 完成全部测试。
- 更新 `docs/back_front_api.md`。
- 必要时更新 `docs/module_back_api.md`。
- 执行 `mvn clean test`。

## 20. 验收标准

- [ ] 分析报告来自真实数据并写入数据库。
- [ ] 报告历史和详情按用户隔离。
- [ ] API Key 使用安全随机数生成。
- [ ] 数据库不保存或返回完整 API Key。
- [ ] `/openapi/v1/**` 强制 API Key 鉴权。
- [ ] 状态、过期、限流和每日额度生效。
- [ ] 开放预测复用预测任务主流程。
- [ ] 成功和失败调用均有日志。
- [ ] 新闻支持 DRAFT/PUBLISHED/OFFLINE。
- [ ] 新闻列表分页并按角色过滤。
- [ ] 站内通知、未读数和已读功能完成。
- [ ] 用户不能访问他人的报告、Key、日志或通知。
- [ ] 测试通过且不泄露密钥。
- [ ] 接口文档与代码一致。

## 21. 交给 Agent 的任务模板

```text
请按照 backend/src/main/java/com/example/pvplatform/module/analysis/README.md
完成第四阶段“分析报告、开放平台与新闻通知”开发。

要求：
1. 先检查工作树，避开其他 Agent 的用户、数据和预测模块改动。
2. 按 README 顺序开发，先新闻/通知，再报告，再开放平台。
3. 报告必须使用真实数据库数据，不新增固定 mock。
4. API Key 只保存哈希和前缀，完整 Key 只返回一次。
5. /openapi/v1/** 必须强制 API Key、限流和额度。
6. 开放预测复用第三阶段统一预测流程，不能直接裸调 ModelServiceClient。
7. 新闻实现草稿、发布、下线和角色过滤。
8. 补报告、Key、开放预测、新闻、通知和权限测试。
9. 更新 docs/back_front_api.md，必要时更新 docs/module_back_api.md。
10. 执行 mvn clean test，并报告改动、接口、测试和剩余限制。
```
