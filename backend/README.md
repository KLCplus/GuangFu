# 光伏发电综合分析与预测系统后端

本目录是项目的统一业务后端。前端 Web 和小程序只调用本服务；本服务负责权限、业务数据、预测任务和统一响应，并通过 HTTP 调用 FastAPI 模型服务。

当前阶段应按模块逐步完成，不建议多人同时修改同一个 Service。每个模块至少完成“数据库映射 → Service 业务规则 → Controller 接口 → 测试 → 接口文档”后再进入联调。

## 1. 技术栈

- Java 17
- Spring Boot 3.3
- Spring Web
- Spring Security（JWT 尚未接入）
- MyBatis Plus 3.5
- MySQL 8
- WebClient
- Springdoc OpenAPI / Swagger UI
- Maven
- Lombok

## 2. 系统边界

```text
Vue Web / 小程序
        │
        │ /api、/openapi
        ▼
Spring Boot 后端
        │
        │ POST /model-api/predict
        ▼
FastAPI 模型服务
```

- 前端不能直接访问 FastAPI。
- 前端不能直接连接 MySQL。
- FastAPI 不处理用户、电站、权限、API Key 和任务记录。
- Spring Boot 是业务数据和权限的唯一入口。

接口文档分为两份：

- [前端—Spring Boot 接口](../docs/back_front_api.md)
- [Spring Boot—模型服务接口](../docs/module_back_api.md)

## 3. 目录分类

```text
backend/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/example/pvplatform/
    │   ├── PvPlatformApplication.java       # 启动类
    │   ├── common/
    │   │   ├── Result.java                  # 统一响应
    │   │   ├── PageResult.java              # 分页结构
    │   │   └── exception/                   # 业务异常和全局异常处理
    │   ├── config/
    │   │   ├── DatabaseConfig.java          # Mapper 扫描
    │   │   ├── SecurityConfig.java          # 权限配置/JWT 接入点
    │   │   ├── SwaggerConfig.java           # OpenAPI 文档
    │   │   └── WebClientConfig.java         # 模型服务 HTTP 客户端
    │   ├── client/
    │   │   ├── ModelServiceClient.java      # 调用 FastAPI
    │   │   ├── dto/                         # 模型服务请求
    │   │   └── vo/                          # 模型服务响应
    │   ├── module/                          # 业务模块
    │   │   ├── auth/
    │   │   ├── user/
    │   │   ├── station/
    │   │   ├── pvdata/
    │   │   ├── weather/
    │   │   ├── model/
    │   │   ├── prediction/
    │   │   ├── analysis/
    │   │   ├── openapi/
    │   │   └── news/
    │   └── persistence/
    │       ├── entity/                      # 22 张表对应的 DO
    │       └── mapper/                      # 22 个 BaseMapper
    └── resources/
        ├── application.yml                  # 服务、MySQL、MyBatis 配置
        ├── mapper/                          # 复杂 SQL 的 XML 位置
        └── sql/init.sql                     # 完整数据库初始化脚本
```

### 业务模块内部结构

```text
module/station/
├── controller/     # 接收 HTTP 参数，不写复杂业务
├── service/        # 权限、校验、事务和业务流程
├── dto/            # 前端请求对象
├── vo/             # 返回前端的对象
└── entity/         # 业务领域对象；数据库对象统一放 persistence/entity
```

约束：

1. Controller 不直接调用 Mapper。
2. DO 只表示数据库记录，不能直接承载密码明文、Token 等接口字段。
3. DTO 和 VO 不应复用 DO，避免数据库字段泄露给前端。
4. 跨模块调用优先调用对方 Service，不直接调用对方 Mapper。
5. 简单单表 CRUD 使用 `BaseMapper`；多表、统计和性能敏感 SQL 放 `resources/mapper`。

## 4. 模块功能与当前状态

