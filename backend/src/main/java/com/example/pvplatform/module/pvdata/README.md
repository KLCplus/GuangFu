# 第二阶段：光伏数据与天气模块开发指导

本文用于指导 Agent 完成第二阶段开发，范围包括：

```text
module/pvdata
module/weather
persistence/entity/PvDataDO.java
persistence/entity/PvDataImportTaskDO.java
persistence/entity/WeatherDataDO.java
persistence/entity/FileResourceDO.java
persistence/mapper/PvDataMapper.java
persistence/mapper/PvDataImportTaskMapper.java
persistence/mapper/WeatherDataMapper.java
persistence/mapper/FileResourceMapper.java
resources/mapper
docs/back_front_api.md
```

目标是把“实时/历史基础查询 + 文件上传 mock + 天气 mock”升级为可支持页面展示和模型预测的数据服务。

## 1. 开发前现状

### 已完成

- `pv_data`、`pv_data_import_task`、`weather_data`、`file_resource` 表已建立。
- 四张表均已有 DO 和 BaseMapper。
- `GET /api/stations/{stationId}/realtime` 已查询数据库最新记录。
- `GET /api/stations/{stationId}/history` 已支持起止时间查询。
- `PvDataService.latestThirty()` 已为预测模块提供最新 30 条数据。
- `power_station` 已保存电站经纬度，可供天气查询。

### 当前临时实现和缺陷

- 文件上传接口只返回固定成功数量，没有读取文件。
- 没有创建和更新 `pv_data_import_task`。
- 没有保存上传文件到 `file_resource`。
- 历史查询没有限制最大时间范围，可能造成全表扫描。
- `interval` 参数未实现聚合。
- `latestThirty()` 只检查数量，不检查一分钟间隔和时间连续性。
- 天气接口全部返回 mock。
- `weather_data` 尚未实际读写。
- 没有第三方天气 Client、缓存、超时和降级。
- 时间格式由 Controller 手动解析，异常提示不够明确。

不要继续扩展 mock 数据，应逐步替换为真实数据库和天气 Provider。

## 2. 本阶段必须完成

1. 光伏数据查询参数校验和权限校验。
2. 实时数据与历史数据稳定查询。
3. `1min/5min/15min/1h` 时间粒度聚合。
4. CSV 文件导入。
5. XLSX 文件导入。
6. 导入任务状态、成功数、失败数和错误原因。
7. 数据批量写入与重复数据策略。
8. 预测输入的 30 分钟连续性校验。
9. 天气 Provider 抽象。
10. 第三方天气 API 接入、超时、缓存和落库。
11. 当前天气与预报接口。
12. 单元测试、Mapper 测试和接口测试。
13. 更新 `docs/back_front_api.md`。

## 3. 本阶段不做

- 真实模型推理。
- 预测任务异步队列。
- 大数据平台、时序数据库迁移。
- 服务器资源监控。
- 气象卫星图像和多模态图片处理。

## 4. 推荐目录

```text
module/
├── pvdata/
│   ├── controller/
│   │   └── PvDataController.java
│   ├── dto/
│   │   ├── PvDataHistoryQuery.java
│   │   └── PvDataImportRow.java
│   ├── service/
│   │   ├── PvDataService.java
│   │   ├── PvDataImportService.java
│   │   └── PvDataValidationService.java
│   ├── parser/
│   │   ├── PvDataFileParser.java
│   │   ├── CsvPvDataFileParser.java
│   │   └── ExcelPvDataFileParser.java
│   └── vo/
│       ├── PvRealtimeVO.java
│       ├── PvHistoryPointVO.java
│       ├── PvDataImportResultVO.java
│       └── PvDataImportTaskVO.java
└── weather/
    ├── controller/
    │   └── WeatherController.java
    ├── client/
    │   ├── WeatherProvider.java
    │   ├── WeatherClient.java
    │   └── dto/
    ├── config/
    │   └── WeatherProperties.java
    ├── service/
    │   └── WeatherService.java
    └── vo/
        ├── CurrentWeatherVO.java
        └── WeatherForecastVO.java
```

约束：

- Controller 不解析 CSV/Excel。
- Parser 不直接写数据库。
- Weather Client 不直接返回前端 VO。
- Service 负责编排校验、持久化、缓存和权限。
- 第三方响应 DTO 与系统 VO 分离。

## 5. 数据库知识

### 5.1 pv_data

核心唯一约束：

```text
(station_id, collect_time)
```

重要字段：

