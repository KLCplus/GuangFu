# model 模块实现现状

## 模块职责

`model` 负责预测模型元数据管理，包括模型编码、类型、输入窗口、输出步长、模型服务名称和上下线状态。预测模块通过它校验可用模型。

## 已实现

- 前台模型列表和详情，只展示 `ONLINE` 且未删除的模型。
- 管理端模型列表、创建、更新、状态更新。
- 创建时校验模型编码唯一、模型类型、状态、输入窗口、帧间隔、输出步数、输出步长、模型服务名称。
- 状态流转有基本限制，预测只允许使用 `ONLINE` 模型。
- `ModelServiceHealthClient` 可探测模型服务健康和模型名称是否存在。
- `model_info` 已落库，`init.sql` 中预置多个模型元数据。

## 未实现或限制

- `model_metric` 和 `model_file` 有 DO/Mapper/表，但当前服务层未提供指标和模型文件管理接口。
- 创建/更新模型时没有强制检查远端模型服务一定存在，仅保留了健康检查客户端能力。
- 没有模型版本回滚、灰度、默认模型选择等能力。
- 删除模型接口未实现，只有状态变更。

## 主要接口

- `GET /api/models`
- `GET /api/models/{modelId}`
- `GET /api/admin/models`
- `POST /api/admin/models`
- `PUT /api/admin/models/{modelId}`
- `PUT /api/admin/models/{modelId}/status`

## 相关表

- `model_info`
- `model_metric`，已建表但未暴露业务接口。
- `model_file`，已建表但未暴露业务接口。

## 测试情况

- 已有 `ModelServiceTest`。
- 建议补充远端模型服务健康检查、状态流转边界、前台只展示在线模型、管理员接口权限测试。