| 模块 | 主要职责 | 关联表 | 当前状态 | 下一步 |
|---|---|---|---|---|
| `auth` | 注册、登录、密码校验、Token | `sys_user`、`sys_role`、`sys_user_role` | 注册和密码校验已接数据库；返回 mock token | JWT 签发、刷新、退出、登录限流 |
| `user` | 用户资料、用户管理、角色 | 用户与角色相关表 | profile 已查数据库 | 从 SecurityContext 获取当前用户；管理员 CRUD |
| `station` | 电站增删改查、用户电站权限 | `power_station`、`user_station_permission` | CRUD 已接数据库 | 分页、条件查询、权限过滤 |
| `pvdata` | 实时数据、历史曲线、文件导入 | `pv_data`、`pv_data_import_task` | 实时/历史查询已接数据库；上传仍为 mock | CSV/Excel 解析、批量插入、导入任务 |
| `weather` | 当前天气、预报、第三方天气接入 | `weather_data` | 接口仍返回 mock | 调天气 API、缓存并落库 |
| `model` | 模型元数据、模型状态、指标和文件 | `model_info`、`model_metric`、`model_file` | 模型 CRUD 已接数据库 | 参数校验、指标和文件管理 |
| `prediction` | 创建任务、输入快照、结果、历史 | 三张 prediction 表 | 主链路已接数据库和 FastAPI | 异步队列、失败重试、任务权限 |
| `analysis` | 综合分析报告 | `analysis_report` | 当前为模板 mock | 汇总真实数据并持久化报告 |
| `openapi` | API Key、外部预测、调用日志 | `api_key`、`api_call_log` | 外部预测可转发；Key/日志仍为 mock | Key 哈希、鉴权、限流、日志落库 |
| `news` | 新闻、公告、站内通知 | `news`、`user_notification` | 新闻 CRUD 已接数据库 | 分页、草稿发布流程、用户通知 |

未实现且不在计划内：服务器 CPU、GPU、内存监控。

## 5. 推荐开发顺序

### 第一阶段：身份和基础数据

1. 完成 JWT 登录、鉴权过滤器和 SecurityContext。
2. 完成用户管理和角色授权。
3. 完成电站分页、条件查询和用户电站权限。
4. 为以上功能补 Service 单元测试和 Controller 集成测试。

完成标准：

- 未登录访问受保护接口返回 401。
- 普通用户访问 `/api/admin/**` 返回 403。
- 管理员可以管理用户和电站。
- 前端不再依赖 mock token。

### 第二阶段：光伏数据和天气

1. 设计 CSV/Excel 导入模板。
2. 解析文件并写入 `pv_data`，记录 `pv_data_import_task`。
3. 校验 `(station_id, collect_time)` 重复和时间连续性。
4. 接入天气 API，按电站经纬度查询并缓存到 `weather_data`。
5. 完成实时卡片和历史曲线接口联调。

完成标准：

- 可导入数据并给出成功、失败数量及错误原因。
- 可按电站和时间范围查询。
- 预测前能取得连续 30 分钟、每分钟一帧的数据。

### 第三阶段：模型和预测

1. 完善模型上下线、版本、输入输出配置。
2. 校验模型必须为 `ONLINE`。
3. 创建任务并保存 `prediction_input_snapshot`。
4. 调用 FastAPI，保存 6 条 `prediction_result`。
5. 对超时、返回空数据、模型异常进行状态回写。
6. 后续再将同步流程升级为消息队列异步流程。

完成标准：

- 任务状态只使用 `PENDING/RUNNING/SUCCESS/FAILED`。
- 每次请求可追踪 taskId、输入、结果、耗时和错误。
- 模型服务不可用时任务必须变为 `FAILED`。

### 第四阶段：报告、开放平台和通知

1. 根据历史数据、天气和预测结果生成报告并入库。
2. API Key 只保存哈希和展示前缀。
3. 增加每分钟限流、每日额度和调用日志。
4. 完成新闻草稿、发布、下线和站内通知。

## 6. 数据库知识和规范

### 6.1 初始化

本地数据库：

```text
host: localhost
port: 3306
database: pv_platform
username: root
password: 通过 MYSQL_PASSWORD 环境变量设置
```

首次初始化：

```bash
mysql -h localhost -P 3306 -u root -p < src/main/resources/sql/init.sql
```

