# station 模块实现现状

## 模块职责

`station` 负责光伏电站基础信息管理，以及其他业务模块使用的电站访问权限校验。

## 已实现

- 电站列表、详情、创建、更新、逻辑删除。
- 后台新增、更新、删除接口已放在 `/api/admin/stations` 下。
- `StationPermissionService` 提供 `requireView` 和 `requireManage`，供光伏数据、天气、预测、分析等模块复用。
- 管理员可访问全部电站；普通用户需要 `user_station_permission` 中具备 `VIEW` 或 `MANAGE` 权限。
- 查询时排除 `deleted=1` 的电站。

## 未实现或限制

- 电站列表当前没有分页、条件查询和按用户权限过滤；普通用户调用列表可能看到全部未删除电站。
- 创建/更新缺少完整字段校验，例如经纬度范围、容量范围、状态枚举、编码唯一性友好错误。
- 仅有权限校验服务，没有管理用户和电站权限关系的接口。
- `StationVO` 字段较少，列表/详情返回的领域对象和 VO 结构还不统一。

## 主要接口

- `GET /api/stations`
- `GET /api/stations/{stationId}`
- `POST /api/admin/stations`
- `PUT /api/admin/stations/{stationId}`
- `DELETE /api/admin/stations/{stationId}`

## 相关表

- `power_station`
- `user_station_permission`

## 测试情况

- 已有 `StationPermissionServiceTest` 覆盖权限校验。
- 建议补充 Controller 集成测试、列表权限过滤测试、创建/更新字段校验测试。
