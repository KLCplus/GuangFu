# DeepSeek Integration

## 配置方式

DeepSeek 只在 Spring Boot 后端调用，`model-service` 不参与第三方 LLM 调用。

本地 PowerShell 示例：

```powershell
$env:DEEPSEEK_API_KEY="你的真实key"
$env:ANALYSIS_LLM_ENABLED="true"
```

可选配置：

```powershell
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_TIMEOUT_MS="60000"
```

不要把真实 API Key 写入代码、`application.yml` 或提交到仓库。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `ANALYSIS_LLM_ENABLED` | `true` | 是否启用真实 LLM。为 `false` 时使用规则 fallback。 |
| `ANALYSIS_LLM_PROVIDER` | `deepseek` | 当前 Provider 标识。 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek OpenAI-compatible API 地址。 |
| `DEEPSEEK_API_KEY` | 空 | 真实 Key，只能从环境变量或本地 `.env` 注入。 |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | 默认演示模型。 |
| `DEEPSEEK_TEMPERATURE` | `0.2` | 低温度，优先稳定 JSON。 |
| `DEEPSEEK_MAX_TOKENS` | `4096` | 最大输出 token。 |
| `DEEPSEEK_TIMEOUT_MS` | `60000` | 后端调用超时。 |
| `DEEPSEEK_JSON_MODE` | `true` | 使用 `response_format: json_object`。 |

## 使用模型

默认模型：`deepseek-v4-flash`。

需要更高质量时可切换：

```powershell
$env:DEEPSEEK_MODEL="deepseek-v4-pro"
```

## 调用流程

1. 前端 `/reports` 调用 `POST /api/analysis/report`。
2. `AnalysisController` 只做接收和返回。
3. `AnalysisService` 创建 PENDING 报告记录。
4. `AnalysisContextService` 聚合用户、电站、天气、预测、历史功率和平台上下文。
5. `AnalysisPromptBuilder` 构造光伏综合分析 Prompt。
6. `AnalysisAgentService` 通过 `LlmClient` 调用 DeepSeek 或 disabled fallback。
7. `LlmResultParser` 解析 `summary/sections/markdown/riskLevel/suggestions`。
8. `AnalysisService` 保存 SUCCESS/FAILED、模型名、上下文快照、Prompt 快照和 raw response。

## 错误处理

- `DEEPSEEK_API_KEY` 为空且 `ANALYSIS_LLM_ENABLED=true`：返回 `DeepSeek API Key 未配置`，不会 mock 成功。
- HTTP 401：返回 `DeepSeek API Key 错误或无效`。
- HTTP 429：返回 `DeepSeek 额度不足或请求频率受限`。
- HTTP 5xx：返回 `DeepSeek 上游模型服务错误`。
- 超时：返回 `DeepSeek 调用超时`。
- 非 JSON：清理 Markdown 代码块后重试解析，仍失败则保存 raw response 并返回 `模型返回格式解析失败`。

## 关闭 LLM Fallback

设置：

```powershell
$env:ANALYSIS_LLM_ENABLED="false"
```

此时 `/api/analysis/report` 不调用 DeepSeek，使用 `MockLlmClient` 生成明确标记为 fallback 的报告，`modelName=mock-analysis-fallback`。

## 可替换 Client

后端通过 `LlmClient` 隔离模型调用。后续接自研 LLM 或内部服务时，只需要新增实现并在 `AnalysisAgentService` 中切换，不需要把模型调用写进 Controller。
