# Agent E2E Test Report

## 测试脚本

新增脚本：

```bash
AGENT_TEST_TOKEN=你的登录Token node scripts/agent-e2e-test.mjs
```

可选指定后端地址：

```bash
AGENT_BASE_URL=http://127.0.0.1:8080 AGENT_TEST_TOKEN=你的登录Token node scripts/agent-e2e-test.mjs
```

脚本不会打印 token。

## 覆盖场景

| 输入 | 预期工具/事件 | 验收点 |
| --- | --- | --- |
| `/station 2` | `tool_call station.detail` + `final` | 电站查询走真实工具 |
| `/weather 2` | `tool_call weather.current` + `final` | 天气查询走真实工具 |
| `/predict 8` | `tool_call prediction.detail` + `final` | 预测任务详情走真实工具 |
| `/report 2` | `approval_required report.generate` | 生成报告先确认，不直接写库 |
| `什么是光伏功率预测？` | 无 `tool_call` + `final` | 普通知识问题不乱调工具 |

## 本轮执行结果

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 脚本无 token 保护 | 通过 | 未设置 `AGENT_TEST_TOKEN` 时退出并提示用法 |
| 后端编译 | 通过 | `mvn -q -DskipTests compile` |
| 前端构建 | 通过 | `npm run build` |
| 真实 SSE E2E | 未执行 | 当前执行环境没有可用 `AGENT_TEST_TOKEN`，不能伪造登录态或绕过认证 |

## 真实 E2E 待执行命令

```bash
AGENT_BASE_URL=http://127.0.0.1:8080 AGENT_TEST_TOKEN=*** node scripts/agent-e2e-test.mjs
```

通过标准：5 个场景全部 PASS，并且 `/report 2` 出现 `approval_required` 而不是直接生成报告。
