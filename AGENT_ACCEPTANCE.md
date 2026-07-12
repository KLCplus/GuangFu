# Agent Acceptance Criteria

## 总体验收目标

`/reports` 必须表现为项目级智能 Agent 工作台：用户提出业务目标后，Agent 自主选择必要工具，聊天流清晰展示执行过程、工具业务摘要和最终结论。正式 UI 不展示 payload、response、raw JSON、token、API Key 或调试面板。

## 场景 1：查询电站

输入：

```text
查看 2 号电站信息
```

期望：

- SSE 出现 `tool_call`，工具为 `station.detail`。
- 聊天流显示“查询电站信息”。
- 工具卡展示电站名称、容量、位置、状态等业务字段。
- 最终回答引用真实电站数据。
- 不调用 weather/prediction/report 等无关工具。

## 场景 2：查询天气

输入：

```text
2 号电站当前天气怎么样，会影响发电吗
```

期望：

- SSE 出现 `tool_call weather.current`。
- 必要时可先调用 `station.detail`。
- 聊天流显示“获取天气数据”。
- 工具卡展示天气、温度、湿度、云量、风速和一句发电影响判断。
- 最终回答说明天气对发电的影响。

## 场景 3：预测分析

输入：

```text
解释任务 8 的预测结果
```

期望：

- SSE 出现 `tool_call prediction.detail`。
- 聊天流显示“读取预测结果”。
- 工具卡展示任务状态、预测时间范围、预测步长、趋势、异常波动判断。
- 最终回答解释趋势、异常和风险。

## 场景 4：综合分析

输入：

```text
分析 2 号电站今天功率波动，结合天气和预测
```

期望：

- Agent 自主选择必要工具。
- 至少调用 `station.detail`、`weather.current`，并调用 `prediction.list` 或 `prediction.detail`。
- 不无脑调用 report/model/api/news/admin 等无关工具。
- 每个工具结果都有简洁业务摘要。
- 最终回答结构化展示：
  - 摘要
  - 影响因素
  - 预测趋势
  - 异常判断
  - 建议

## 场景 5：生成报告

输入：

```text
生成 2 号电站综合分析报告
```

期望：

- Agent 判断 `report.generate` 是写操作。
- 聊天流显示确认卡片。
- 用户确认前不执行 `report.generate`。
- 用户确认后执行 `report.generate`。
- 完成后显示报告标题、reportId、摘要、风险等级和后续操作按钮。
- 用户取消后，不创建报告，并回复“已取消生成报告”。

## 场景 6：缺少参数

输入：

```text
分析这个电站的天气
```

期望：

- 如果上下文没有 stationId，Agent 不乱猜。
- Agent 调用 `station.list` 或追问用户提供电站 ID。
- 不使用默认 stationId。

## 场景 7：普通聊天

输入：

```text
什么是光伏功率预测？
```

期望：

- 可以直接回答。
- 不调用 station/weather/prediction/report/api/model 工具。
- 回答简洁、清楚、适合平台用户。

## UI 验收

- 左侧会话栏数据来自真实 Agent session 接口。
- 中间消息流可滚动。
- 底部输入框固定在工作区底部。
- slash command 支持 `/station /weather /predict /report /model /api`。
- 工具调用显示为业务卡片，不展示完整 JSON。
- 确认卡片在聊天流中展示。
- 不出现 Inspector、payload、response、rawOutput、llm_decision JSON。

## 后端验收

- 工具调用必须调用真实 Service。
- `report.generate` 必须确认后执行。
- 工具调用记录必须持久化。
- 工具失败必须可见，不伪装成功。
- LLM 对业务问题直接 final 时必须被后端拦截。