| 字段 | 单位 | 说明 |
|---|---|---|
| `power_kw` | kW | 实时功率 |
| `energy_today_kwh` | kWh | 当日发电量 |
| `energy_total_kwh` | kWh | 累计发电量 |
| `voltage_v` | V | 电压 |
| `current_a` | A | 电流 |
| `irradiance_w_m2` | W/m² | 辐照度 |
| `module_temperature_c` | ℃ | 组件温度 |
| `ambient_temperature_c` | ℃ | 环境温度 |
| `humidity_percent` | % | 湿度 |
| `wind_speed_m_s` | m/s | 风速 |
| `data_source` | - | `MANUAL/API/IMPORT/MOCK` |

规则：

- 数值字段使用 `BigDecimal` 入库。
- 查询必须带 `station_id`。
- 历史查询必须限制时间范围。
- 不允许为了导入方便先删除某电站全部历史数据。
- `raw_data` 只保存必要原始信息，不能无限复制整行大对象。

### 5.2 pv_data_import_task

状态：

```text
PENDING → PROCESSING → SUCCESS
                     ↘ FAILED
```

字段语义：

- `total_count`：文件中有效数据行数，不含表头。
- `success_count`：成功新增或按策略更新的行数。
- `fail_count`：格式、校验、重复或数据库失败行数。
- `error_message`：任务级错误摘要，不存无限长逐行错误。
- `finished_at`：成功或失败时必须写入。

建议逐行错误返回前端前限制最多 100 条，完整错误可生成文件。

### 5.3 weather_data

唯一约束：

```text
(station_id, weather_time, source)
```

规则：

- 当前天气和预报都可按时间点存入同一表。
- `source` 标识供应商，如 `AMAP`、`QWEATHER`、`MOCK`。
- 第三方原始响应可保存到 `raw_data`，但应限制大小。
- 查询优先返回未过期缓存，避免每次刷新页面都调用第三方。

### 5.4 file_resource

用于保存导入文件元数据，不保存文件二进制：

- `original_name`：用户上传文件名。
- `storage_name`：服务端生成的唯一文件名。
- `storage_path`：服务器内部路径。
- `file_url`：允许访问时的 URL。
- `business_type`：本阶段使用 `PV_DATA`。
- `checksum`：建议 SHA-256，用于审计和重复文件识别。

文件实际内容保存在受控目录或对象存储，禁止使用用户文件名直接拼接路径。

## 6. 光伏数据接口目标

### 6.1 实时数据

```http
GET /api/stations/{stationId}/realtime
```

业务规则：

1. 校验电站存在。
2. 校验当前用户有电站查看权限。
3. 查询最新一条记录。
4. 没有数据返回 404。
5. 可增加 `dataDelaySeconds` 判断数据是否过期。

### 6.2 历史数据

```http
GET /api/stations/{stationId}/history
    ?startTime=2026-07-06 00:00:00
    &endTime=2026-07-06 23:59:59
    &interval=5min
```

校验：

- `startTime < endTime`。
- 默认查询最近 24 小时。
- 普通查询最大范围建议 31 天。
- `interval` 只允许 `1min/5min/15min/1h`。
- 超大范围必须使用更粗粒度。

聚合建议：

| 字段 | 聚合方式 |
|---|---|
| 功率 | `AVG` |
| 辐照度 | `AVG` |
| 温度 | `AVG` |
| 电压、电流 | `AVG` |
| 累计发电量 | `MAX` |

不要在 Java 中先查询百万行再聚合。时间桶聚合应由 SQL 完成，并在 `resources/mapper/PvDataMapper.xml` 编写。

### 6.3 数据上传

```http
POST /api/stations/{stationId}/data/upload
Content-Type: multipart/form-data
```

表单：

```text
file: CSV 或 XLSX
duplicateStrategy: SKIP | UPDATE | FAIL
```

建议响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "importId": 1001,
    "status": "SUCCESS",
    "totalCount": 300,
    "successCount": 298,
    "failCount": 2,
    "errors": [
      {
        "row": 12,
        "message": "collectTime 格式错误"
      }
    ]
  }
}
```

### 6.4 查询导入任务

建议新增：

```http
GET /api/stations/{stationId}/data/imports/{importId}
```

用户只能查看自己创建且有权访问电站的任务，管理员可查看全部。

## 7. 导入文件规范

建议模板表头：

```text
collectTime,powerKw,energyTodayKwh,energyTotalKwh,voltageV,currentA,irradianceWM2,moduleTemperatureC,ambientTemperatureC,humidityPercent,windSpeedMS
```

最低必填字段：

```text
collectTime
powerKw
```

用于预测时还需要：

```text
ambientTemperatureC
irradianceWM2
```

逐行校验：

- 时间格式为 `yyyy-MM-dd HH:mm:ss`。
- 功率、发电量、辐照度不能为负。
- 湿度范围 `0～100`。
- 经纬度不在导入文件中，由电站提供。
- 同一文件内不允许重复时间点。
- 文件内 stationId 不可信，统一使用路径参数。
- 空单元格按字段是否必填处理，不能自动变成 0。

文件级校验：

- 文件不能为空。
- 扩展名与内容类型匹配。
- 限制最大文件大小。
- 限制最大数据行数。
- 防止公式注入和 Zip Bomb。
- 保存时生成随机 storageName，防止路径穿越。

## 8. 导入事务和批处理

推荐流程：

```text
验证用户和电站权限
    ↓