注意：`init.sql` 包含 `DROP TABLE`，会删除现有数据。应用配置为：

```yaml
spring:
  sql:
    init:
      mode: never
```

因此脚本只能人工执行，禁止改成每次启动自动执行。

生产和共享环境通过环境变量配置：

```text
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
```

### 6.2 表与模块关系

| 分类 | 数据表 |
|---|---|
| 用户权限 | `sys_user`、`sys_role`、`sys_user_role`、`sys_oauth_account`、`sys_face_auth` |
| 电站 | `power_station`、`user_station_permission` |
| 光伏和天气 | `pv_data`、`pv_data_import_task`、`weather_data` |
| 模型 | `model_info`、`model_metric`、`model_file` |
| 预测 | `prediction_task`、`prediction_input_snapshot`、`prediction_result` |
| 分析 | `analysis_report` |
| 开放平台 | `api_key`、`api_call_log` |
| 文件 | `file_resource` |
| 新闻通知 | `news`、`user_notification` |

### 6.3 MyBatis Plus 用法

简单查询：

```java
List<ModelInfoDO> models = modelInfoMapper.selectList(
    Wrappers.<ModelInfoDO>lambdaQuery()
        .eq(ModelInfoDO::getStatus, "ONLINE")
        .orderByAsc(ModelInfoDO::getModelId)
);
```

按主键新增：

```java
PowerStationDO station = new PowerStationDO();
station.setStationCode("PV-001");
station.setStationName("示例电站");
stationMapper.insert(station);
Long stationId = station.getStationId();
```

复杂关联查询可使用 Mapper 注解或 XML。项目中角色查询是现有示例：

```java
List<String> selectRoleCodesByUserId(Long userId);
```

### 6.4 字段和事务原则

- Java 使用驼峰字段，MyBatis 开启下划线转驼峰。
- 金额、功率、辐照度等精确值使用 `BigDecimal`。
- 数据库时间使用 `LocalDateTime`，日期使用 `LocalDate`。
- JSON 列当前用 `String` 保存合法 JSON；复杂场景应增加 TypeHandler。
- `sys_user`、`power_station`、`model_info`、`news` 使用逻辑删除。
- 注册、任务创建、批量导入等多表写操作必须考虑 `@Transactional`。
- 不允许保存密码明文；当前注册使用 BCrypt。
- 不允许保存 API Key 明文，只保存哈希和前缀。
- 高频表 `pv_data` 查询必须带 `station_id` 和时间范围，避免全表扫描。

## 7. 接口统一约定

统一响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

业务码建议：

| code | 含义 |
|---:|---|
| 200 | 成功 |
| 400 | 参数或业务条件不满足 |
| 401 | 未登录、Token 无效或登录失败 |
| 403 | 无权限 |
| 404 | 数据不存在 |
| 500 | 后端异常 |
| 502 | 模型服务调用失败 |

时间格式统一使用：

```text
yyyy-MM-dd HH:mm:ss
```

接口详细定义见：

- [docs/back_front_api.md](../docs/back_front_api.md)
- [docs/module_back_api.md](../docs/module_back_api.md)

## 8. 启动与验证

前置条件：

- JDK 17
- Maven 3.8+
- MySQL 8
- 创建预测任务时需启动 FastAPI 模型服务

启动后端：

```bash
mvn spring-boot:run
```

地址：

- 后端：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

编译测试：

```bash
mvn clean test
```

模型服务地址：

```yaml
model-service:
  base-url: ${MODEL_SERVICE_BASE_URL:http://localhost:9000}
```

## 9. 分支和提交建议

按模块建立分支，例如：

```text
feature/auth-jwt
feature/station-management
feature/pv-data-import
feature/weather-integration
feature/prediction-task
feature/open-api
```

每个提交只处理一个模块或一个明确问题。提交前至少完成：

1. `mvn clean test` 通过。
2. 接口请求、响应已同步到文档。
3. 没有提交密码、第三方 API Key、模型文件或导入数据。
4. 数据库变更同步追加迁移脚本，不能只修改本机数据库。
