# analysis 模块实现现状

## 模块职责

`analysis` 负责基于电站历史光伏数据、天气数据和预测结果生成综合分析报告，并提供报告历史和详情查询。

## 已实现

- 生成分析报告接口，入参包含电站、预测任务、标题以及是否包含天气/预测分析。
- 生成前校验电站查看权限、预测任务归属和任务状态必须为 `SUCCESS`。
- 从 `pv_data` 获取最近 30 分钟左右的功率数据，计算平均值、最大值、最小值、历史变化率和波动率。
- 从 `prediction_result` 获取预测结果并计算预测趋势。
- 可读取最近一条 `weather_data` 生成天气分析，没有天气数据时会降级说明。
- 检测采样断档和低辐照度等基础异常信号。
- 报告摘要、天气分析、预测分析、异常分析、建议、正文和结构化 JSON 均写入 `analysis_report`。
- 支持报告历史分页和详情查询。
- 普通用户只能查看自己的报告，管理员可查看全部。

## 未实现或限制

- 报告生成是规则模板，不是 LLM 或复杂统计分析。
- 只使用最近一段数据，没有支持用户指定分析时间范围。
- 天气分析只用最近一条天气记录，未融合天气预报序列。
- 异常检测较基础，没有设备告警、逆变器维度、容量归一化或 PR 指标。
- 报告不能导出 PDF/Word，也没有重新生成、删除、分享接口。

## 主要接口

- `POST /api/analysis/report`
- `GET /api/analysis/reports`
- `GET /api/analysis/reports/{reportId}`

## 相关表

- `analysis_report`
- 依赖 `pv_data`、`weather_data`、`prediction_task`、`prediction_result`、`power_station`。

## 测试情况

- 已有 `PhaseFourServiceTest` 覆盖阶段四部分服务。
- 建议补充无天气数据、预测任务失败、数据不足、普通用户越权查看、报告 JSON 内容校验测试。