验证文件类型和大小
    ↓
保存 file_resource
    ↓
创建 PENDING 导入任务
    ↓
更新 PROCESSING
    ↓
解析并逐行校验
    ↓
分批写入 pv_data
    ↓
更新成功/失败数量
    ↓
更新 SUCCESS 或 FAILED
```

注意：

- 大文件不要使用一个超长事务。
- 建议每 500～1000 条一批。
- `BaseMapper.insert()` 循环不是高效批量插入，应使用 MyBatis Batch 或批量 SQL。
- 任务状态更新和数据批次事务要明确。
- 某一批失败时要记录错误，不能让任务永远停在 `PROCESSING`。
- `FAIL` 重复策略遇到重复时可终止整个任务。
- `SKIP` 跳过重复。
- `UPDATE` 只更新允许字段，不改变主键。

## 9. 预测输入连续性

当前 `latestThirty()` 只检查 30 条数量。应改为：

1. 按 `collect_time DESC LIMIT 30` 查询。
2. 反转为升序。
3. 必须恰好 30 条。
4. 相邻记录间隔必须为 1 分钟。
5. 最后一帧不能过旧，例如距当前超过 5 分钟。
6. `power/temperature/irradiance` 必须存在且合法。
7. 缺失时明确返回错误，不静默补 0。

示例校验：

```text
10:00
10:01
...
10:29
```

如果缺少 `10:15`，即使总数为 30 也不能用于模型预测。

## 10. 天气 Provider 设计

不要把供应商字段直接散落到 Service。定义统一接口：

```java
public interface WeatherProvider {
    CurrentWeatherResult getCurrent(double longitude, double latitude);
    List<WeatherForecastResult> getForecast(double longitude, double latitude);
    String source();
}
```

WeatherService 流程：

```text
校验电站和权限
    ↓
查询 power_station 经纬度
    ↓
查询 weather_data 缓存
    ↓ 有效
直接返回
    ↓ 失效
调用 WeatherProvider
    ↓
转换并 upsert weather_data
    ↓
返回系统 VO
```

配置：

```yaml
weather:
  provider: ${WEATHER_PROVIDER:AMAP}
  api-key: ${WEATHER_API_KEY}
  base-url: ${WEATHER_BASE_URL}
  connect-timeout: 3000
  response-timeout: 5000
  cache-minutes: 10
