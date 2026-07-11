# Agent Final UI Report

## 删除的调试内容

| 内容 | 处理 |
| --- | --- |
| 右侧 Inspector | 已从 `/reports` 页面删除，页面改为左侧会话栏 + 中间 Agent 工作区。 |
| 上下文 / 工具 / 结果 / 调试 Tab | 已删除，不再作为正式 UI 展示。 |
| payload / response / rawOutput / llm_decision JSON | 不在页面展示；SSE 决策事件只转成用户可读状态。 |
| 旧直连工具调试逻辑 | 删除 `runTrackedTool`、报告直连生成、接口调试记录等页面逻辑。 |
| 工具完整 JSON 展示 | 工具结果改为业务摘要卡片，默认折叠，只显示摘要和错误说明。 |
| mock / setTimeout 模拟步骤 | 页面未引入模拟步骤，所有过程来自真实 SSE 事件。 |

## 最终页面结构

| 区域 | 内容 |
| --- | --- |
| 左侧会话栏 | 新建会话、固定会话、最近会话、已归档入口、归档/重命名/删除操作。数据来自 `agent_session` 接口。 |
| 顶部栏 | 综合分析 Agent、当前会话标题、Agent 模式、连接状态、用户入口。 |
| 中间消息流 | 用户消息、Agent 执行过程、工具业务卡片、确认卡片、最终回答。 |
| 底部输入框 | 自然语言输入、slash 能力入口、报告包含天气/预测选项。 |

更新：底部不再显示“报告含天气/预测”这类会让对话绑定电站或任务的开关。前端只提交用户原始意图，工具选择交给后端 Agent。

## SSE 到用户可见消息的映射

| SSE 事件 | 正式 UI 行为 |
| --- | --- |
| `started` | 创建或更新一条 Agent 处理中消息。 |
| `thinking` | 在执行卡片中显示“理解用户意图”。 |
| `intent_resolved` | 转成“已识别任务类型：xxx”，不展示 JSON。 |
| `plan` | 更新执行计划步骤。 |
| `tool_call` | 在当前 Agent 消息中追加工具卡片，状态为进行中。 |
| `tool_result` | 更新对应工具卡片为完成或失败，展示业务摘要。 |
| `approval_required` | 在聊天流中显示确认卡片。 |
| `token` | 追加到当前 Agent 回复正文。 |
| `final` | 完成当前 Agent 消息，展示最终回答和后续操作按钮。 |
| `error` | 在消息流中显示错误卡片。 |
| `llm_decision` / debug 类事件 | 正式 UI 忽略。 |

前端不再发送 `preferredTool` / `toolArguments`。slash command 只作为输入入口，后端会基于原始消息二次解析。

## 工具调用展示

工具结果不展示完整 JSON。页面按工具名转为业务名称：

| 工具 | 展示名称 |
| --- | --- |
| `station.detail` | 查询电站信息 |
| `weather.current` | 获取天气数据 |
| `prediction.detail` | 读取预测结果 |
| `report.generate` | 生成综合分析报告 |
| `api.usage` | 查看 API 使用情况 |

工具卡片默认折叠，摘要来自后端 `summary`、业务字段或错误信息。失败时展示失败状态和业务错误，不包装成成功。

## 确认流程

`report.generate` 等写操作收到 `approval_required` 后：

1. 聊天流显示“Agent 需要你的确认”。
2. 展示操作名称、原因、关键参数。
3. 用户点击“确认执行”后调用 `POST /api/agent/approvals/{approvalId}/approve`。
4. 前端继续调用 `/api/agent/chat/stream`，后端才执行真实工具。
5. 用户取消时卡片状态变为已取消，并继续让后端返回取消说明。

## 已验证场景

| 场景 | 结果 |
| --- | --- |
| 前端构建 | `npm run build` 通过。 |
| 页面调试内容检查 | 新版 `/reports` 模板不包含 Inspector、payload/response 调试面板、旧直连工具逻辑。 |
| Agent 链路 | 沿用后端 `/api/agent/chat/stream`，工具选择仍以后端 slash/preferredTool/业务意图/LLM 决策为准。 |
| 报告确认 | 确认卡片在聊天流内展示，确认后继续原有 approval 流程。 |
| 综合分析 | 输入“分析 2 号电站今天功率波动，结合天气和预测”时，SSE 出现 `station.detail`、`weather.current`、`prediction.list` 工具链。 |

## 仍待完善

| 项目 | 说明 |
| --- | --- |
| Markdown 富文本 | 当前最终回答以纯文本换行展示，后续可接入安全 Markdown 渲染。 |
| 业务字段摘要 | 已对天气、报告做基础摘要，后续可为更多工具增加专属摘要。 |
| 移动端会话栏 | 小屏暂隐藏会话栏，后续可改为抽屉。 |
