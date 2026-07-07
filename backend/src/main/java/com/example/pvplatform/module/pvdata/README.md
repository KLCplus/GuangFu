# pvdata 模块实现现状

## 模块职责

`pvdata` 负责光伏采集数据查询、历史曲线查询、预测输入数据提取，以及 CSV/Excel 文件导入。

## 已实现

- 实时数据接口：返回指定电站最近一条 `pv_data`。
- 历史数据接口：支持 `startTime`、`endTime`、`interval` 查询参数。
- 历史查询会校验时间范围，并限制分页式大范围误用；目前返回 VO 包含时间、功率、辐照度、温度。
- `latestThirty` 为预测模块提供最近 30 条输入数据。
- 文件上传导入已实现，不再是 mock：支持 CSV、XLS、XLSX。
- 导入文件先落本地目录并记录 `file_resource`。
- 导入任务写入 `pv_data_import_task`，包含总数、成功数、失败数、状态、错误信息、完成时间。
- 导入行校验、重复时间点处理、批量插入/更新已实现。
- 支持重复策略解析，具体行为由 `DuplicateStrategy` 和导入服务控制。
- 电站访问通过 `StationPermissionService.requireView` 校验。

## 未实现或限制

- 文件导入是同步处理，大文件会占用请求线程。
- 导入错误明细主要压缩进任务错误信息，没有独立错误明细表。
- `interval` 查询当前主要按采样间隔筛选/处理，未形成数据库侧聚合统计能力。
- 没有手工新增/修改/删除单条采集数据的接口。
- 上传文件存储是本地目录，未实现对象存储、病毒扫描、文件生命周期清理。
- `latestThirty` 只取最近 30 条，不严格保证每分钟连续 30 分钟。

## 主要接口

- `GET /api/stations/{stationId}/realtime`
- `GET /api/stations/{stationId}/history`
- `POST /api/stations/{stationId}/data/upload`
- `GET /api/stations/{stationId}/data/imports/{importId}`

## 相关表

- `pv_data`
- `pv_data_import_task`
- `file_resource`
- 依赖 `power_station` 和 `user_station_permission`。

## 测试情况

- 已有 `PvDataServiceTest`、`PvDataImportServiceTest`、`PvDataHistoryQueryTest`、`PvDataFileParserTest`。
- 建议补充大文件/超行数、重复策略全路径、导入事务失败、非授权电站上传、历史 interval 聚合预期测试。