```

要求：

- API Key 不提供真实默认值。
- 不提交 `.env`。
- Client 配置连接和响应超时。
- 第三方错误转换为业务异常。
- 日志中不能打印完整 API Key。

## 11. 天气接口目标

### 当前天气

```http
GET /api/stations/{stationId}/weather/current
```

建议增加：

```json
{
  "stationId": 1,
  "weather": "晴",
  "temperature": 32.0,
  "humidity": 60.0,
  "windDirection": "东南",
  "windPower": "3级",
  "windSpeed": 2.5,
  "reportTime": "2026-07-06 10:30:00",
  "source": "AMAP",
  "cached": true
}
```

### 天气预报

```http
GET /api/stations/{stationId}/weather/forecast
```

预报对象必须明确日期、白天/夜间天气和温度。若供应商只提供逐小时数据，应在 Service 中转换，不要让前端适配供应商格式。

## 12. 权限要求

- 所有光伏和天气接口要求登录。
- 用户必须拥有对应电站 `VIEW` 或 `MANAGE` 权限。
- 管理员可访问全部电站。
- 上传数据要求 `MANAGE` 或 `ADMIN`。
- stationId 必须由后端检查，不能相信前端。
- 文件 URL 若含敏感数据，应通过鉴权下载，不直接暴露物理路径。

权限逻辑建议集中到：

```text
StationPermissionService.requireView(stationId)
StationPermissionService.requireManage(stationId)
```

不要在多个 Controller 重复写角色判断。

## 13. 异常规范

| 场景 | code | message 示例 |
|---|---:|---|
| 电站不存在 | 404 | 电站不存在 |
| 无电站权限 | 403 | 无权访问该电站 |
| 无实时数据 | 404 | 该电站暂无光伏数据 |
| 时间范围错误 | 400 | 开始时间必须早于结束时间 |
| interval 非法 | 400 | 不支持的时间粒度 |
| 文件格式错误 | 400 | 仅支持 CSV 和 XLSX |
| 输入不连续 | 400 | 预测输入数据时间不连续 |
| 天气 Key 未配置 | 500 | 天气服务未配置 |
| 第三方天气超时 | 502 | 天气服务暂不可用 |

不要把第三方响应、文件系统路径和 SQL 错误直接返回前端。

## 14. 测试要求

### PvDataService

- 最新数据按 collectTime 正确返回。
- 无数据返回 404。
- 历史查询按时间升序。
- 起止时间边界包含规则明确。
- 不同 stationId 不串数据。
- 30 条连续数据校验成功。
- 30 条但时间断档校验失败。
- 数值空值不被静默当成合法预测输入。

### 文件导入

- 正常 CSV 成功。
- 正常 XLSX 成功。
- 空文件失败。
- 错误表头失败。
- 非法时间、负功率、湿度越界逐行失败。
- 重复策略 `SKIP/UPDATE/FAIL` 正确。
- 文件过大和行数过多被拒绝。
- 导入异常后任务状态为 `FAILED`。
- 批量插入不会产生 N+1 查询。

### WeatherService

- 缓存有效时不调用第三方。
- 缓存失效时调用 Provider 并落库。
- 经纬度缺失返回明确错误。
- 第三方超时正确降级。
- API Key 不出现在日志。
- 不同供应商 DTO 正确转换为统一 VO。

### 权限

- 无登录返回 401。
- 无电站权限返回 403。
- VIEW 用户能查询但不能上传。
- MANAGE 用户可上传。
- ADMIN 可访问所有电站。

## 15. 实施顺序

### 第 1 步：查询规范化

- 新建查询 DTO。
- 统一时间解析。
- 增加范围和 interval 校验。
- 编写历史聚合 SQL。
- 补查询测试。

### 第 2 步：连续性校验

- 重构 `latestThirty()`。
- 严格校验 30 帧和一分钟间隔。
- 增加过期和空值检查。
- 与预测模块约定异常。

### 第 3 步：CSV 导入

- 定义文件模板。
- 实现 CSV Parser。
- 创建导入任务。
- 批量写库和错误统计。
- 完成重复策略。

### 第 4 步：Excel 与文件资源

- 实现 XLSX Parser。
- 保存 `file_resource`。
- 增加文件安全校验。
- 增加任务查询接口。

### 第 5 步：天气接入

- 定义 Provider。
- 配置 Weather Client。
- 实现当前天气与预报转换。
- 增加缓存、落库、超时和降级。

### 第 6 步：权限、测试和文档

- 接入电站权限。
- 完成测试。
- 更新 `docs/back_front_api.md`。
- 执行 `mvn clean test`。

## 16. 验收标准

- [ ] 实时和历史接口完全来自数据库。
- [ ] 历史查询限制范围并支持时间粒度。
- [ ] CSV 和 XLSX 均可导入。
- [ ] 导入任务状态和统计真实可靠。
- [ ] 重复数据策略明确且有测试。
- [ ] 文件名、路径和大小校验安全。
- [ ] 预测输入严格满足连续 30 帧。
- [ ] 当前天气和预报不再返回固定 mock。
- [ ] 天气 API Key 只来自环境变量。
- [ ] 天气响应有缓存和落库。
- [ ] 电站查看与管理权限生效。
- [ ] 全部测试通过。
- [ ] `docs/back_front_api.md` 已同步。

## 17. 交给 Agent 的任务模板

```text
请按照 backend/src/main/java/com/example/pvplatform/module/pvdata/README.md
完成第二阶段“光伏数据与天气”开发。

要求：
1. 先检查工作树，避开用户/JWT 等其他 Agent 的改动。
2. 按 README 的实施顺序开发，不一次重写所有模块。
3. 使用现有 pv_data、pv_data_import_task、weather_data、file_resource 表。
4. 实现历史聚合、连续性校验、CSV/XLSX 导入和天气 Provider。
5. 天气 API Key 必须使用环境变量。
6. 增加电站权限校验。
7. 补 Service、Mapper、文件解析、天气 Client 和权限测试。
8. 更新 docs/back_front_api.md。
9. 执行 mvn clean test，并报告修改文件、接口、测试和剩余限制。
```
