# prediction 模块实现现状

## 模块职责

`prediction` 负责创建预测任务、准备模型输入、调用 FastAPI 模型服务、保存输入快照和预测结果，并提供任务详情、结果和历史查询。

## 已实现

- 创建预测任务接口。
- 预测前校验电站查看权限和模型必须为 `ONLINE`。
- 当前实现支持 `MANUAL_MULTIMODAL` 输入模式，请求传入 30 个数值点和 30 张图片，均按 1 分钟间隔连续。
- 创建任务时保存 `prediction_task` 和 `prediction_input_snapshot`。
- 调用 `ModelServiceClient` 访问模型服务 `/model-api/predict`。
- 模型服务返回后保存 `prediction_result`，并将任务状态更新为 `SUCCESS`。
- 模型调用失败、返回异常或业务错误时，将任务状态更新为 `FAILED` 并记录错误。
- 支持任务详情、结果列表、历史分页查询。
- 普通用户只能查看自己的预测任务，管理员可查看全部。
- 预测结果时间基于最后一个输入点和模型配置的输出步长计算。

## 未实现或限制

- 预测执行是同步流程，没有队列、异步 worker、取消任务或重试机制。
- 目前只实现 `MANUAL_MULTIMODAL` 输入；`STATION_HISTORY`、`FILE_UPLOAD` 等模式未实现。
- `latestThirty` 不严格校验输入数据连续性，可能不满足模型对 30 分钟连续帧的要求。
- 没有预测结果与后续真实值的误差回填任务。
- 没有任务超时后的后台补偿处理，只在同步调用异常时标记失败。
- 未实现按模型、电站、状态、时间范围更丰富的历史筛选。

## 主要接口

- `POST /api/predictions`
- `GET /api/predictions/{taskId}`
- `GET /api/predictions/{taskId}/results`
- `GET /api/predictions/history`

## 相关表

- `prediction_task`
- `prediction_input_snapshot`
- `prediction_result`
- 依赖 `model_info`、`pv_data`、`power_station`。

## 测试情况

- 已有 `PredictionServiceTest`。
- 建议补充模型服务超时/返回空结果、任务失败状态一致性、普通用户越权访问、输入数据不连续、异步化前后的兼容测试。
