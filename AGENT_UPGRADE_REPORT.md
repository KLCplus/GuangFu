# Agent Upgrade Report

## 原问题原因

| 问题 | 原因 |
| --- | --- |
| slash command 看起来无反应 | 前端主要把 `/xxx` 当普通文本交给 Agent，后端没有稳定的 slash 二次解析和确定工具意图。 |
| 业务问题被普通聊天回答 | Prompt 只弱约束“可以调用工具”，没有硬性要求业务数据必须 tool_call。 |
| 模型直接 final | `AgentOrchestratorService` 接受 LLM 的 final，没有根据业务关键词拦截。 |
| 调试不可追踪 | SSE 没有暴露意图解析、LLM 原始输出、解析后 type 等决策事件。 |
| LLM 不可用时工具结果丢失 | 明确工具执行后仍会再次调用 LLM 总结，LLM 关闭时会直接 error。 |

## 修复内容

| 模块 | 修复 |
| --- | --- |
| 后端 Prompt | 强化为业务问题必须优先调用工具，加入 station/weather/prediction/report 示例和严格 JSON 输出规则。 |
| SlashCommandParser | 新增后端解析 `/station`、`/weather`、`/predict`、`/report`、`/model`、`/api`，支持缺参追问。 |
| preferredTool | `AgentChatRequest` 增加 `preferredTool`、`toolArguments`，前后端都支持工具意图传递。 |
| 工具调用循环 | slash/preferredTool 优先进入 tool_call；业务 final 被拦截并强制转工具调用。 |
| SSE 调试 | 新增 `intent_resolved`、`llm_decision`、`final_intercepted` 事件，调试面板可见。 |
| LLM 降级 | 已有工具结果后 LLM 不可用时，返回真实工具结果摘要，不伪造成功。 |
| 前端 slash | 前端解析 slash 并发送 preferredTool，同时未知命令本地提示。 |
| 前端工具面板 | 点击真实工具会插入对应 slash 命令，不再插入裸工具名。 |

## 已验证场景

| 输入 | 预期工具 | 实际工具 | 是否成功 | 备注 |
| --- | --- | --- | --- | --- |
| `/station 2` | `station.detail` | `station.detail` | 通过 | SSE: started, thinking, intent_resolved, llm_decision, plan, tool_call, tool_result, final。测试用户无权限，tool_result 为 failed。 |
| `查看 2 号电站信息` | `station.detail` | `station.detail` | 通过 | 自然语言被解析为业务工具调用。 |
| `/weather 2` | `weather.current` | `weather.current` | 通过 | 工具真实执行，当前测试用户无权限/数据时失败可见。 |
| `2 号电站天气怎么样，会影响发电吗` | `weather.current` | `weather.current` | 通过 | 自然语言天气问题没有直接闲聊。 |
| `解释任务 8 的预测结果` | `prediction.detail` | `prediction.detail` | 通过 | 工具真实执行，任务不可访问时 failed 可见。 |
| `/report 2` | `report.generate` | `report.generate` | 通过 | 首轮 SSE 返回 `approval_required`，确认后才执行工具。测试用户无权限时执行 failed。 |
| `/unknown` | 无 | 无 | 通过 | 返回追问/错误提示，不白屏。 |

## 工具注册校验

| 工具 | enabled | requiresApproval |
| --- | --- | --- |
| `station.list` | true | false |
| `station.detail` | true | false |
| `weather.current` | true | false |
| `prediction.list` | true | false |
| `prediction.detail` | true | false |
| `report.generate` | true | true |
| `report.list` | true | false |
| `report.detail` | true | false |
| `model.list` | true | false |
| `api.usage` | true | false |

## 仍未接入或禁用工具

禁用工具继续以 `enabled=false` 通过 `/api/agent/tools` 暴露，不会被 Agent 调用。主要包括需要更完整参数、安全策略或管理端审批的综合类工具，例如 `admin.station.manage`、`admin.model.manage` 等。具体原因以工具 description 为准。

## 验证命令

| 命令 | 结果 |
| --- | --- |
| `mvn -q -DskipTests compile` | 通过 |
| `npm run build` | 通过，仍有现有 Rolldown pure annotation 和 chunk size 警告 |
| `GET /api/agent/tools` | 200，关键工具 enabled |
| `POST /api/agent/chat/stream` | 已用测试账号验证 station/weather/prediction/report/unknown 事件链 |

