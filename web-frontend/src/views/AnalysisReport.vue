<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import {
  createReport,
  getReport,
  getReports,
  normalizeReportDetail,
  normalizeReportId,
  normalizeReportList,
  type AnalysisReportDetail,
  type AnalysisReportListItem,
  type CreateAnalysisReportRequest
} from '../api/analysis'
import { getStation, getStations, getRealtime } from '../api/station'
import { getCurrentWeather, getForecast } from '../api/weather'
import { getPrediction, getPredictionHistory, getPredictionResults } from '../api/prediction'
import { getApiKeys, getCallLogs } from '../api/open'
import { getModel, getModels } from '../api/model'
import { getNews, getNewsList } from '../api/news'
import { getNotifications, getUnreadCount } from '../api/notification'
import { getProfile } from '../api/user'
import {
  approveAgentApproval,
  archiveAgentSession,
  createAgentSession,
  deleteAgentSession,
  getAgentSessionMessages,
  getAgentSessions,
  getAgentSessionToolCalls,
  getAgentTools,
  pinAgentSession,
  renameAgentSession,
  streamAgentChat,
  unarchiveAgentSession,
  unpinAgentSession,
  type AgentMessageRecord,
  type AgentSession,
  type AgentToolCallRecord,
  type AgentToolInfo,
  type AgentSseEnvelope
} from '../api/agent'

type MessageRole = 'user' | 'agent' | 'system' | 'tool'
type MessageType = 'text' | 'error' | 'report' | 'status' | 'approval'
type StepStatus = 'pending' | 'running' | 'success' | 'failed'
type ToolStatus = 'connected' | 'available' | 'pending'

interface ChatMessage {
  id: number
  role: MessageRole
  type: MessageType
  content: string
  report?: AnalysisReportDetail
  metadata?: Record<string, unknown>
  time: string
}

interface ExecutionStep {
  name: string
  status: StepStatus
  detail: string
}

interface DebugRecord {
  name: string
  method: string
  url: string
  payload?: unknown
  status?: number | string
  response?: unknown
  error?: unknown
  duration: number
  time: string
}

interface ReportSection {
  key: string
  title: string
  content?: string
}

interface SlashCommand {
  command: string
  title: string
  detail: string
  action: ToolAction
}

interface ToolItem {
  group: string
  name: string
  command: string
  status: ToolStatus
}

type ToolAction =
  | 'report'
  | 'station'
  | 'weather'
  | 'predict'
  | 'api'
  | 'model'
  | 'news'
  | 'notify'
  | 'user'
  | 'pending'

const userStore = useUserStore()

const activeTab = ref('context')
const reports = ref<AnalysisReportListItem[]>([])
const agentSessions = ref<AgentSession[]>([])
const currentReport = ref<AnalysisReportDetail | null>(null)
const selectedReportId = ref<number | null>(null)
const loadingHistory = ref(false)
const loadingDetail = ref(false)
const running = ref(false)
const historyError = ref('')
const detailError = ref('')
const debugRecords = ref<DebugRecord[]>([])
const agentSessionId = ref<number | null>(null)
const agentTools = ref<AgentToolInfo[]>([])
const recentToolCalls = ref<AgentToolCallRecord[]>([])
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const showArchived = ref(false)
const showSlashMenu = ref(false)
const composerText = ref('')
const composerRef = ref<HTMLTextAreaElement>()
const pinnedIds = ref<Set<number>>(new Set())
const archivedIds = ref<Set<number>>(new Set())
const hiddenIds = ref<Set<number>>(new Set())
const titleOverrides = ref<Record<number, string>>({})

const form = reactive({
  stationId: null as number | null,
  taskId: null as number | null,
  title: '',
  includeWeather: true,
  includePrediction: true
})

const slashCommands: SlashCommand[] = [
  { command: '/report', title: '综合分析', detail: '生成综合分析报告，需要确认写入', action: 'report' },
  { command: '/predict', title: '预测解读', detail: '交给 Agent 决策调用预测工具', action: 'predict' },
  { command: '/weather', title: '天气分析', detail: '交给 Agent 决策调用天气工具', action: 'weather' },
  { command: '/station', title: '电站上下文', detail: '交给 Agent 决策调用电站工具', action: 'station' },
  { command: '/api', title: 'API 查看', detail: '交给 Agent 决策调用 API 工具', action: 'api' },
  { command: '/model', title: '模型查询', detail: '交给 Agent 决策调用模型工具', action: 'model' }
]

const tools: ToolItem[] = [
  { group: '分析', name: '综合分析报告', command: '/report', status: 'connected' },
  { group: '分析', name: '异常诊断', command: '/diagnose', status: 'pending' },
  { group: '分析', name: '运维建议', command: '/ops', status: 'pending' },
  { group: '数据', name: '电站上下文', command: '/station', status: 'pending' },
  { group: '数据', name: '天气信息', command: '/weather', status: 'pending' },
  { group: '数据', name: '预测任务', command: '/predict', status: 'pending' },
  { group: '模型', name: '模型信息', command: '/model', status: 'pending' },
  { group: '模型', name: '云图预测', command: '/cloud', status: 'pending' },
  { group: '平台', name: 'API 状态', command: '/api', status: 'pending' },
  { group: '平台', name: '用户资料', command: '/user', status: 'pending' },
  { group: '平台', name: '新闻资讯', command: '/news', status: 'pending' },
  { group: '平台', name: '通知中心', command: '/notify', status: 'pending' },
  { group: '平台', name: '报告导出', command: '/export', status: 'pending' }
]

const messages = ref<ChatMessage[]>([createSystemMessage()])
const executionSteps = ref<ExecutionStep[]>(baseSteps())

const lastDebug = computed(() => debugRecords.value[0] ?? null)
const currentReportId = computed(() => (currentReport.value ? normalizeReportId(currentReport.value) : selectedReportId.value))
const username = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || 'User')

const visibleReports = computed(() => reports.value.filter((report) => {
  const id = reportIdOf(report)
  return !id || (!hiddenIds.value.has(id) && !archivedIds.value.has(id))
}))

const archivedReports = computed(() => reports.value.filter((report) => {
  const id = reportIdOf(report)
  return id ? archivedIds.value.has(id) && !hiddenIds.value.has(id) : false
}))

const pinnedReports = computed(() => visibleReports.value.filter((report) => {
  const id = reportIdOf(report)
  return id ? pinnedIds.value.has(id) : false
}))

const recentReports = computed(() => visibleReports.value.filter((report) => {
  const id = reportIdOf(report)
  return !id || !pinnedIds.value.has(id)
}))

const filteredCommands = computed(() => {
  const text = composerText.value.trim()
  if (!text.startsWith('/')) return slashCommands
  return slashCommands.filter((item) => item.command.includes(text.split(/\s+/)[0]))
})

const groupedTools = computed(() => {
  const groups: Record<string, ToolItem[]> = {}
  for (const tool of tools) {
    groups[tool.group] = groups[tool.group] ?? []
    groups[tool.group].push(tool)
  }
  return groups
})

const groupedAgentTools = computed(() => {
  const groups: Record<string, AgentToolInfo[]> = {}
  for (const tool of agentTools.value) {
    const group = tool.category || 'AGENT'
    groups[group] = groups[group] ?? []
    groups[group].push(tool)
  }
  return groups
})

const currentTitle = computed(() => {
  const report = currentReport.value
  return firstText(report?.title, report?.reportTitle, report?.name) || '后端未返回标题'
})

const currentSummary = computed(() => {
  const report = currentReport.value
  return firstText(report?.summary, report?.abstract, report?.overview)
})

const currentBody = computed(() => {
  const report = currentReport.value
  const parsed = parsedReportJson(report)
  return firstText(report?.markdown, parsed.markdown, report?.content, report?.reportContent, report?.body)
})

const currentRiskLevel = computed(() => {
  const report = currentReport.value
  const parsed = parsedReportJson(report)
  return firstText(report?.riskLevel, parsed.riskLevel, 'unknown')
})

const currentSuggestions = computed(() => {
  const report = currentReport.value
  const parsed = parsedReportJson(report)
  return stringList(report?.suggestions, parsed.suggestions, report?.recommendations, report?.suggestion)
})

const currentModelName = computed(() => firstText(currentReport.value?.modelName, parsedReportJson(currentReport.value).modelName, '未返回'))
const currentGeneratedAt = computed(() => currentReport.value ? getReportTime(currentReport.value) : '-')


const pinnedSessions = computed(() => agentSessions.value.filter((session) => session.pinned && !session.archived))
const recentSessions = computed(() => agentSessions.value.filter((session) => !session.pinned && !session.archived))
const archivedSessions = computed(() => agentSessions.value.filter((session) => session.archived))
const activeSessionTitle = computed(() => agentSessions.value.find((item) => item.sessionId === agentSessionId.value)?.title || '新会话')

const reportDebugMeta = computed(() => {
  const report = currentReport.value
  const parsed = parsedReportJson(report)
  return {
    modelName: currentModelName.value,
    llmEnabled: report?.llmEnabled ?? parsed.llmEnabled ?? null,
    llmProvider: firstText(report?.llmProvider, parsed.llmProvider, '未返回'),
    hasRawResponse: Boolean(firstText(report?.rawResponse)),
    hasPromptSnapshot: Boolean(firstText(report?.promptSnapshot)),
    hasContextSnapshot: Boolean(firstText(report?.contextSnapshot))
  }
})

const reportSections = computed<ReportSection[]>(() => {
  const report = currentReport.value
  if (!report) return []
  const parsed = parsedReportJson(report)
  const structured = Array.isArray(report.sections) && report.sections.length > 0 ? report.sections : parsed.sections
  if (structured.length > 0) {
    return structured.map((section, index) => ({
      key: `${section.title || 'section'}-${index}`,
      title: firstText(section.title, `章节 ${index + 1}`),
      content: firstText(section.content)
    }))
  }
  return [
    { key: 'summary', title: '摘要', content: currentSummary.value },
    { key: 'weather', title: '天气', content: firstText(report.weatherAnalysis) },
    { key: 'prediction', title: '预测', content: firstText(report.predictionAnalysis) },
    { key: 'anomaly', title: '异常', content: firstText(report.anomalyAnalysis, report.abnormalAnalysis) },
    { key: 'suggestion', title: '建议', content: firstText(report.suggestion, report.recommendations) },
    { key: 'body', title: '正文', content: currentBody.value }
  ]
})

const copyText = computed(() => {
  if (!currentReport.value) return ''
  if (currentBody.value) return currentBody.value
  const lines = [`# ${currentTitle.value}`, '', currentSummary.value]
  for (const section of reportSections.value) {
    if (section.content) lines.push(`\n## ${section.title}\n${section.content}`)
  }
  if (currentSuggestions.value.length > 0) {
    lines.push(`\n## 建议\n${currentSuggestions.value.map((item) => `- ${item}`).join('\n')}`)
  }
  return lines.join('\n')
})

function nowTime() {
  return new Date().toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function createSystemMessage(): ChatMessage {
  return {
    id: Date.now(),
    role: 'system',
    type: 'text',
    content: '输入任务，或使用 / 调用工具。',
    time: nowTime()
  }
}

function baseSteps(): ExecutionStep[] {
  return [
    { name: '理解任务', status: 'pending', detail: '等待输入。' },
    { name: '选择工具', status: 'pending', detail: '等待 Agent 决策。' },
    { name: '调用接口', status: 'pending', detail: '等待真实接口返回。' },
    { name: '生成回答', status: 'pending', detail: '等待 DeepSeek 总结。' }
  ]
}

function firstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function recordValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function messageMeta(message: ChatMessage, key: string): Record<string, unknown> {
  return recordValue(message.metadata?.[key])
}

function formatAgentPayload(value: unknown) {
  if (value === undefined || value === null || value === '') return '{}'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function approvalTitle(message: ChatMessage) {
  const approval = messageMeta(message, 'approval')
  return firstText(approval.displayName, approval.toolName, '待确认操作')
}

function approvalReason(message: ChatMessage) {
  const approval = messageMeta(message, 'approval')
  return firstText(approval.reason, '该操作需要确认后才会执行。')
}

function parsedReportJson(report?: AnalysisReportDetail | null) {
  const empty = {
    summary: '',
    riskLevel: '',
    sections: [] as ReportSection[],
    suggestions: [] as string[],
    markdown: '',
    modelName: '',
    llmEnabled: null as boolean | null,
    llmProvider: ''
  }
  if (!report?.reportJson || typeof report.reportJson !== 'string') return empty
  try {
    const parsed = JSON.parse(report.reportJson) as Record<string, unknown>
    const sections = Array.isArray(parsed.sections)
      ? parsed.sections.map((item, index) => {
        const section = item && typeof item === 'object' ? item as Record<string, unknown> : {}
        return {
          key: `${firstText(section.title, 'section')}-${index}`,
          title: firstText(section.title, `章节 ${index + 1}`),
          content: firstText(section.content)
        }
      })
      : []
    return {
      summary: firstText(parsed.summary),
      riskLevel: firstText(parsed.riskLevel),
      sections,
      suggestions: stringList(parsed.suggestions),
      markdown: firstText(parsed.markdown),
      modelName: firstText(parsed.modelName),
      llmEnabled: typeof parsed.llmEnabled === 'boolean' ? parsed.llmEnabled : null,
      llmProvider: firstText(parsed.llmProvider)
    }
  } catch {
    return empty
  }
}

function stringList(...values: unknown[]) {
  const result: string[] = []
  for (const value of values) {
    if (Array.isArray(value)) {
      for (const item of value) {
        const text = firstText(item)
        if (text) result.push(text)
      }
    } else {
      const text = firstText(value)
      if (text) {
        result.push(...text.split(/\n+/).map((item) => item.trim()).filter(Boolean))
      }
    }
  }
  return Array.from(new Set(result))
}

function riskClass(riskLevel?: string) {
  const value = (riskLevel || '').toLowerCase()
  if (value === 'high') return 'danger'
  if (value === 'medium') return 'warning'
  if (value === 'low') return 'success'
  return 'info'
}

function riskText(riskLevel?: string) {
  const value = (riskLevel || '').toLowerCase()
  if (value === 'high') return '高风险'
  if (value === 'medium') return '中风险'
  if (value === 'low') return '低风险'
  return '未知'
}

function asPageRecords<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (value && typeof value === 'object' && Array.isArray((value as { records?: unknown[] }).records)) {
    return (value as { records: T[] }).records
  }
  return []
}

function firstNumber(...values: unknown[]) {
  for (const value of values) {
    const number = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN
    if (Number.isFinite(number) && number > 0) return number
  }
  return null
}

function commandParts(text = composerText.value) {
  const parts = text.trim().split(/\s+/).filter(Boolean)
  return {
    command: parts[0] || '',
    args: parts.slice(1)
  }
}

function commandNumber(args: string[], fallback?: number | null) {
  return firstNumber(args[0], fallback)
}

function formatJson(value: unknown) {
  return JSON.stringify(value, null, 2)
}

function statusOf(value: unknown) {
  if (!value || typeof value !== 'object') return ''
  const record = value as { status?: string; taskStatus?: string; modelStatus?: string }
  return record.taskStatus || record.modelStatus || record.status || ''
}

function summarizeList<T>(items: T[], formatter: (item: T, index: number) => string, empty: string) {
  if (items.length === 0) return empty
  return items.slice(0, 8).map(formatter).join('\n')
}

function addToolResult(title: string, response: unknown, content: string) {
  addMessage({
    role: 'agent',
    type: 'text',
    content: `${title}\n${content || '后端未返回可展示字段。'}`
  })
  activeTab.value = 'debug'
  rightCollapsed.value = false
}

function reportIdOf(report: AnalysisReportListItem | AnalysisReportDetail) {
  return normalizeReportId(report)
}

function getReportTitle(report: AnalysisReportListItem | AnalysisReportDetail) {
  const id = reportIdOf(report)
  return (id ? titleOverrides.value[id] : '') || firstText(report.title, report.reportTitle, report.name) || `报告 ${id ?? '-'}`
}

function getReportTime(report: AnalysisReportListItem | AnalysisReportDetail) {
  return firstText(report.createdAt, report.createTime, report.generatedAt, report.updatedAt) || '无时间'
}

function statusClass(status?: string) {
  const value = (status || '').toLowerCase()
  if (value.includes('fail') || value.includes('error')) return 'failed'
  if (value.includes('run') || value.includes('pending')) return 'running'
  return 'done'
}

function statusText(status?: string) {
  const value = (status || '').toLowerCase()
  if (value.includes('fail') || value.includes('error')) return '失败'
  if (value.includes('run') || value.includes('pending')) return '运行中'
  if (!status) return '完成'
  return status
}

function asRecord(error: unknown) {
  return error && typeof error === 'object' ? (error as Record<string, unknown>) : null
}

function extractErrorInfo(error: unknown) {
  const record = asRecord(error)
  const response = asRecord(record?.response)
  const request = record?.request
  const status = response?.status
  const data = response?.data
  const message = error instanceof Error ? error.message : '未知错误'
  const stack = error instanceof Error ? error.stack : undefined

  return {
    status: typeof status === 'number' ? status : request ? 'Network Error' : 'Error',
    data,
    message,
    stack
  }
}

function humanError(error: unknown) {
  const info = extractErrorInfo(error)
  if (info.status === 401 || info.status === 403) return '登录状态可能失效，或当前 token 无权限。'
  if (info.status === 404) return '接口不存在，请检查 /api/analysis/...'
  if (info.status === 500) return '后端内部错误，查看调试响应。'
  if (info.status === 'Network Error') return '后端未启动，或代理/baseURL 异常。'
  if (info.message.toLowerCase().includes('cors')) return '可能是 CORS / Proxy 问题。'
  return info.message
}

function recordDebug(record: DebugRecord) {
  debugRecords.value.unshift(record)
  debugRecords.value = debugRecords.value.slice(0, 5)
}

async function trackedRequest<T>(meta: Omit<DebugRecord, 'duration' | 'time' | 'response' | 'error' | 'status'>, task: () => Promise<T>) {
  const started = performance.now()
  const time = nowTime()
  const pendingRecord: DebugRecord = {
    ...meta,
    status: 'pending',
    duration: 0,
    time
  }
  recordDebug(pendingRecord)
  const completeRecord = (patch: Partial<DebugRecord>) => {
    const index = debugRecords.value.indexOf(pendingRecord)
    const next = {
      ...pendingRecord,
      ...patch,
      duration: Math.round(performance.now() - started)
    }
    if (index >= 0) {
      debugRecords.value[index] = next
    } else {
      recordDebug(next)
    }
  }
  try {
    const response = await task()
    completeRecord({
      status: 200,
      response
    })
    return response
  } catch (error) {
    const info = extractErrorInfo(error)
    completeRecord({
      status: info.status,
      response: info.data,
      error: { message: info.message, stack: info.stack }
    })
    throw error
  }
}

async function runTrackedTool<T>(
  title: string,
  meta: Omit<DebugRecord, 'duration' | 'time' | 'response' | 'error' | 'status'>,
  task: () => Promise<T>,
  presenter: (response: T) => string
) {
  if (running.value) return
  running.value = true
  detailError.value = ''
  showSlashMenu.value = false
  activeTab.value = 'debug'
  addMessage({ role: 'user', type: 'text', content: composerText.value.trim() || title })
  addMessage({ role: 'tool', type: 'status', content: `调用工具：${title}` })
  composerText.value = ''
  try {
    const response = await trackedRequest(meta, task)
    addToolResult(title, response, presenter(response))
  } catch (error) {
    const message = humanError(error)
    addMessage({ role: 'agent', type: 'error', content: `${meta.method} ${meta.url} 调用失败：${message}` })
    detailError.value = message
  } finally {
    running.value = false
  }
}

function setStep(index: number, status: StepStatus, detail: string) {
  executionSteps.value[index] = { ...executionSteps.value[index], status, detail }
}

function resetSteps() {
  executionSteps.value = baseSteps()
}

function addMessage(message: Omit<ChatMessage, 'id' | 'time'>) {
  messages.value.push({
    ...message,
    id: Date.now() + Math.random(),
    time: nowTime()
  })
}

function formatHistoryTime(value?: string) {
  if (!value) return nowTime()
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function toolResultPayload(tool: AgentToolCallRecord) {
  const result = recordValue(tool.result)
  return {
    toolCallId: tool.clientToolCallId,
    toolName: tool.toolName,
    displayName: tool.displayName || tool.toolName,
    status: tool.status === 'SUCCESS' ? 'success' : tool.status === 'FAILED' ? 'failed' : tool.status.toLowerCase(),
    summary: firstText(result.summary, tool.errorMessage, tool.status),
    error: firstText(tool.errorMessage, result.errorMessage),
    durationMs: tool.durationMs ?? 0,
    dataPreview: result.data ?? result.raw ?? result
  }
}

function mapHistoryMessage(record: AgentMessageRecord): ChatMessage {
  const role: MessageRole = record.role === 'assistant' ? 'agent' : record.role === 'user' ? 'user' : record.role === 'tool' ? 'tool' : 'system'
  const metadata = recordValue(record.metadata)
  let type: MessageType = metadata.approvalId || metadata.toolName ? 'approval' : 'text'
  const firstApproval = record.approvals?.[0]
  if (firstApproval && firstApproval.status === 'PENDING') {
    type = 'approval'
    metadata.approval = {
      approvalId: firstApproval.approvalId,
      toolCallId: firstApproval.toolCallId,
      toolName: firstApproval.toolName,
      displayName: firstApproval.toolName,
      reason: firstApproval.reason,
      arguments: firstApproval.arguments,
      status: firstApproval.status
    }
  }
  const firstTool = record.toolCalls?.[0]
  if (firstTool && firstTool.status !== 'AWAITING_APPROVAL') {
    metadata.toolResult = toolResultPayload(firstTool)
    if (role === 'agent') type = firstTool.status === 'FAILED' ? 'error' : 'text'
  }
  return {
    id: record.messageId,
    role,
    type,
    content: record.content || (type === 'approval' ? '需要确认操作' : ''),
    metadata,
    time: formatHistoryTime(record.createdAt)
  }
}

async function loadAgentSessions() {
  loadingHistory.value = true
  historyError.value = ''
  try {
    const result = await getAgentSessions({ page: 1, size: 50 })
    agentSessions.value = result.records || []
    return true
  } catch (error) {
    agentSessions.value = []
    historyError.value = `会话加载失败：${humanError(error)}`
    activeTab.value = 'debug'
    return false
  } finally {
    loadingHistory.value = false
  }
}

async function loadAgentMessages(sessionId: number) {
  loadingDetail.value = true
  detailError.value = ''
  try {
    agentSessionId.value = sessionId
    selectedReportId.value = null
    const history = await getAgentSessionMessages(sessionId)
    messages.value = history.length > 0 ? history.map(mapHistoryMessage) : [createSystemMessage()]
    recentToolCalls.value = await getAgentSessionToolCalls(sessionId)
  } catch (error) {
    detailError.value = `会话消息加载失败：${humanError(error)}`
    addMessage({ role: 'agent', type: 'error', content: detailError.value })
  } finally {
    loadingDetail.value = false
  }
}

async function createNewAgentSession() {
  const session = await createAgentSession('新的 Agent 会话')
  await loadAgentSessions()
  agentSessionId.value = session.sessionId
  currentReport.value = null
  selectedReportId.value = null
  detailError.value = ''
  composerText.value = ''
  showSlashMenu.value = false
  messages.value = [createSystemMessage()]
  executionSteps.value = baseSteps()
  focusComposer()
}

function focusComposer() {
  nextTick(() => composerRef.value?.focus())
}

function newSession() {
  currentReport.value = null
  selectedReportId.value = null
  detailError.value = ''
  composerText.value = ''
  showSlashMenu.value = false
  messages.value = [createSystemMessage()]
  executionSteps.value = baseSteps()
  focusComposer()
}

async function loadReports() {
  loadingHistory.value = true
  historyError.value = ''
  try {
    const result = await trackedRequest(
      { name: '查询报告历史', method: 'GET', url: '/analysis/reports' },
      () => getReports({ pageNum: 1, pageSize: 30 })
    )
    reports.value = normalizeReportList(result)
    return true
  } catch (error) {
    reports.value = []
    historyError.value = `历史加载失败：${humanError(error)}`
    activeTab.value = 'debug'
    return false
  } finally {
    loadingHistory.value = false
  }
}

async function loadReportDetail(reportId: number) {
  selectedReportId.value = reportId
  loadingDetail.value = true
  detailError.value = ''
  activeTab.value = 'debug'
  recordDebug({
    name: '准备查询报告详情',
    method: 'GET',
    url: `/analysis/reports/${reportId}`,
    payload: { reportId },
    status: 'pending',
    duration: 0,
    time: nowTime()
  })
  try {
    const response = await trackedRequest(
      { name: '查询报告详情', method: 'GET', url: `/analysis/reports/${reportId}` },
      () => getReport(reportId)
    )
    const detail = normalizeReportDetail(response)
    if (!detail) throw new Error('后端未返回报告详情对象')
    currentReport.value = detail
    addMessage({
      role: 'agent',
      type: 'report',
      content: currentSummary.value || currentBody.value || '后端已返回报告详情，但摘要和正文为空。',
      report: detail
    })
    activeTab.value = 'result'
  } catch (error) {
    currentReport.value = null
    detailError.value = `报告详情加载失败：${humanError(error)}`
    addMessage({ role: 'agent', type: 'error', content: detailError.value })
    activeTab.value = 'debug'
  } finally {
    loadingDetail.value = false
  }
}

function buildPayload(): CreateAnalysisReportRequest | null {
  if (!form.stationId || form.stationId <= 0) {
    ElMessage.warning('请输入有效的电站 ID')
    activeTab.value = 'context'
    rightCollapsed.value = false
    return null
  }
  const typedInstruction = composerText.value.trim().replace(/^\/report\s*/i, '').trim()
  return {
    stationId: form.stationId,
    taskId: form.taskId && form.taskId > 0 ? form.taskId : undefined,
    title: form.title.trim() || `电站 ${form.stationId} 综合分析报告`,
    userInstruction: typedInstruction || undefined,
    includeWeather: form.includeWeather,
    includePrediction: form.includePrediction
  }
}

function buildUserTask(payload: CreateAnalysisReportRequest) {
  const text = firstText(payload.userInstruction, composerText.value.trim())
  if (text && !text.startsWith('/report')) return text
  const parts = []
  if (payload.includeWeather) parts.push('天气分析')
  if (payload.includePrediction) parts.push('预测分析')
  return `生成电站 ${payload.stationId} 的综合分析报告${parts.length ? `，包含${parts.join('和')}。` : '。'}`
}


function agentContext() {
  return {
    conversationMode: 'independent_agent',
    includeWeather: form.includeWeather,
    includePrediction: form.includePrediction,
    reportTitlePreference: form.title || null,
    currentReportId: currentReportId.value
  }
}

function eventData(event: AgentSseEnvelope) {
  return event.data || {}
}

function pushAgentDebug(event: AgentSseEnvelope) {
  recordDebug({
    name: `Agent:${event.event}`,
    method: 'SSE',
    url: '/agent/chat/stream',
    payload: event.data,
    status: event.event === 'error' ? 'failed' : 'success',
    response: event.data,
    duration: 0,
    time: nowTime()
  })
}

function handleAgentEvent(event: AgentSseEnvelope) {
  pushAgentDebug(event)
  const data = eventData(event)
  if (event.event === 'started') {
    agentSessionId.value = firstNumber(data.sessionId, agentSessionId.value)
    setStep(0, 'success', firstText(data.text) || 'Agent 已开始处理。')
    return
  }
  if (event.event === 'thinking') {
    setStep(1, 'running', firstText(data.text) || '正在选择工具。')
    addMessage({ role: 'tool', type: 'status', content: firstText(data.text) || '正在选择工具。' })
    return
  }
  if (event.event === 'plan') {
    const steps = Array.isArray(data.steps) ? data.steps.map((item) => String(item)) : []
    executionSteps.value = steps.length
      ? steps.map((step, index) => ({ name: step, status: index === 0 ? 'running' : 'pending', detail: firstText(data.reason) || '等待执行。' }))
      : executionSteps.value
    return
  }
  if (event.event === 'tool_call') {
    setStep(2, 'running', `调用 ${firstText(data.displayName, data.toolName)}`)
    addMessage({
      role: 'tool',
      type: 'status',
      content: `正在调用：${firstText(data.displayName, data.toolName)}`,
      metadata: { tool: data }
    })
    return
  }
  if (event.event === 'tool_result') {
    const ok = firstText(data.status) === 'success'
    setStep(2, ok ? 'success' : 'failed', firstText(data.summary, data.error) || '工具调用完成。')
    addMessage({
      role: 'tool',
      type: ok ? 'status' : 'error',
      content: `${firstText(data.displayName, data.toolName)}：${firstText(data.summary, data.error)}`,
      metadata: { toolResult: data }
    })
    return
  }
  if (event.event === 'approval_required') {
    setStep(2, 'running', '等待用户确认。')
    addMessage({
      role: 'agent',
      type: 'approval',
      content: `需要确认：${firstText(data.displayName, data.toolName)}`,
      metadata: { approval: data }
    })
    return
  }
  if (event.event === 'final') {
    setStep(3, 'success', '回答已生成。')
    const content = firstText(data.markdown, data.content) || 'Agent 已完成。'
    addMessage({ role: 'agent', type: 'text', content, metadata: { final: data } })
    return
  }
  if (event.event === 'error') {
    setStep(3, 'failed', firstText(data.message) || 'Agent 执行失败。')
    addMessage({ role: 'agent', type: 'error', content: `${firstText(data.message) || 'Agent 执行失败'}\n${firstText(data.detail)}` })
  }
}

async function runAgentChat(message: string, approvalId?: number | null) {
  if (running.value) return
  running.value = true
  detailError.value = ''
  showSlashMenu.value = false
  activeTab.value = 'debug'
  resetSteps()
  if (!approvalId) addMessage({ role: 'user', type: 'text', content: message })
  try {
    await streamAgentChat({
      sessionId: agentSessionId.value,
      message,
      approvalId: approvalId ?? null,
      context: agentContext(),
      mode: 'auto',
      allowedTools: [],
      requireApproval: true
    }, handleAgentEvent)
  } catch (error) {
    const message = humanError(error)
    detailError.value = message
    addMessage({ role: 'agent', type: 'error', content: `Agent 请求失败：${message}` })
  } finally {
    running.value = false
    void loadAgentSessions()
    if (agentSessionId.value) void getAgentSessionToolCalls(agentSessionId.value).then((items) => { recentToolCalls.value = items }).catch(() => undefined)
  }
}

async function decideApproval(message: ChatMessage, approved: boolean) {
  const approval = message.metadata?.approval as Record<string, unknown> | undefined
  const approvalId = firstNumber(approval?.approvalId)
  if (!approvalId || running.value) return
  await approveAgentApproval(approvalId, approved, approved ? '同意执行' : '拒绝执行')
  addMessage({ role: 'user', type: 'text', content: approved ? '确认执行' : '取消执行' })
  await runAgentChat(approved ? '继续执行已确认的操作' : '取消刚才的操作', approvalId)
}

async function loadAgentTools() {
  try {
    agentTools.value = await getAgentTools()
  } catch {
    agentTools.value = []
  }
}

async function runAnalysis() {
  if (running.value) return
  const payload = buildPayload()
  if (!payload) return

  const taskText = buildUserTask(payload)
  running.value = true
  detailError.value = ''
  resetSteps()
  showSlashMenu.value = false
  activeTab.value = 'debug'

  setStep(0, 'success', `stationId=${payload.stationId}, taskId=${payload.taskId ?? '未传'}`)
  addMessage({ role: 'user', type: 'text', content: taskText })
  addMessage({ role: 'tool', type: 'status', content: '调用工具：综合分析报告' })
  addMessage({ role: 'agent', type: 'status', content: '正在生成报告...' })
  composerText.value = ''

  try {
    setStep(1, 'running', '请求中。')
    const response = await trackedRequest(
      { name: '生成综合分析报告', method: 'POST', url: '/analysis/report', payload },
      () => createReport(payload)
    )
    const report = normalizeReportDetail(response)
    setStep(1, 'success', '调用成功。')
    setStep(2, 'success', '已接收后端响应。')
    currentReport.value = report
    selectedReportId.value = report ? reportIdOf(report) : null

    if (report) {
      addMessage({
        role: 'agent',
        type: 'report',
        content: firstText(report.summary, report.abstract, report.overview, report.markdown, report.content, report.reportContent) ||
          '后端已返回报告对象，但未返回摘要或正文内容。',
        report
      })
    }

    setStep(3, 'running', '刷新中。')
    const refreshed = await loadReports()
    if (refreshed) {
      setStep(3, 'success', '完成。')
    } else {
      setStep(3, 'failed', '刷新失败，查看调试。')
    }

    const reportId = report ? reportIdOf(report) : null
    if (reportId) {
      try {
        setStep(4, 'running', `加载 #${reportId}。`)
        const detailResponse = await trackedRequest(
          { name: '生成后加载报告详情', method: 'GET', url: `/analysis/reports/${reportId}` },
          () => getReport(reportId)
        )
        const detail = normalizeReportDetail(detailResponse)
        if (!detail) throw new Error('后端未返回报告详情对象')
        currentReport.value = detail
        selectedReportId.value = reportId
        setStep(4, 'success', '完成。')
      } catch (error) {
        setStep(4, 'failed', humanError(error))
      }
    } else if (refreshed && reports.value.length > 0) {
      const firstId = reportIdOf(reports.value[0])
      if (firstId) {
        setStep(4, 'running', `响应未返回 id，加载最新历史 #${firstId}。`)
        await loadReportDetail(firstId)
        setStep(4, currentReport.value ? 'success' : 'failed', currentReport.value ? '完成。' : '最新历史加载失败。')
      } else {
        setStep(4, 'failed', '最新历史未返回 id/reportId。')
      }
    } else {
      setStep(4, 'failed', '响应未返回 id/reportId，历史列表也无可选报告。')
    }

    activeTab.value = 'result'
  } catch (error) {
    const message = humanError(error)
    setStep(1, 'failed', message)
    setStep(2, 'failed', '未收到可用报告。')
    addMessage({ role: 'agent', type: 'error', content: `POST /analysis/report 调用失败：${message}` })
    detailError.value = message
    activeTab.value = 'debug'
  } finally {
    running.value = false
  }
}

async function runStationTool(args: string[]) {
  const stationId = commandNumber(args, form.stationId)
  if (stationId) {
    await runTrackedTool(
      '电站详情',
      { name: '查询电站详情', method: 'GET', url: `/stations/${stationId}` },
      () => getStation(stationId),
      (station) => [
        `stationId: ${station.stationId}`,
        `名称: ${station.stationName || '后端未返回该字段'}`,
        `位置: ${firstText(station.province, '后端未返回该字段')} ${firstText(station.city)}`,
        `容量: ${station.capacity ?? '后端未返回该字段'} kW`,
        `状态: ${station.status || '后端未返回该字段'}`,
        `描述: ${station.description || '后端未返回该字段'}`
      ].join('\n')
    )
    return
  }
  await runTrackedTool(
    '电站列表',
    { name: '查询电站列表', method: 'GET', url: '/stations', payload: { pageNum: 1, pageSize: 10 } },
    () => getStations({ pageNum: 1, pageSize: 10 }),
    (page) => summarizeList(
      asPageRecords<{ stationId: number; stationName?: string; city?: string; status?: string }>(page),
      (station) => `#${station.stationId} ${station.stationName || '后端未返回该字段'} · ${station.city || '后端未返回该字段'} · ${station.status || '后端未返回该字段'}`,
      '后端返回空电站列表。'
    )
  )
}

async function runWeatherTool(args: string[]) {
  const stationId = commandNumber(args, form.stationId)
  if (!stationId) {
    ElMessage.warning('请先输入 stationId，或使用 /weather 2')
    return
  }
  await runTrackedTool(
    '天气信息',
    { name: '查询天气信息', method: 'GET', url: `/stations/${stationId}/weather/current` },
    async () => {
      const current = await getCurrentWeather(stationId)
      const forecast = await getForecast(stationId)
      return { current, forecast }
    },
    ({ current, forecast }) => [
      `当前天气: ${current.weather || '后端未返回该字段'}`,
      `温度: ${current.temperature ?? '后端未返回该字段'} ℃`,
      `湿度: ${current.humidity ?? '后端未返回该字段'}%`,
      `风: ${current.windDirection || '后端未返回该字段'} ${current.windPower || ''}`,
      `来源: ${current.source || '后端未返回该字段'}${current.cached ? '（缓存）' : ''}`,
      '',
      summarizeList(
        forecast,
        (item) => `${item.date}: ${item.dayWeather || '后端未返回该字段'} / ${item.nightWeather || '后端未返回该字段'}，${item.dayTemp ?? '-'}~${item.nightTemp ?? '-'} ℃`,
        '后端返回空天气预报。'
      )
    ].join('\n')
  )
}

async function runPredictTool(args: string[]) {
  const taskId = commandNumber(args, form.taskId)
  if (taskId) {
    await runTrackedTool(
      '预测任务详情',
      { name: '查询预测任务详情', method: 'GET', url: `/predictions/${taskId}` },
      async () => {
        const detail = await getPrediction(taskId)
        const results = await getPredictionResults(taskId)
        return { detail, results }
      },
      ({ detail, results }) => [
        `taskId: ${detail.taskId}`,
        `状态: ${statusOf(detail) || '后端未返回该字段'}`,
        `模型: ${detail.modelName || '后端未返回该字段'} (${detail.modelCode || '-'})`,
        `stationId: ${detail.stationId ?? '后端未返回该字段'}`,
        `创建时间: ${detail.createdAt || '后端未返回该字段'}`,
        '',
        summarizeList(
          results,
          (item) => `${item.timeOffset}min: ${item.predictPower} kW${item.predictTime ? ` · ${item.predictTime}` : ''}`,
          '后端返回空预测结果。'
        )
      ].join('\n')
    )
    return
  }
  await runTrackedTool(
    '预测任务列表',
    { name: '查询预测任务历史', method: 'GET', url: '/predictions/history', payload: { pageNum: 1, pageSize: 10 } },
    () => getPredictionHistory({ pageNum: 1, pageSize: 10, stationId: form.stationId || undefined }),
    (page) => summarizeList(
      asPageRecords<{ taskId: number; taskStatus?: string; status?: string; modelName?: string; createdAt?: string }>(page),
      (task) => `#${task.taskId} ${task.modelName || '后端未返回模型'} · ${task.taskStatus || task.status || '后端未返回状态'} · ${task.createdAt || '后端未返回时间'}`,
      '后端返回空预测任务列表。'
    )
  )
}

async function runApiTool() {
  await runTrackedTool(
    'API 状态',
    { name: '查询 API Key 与调用日志', method: 'GET', url: '/open/keys' },
    async () => {
      const keys = await getApiKeys()
      const logs = await getCallLogs({ pageNum: 1, pageSize: 10 })
      return { keys, logs }
    },
    ({ keys, logs }) => [
      `API Key 数量: ${keys.length}`,
      summarizeList(
        keys,
        (key) => `#${key.apiKeyId} ${key.keyName || '后端未返回名称'} · ${key.status || '后端未返回状态'} · ${key.apiKeyPrefix || '后端未返回前缀'}`,
        '当前用户暂无 API Key。'
      ),
      '',
      '最近调用:',
      summarizeList(
        asPageRecords<{ path?: string; method?: string; status?: string; statusCode?: number; createdAt?: string }>(logs),
        (log) => `${log.method || '-'} ${log.path || '-'} · ${log.status || log.statusCode || '-'} · ${log.createdAt || '-'}`,
        '当前用户暂无 API 调用日志。'
      )
    ].join('\n')
  )
}

async function runModelTool(args: string[]) {
  const modelId = commandNumber(args)
  if (modelId) {
    await runTrackedTool(
      '模型详情',
      { name: '查询模型详情', method: 'GET', url: `/models/${modelId}` },
      () => getModel(modelId),
      (model) => [
        `modelId: ${model.modelId}`,
        `名称: ${model.modelName || '后端未返回该字段'}`,
        `编码: ${model.modelCode || '后端未返回该字段'}`,
        `类型: ${model.modelType || '后端未返回该字段'}`,
        `状态: ${statusOf(model) || '后端未返回该字段'}`,
        `服务模型: ${model.serviceModelName || '后端未返回该字段'}`,
        `描述: ${model.description || '后端未返回该字段'}`
      ].join('\n')
    )
    return
  }
  await runTrackedTool(
    '模型列表',
    { name: '查询模型列表', method: 'GET', url: '/models' },
    () => getModels(),
    (models) => summarizeList(
      models,
      (model) => `#${model.modelId} ${model.modelName || '后端未返回名称'} · ${model.modelType || '-'} · ${statusOf(model) || '-'}`,
      '后端返回空模型列表。'
    )
  )
}

async function runNewsTool(args: string[]) {
  const newsId = commandNumber(args)
  if (newsId) {
    await runTrackedTool(
      '新闻详情',
      { name: '查询新闻详情', method: 'GET', url: `/news/${newsId}` },
      () => getNews(newsId),
      (news) => [
        news.title || '后端未返回标题',
        news.summary || '后端未返回摘要',
        '',
        news.content || '后端未返回正文'
      ].join('\n')
    )
    return
  }
  await runTrackedTool(
    '新闻列表',
    { name: '查询新闻列表', method: 'GET', url: '/news', payload: { pageNum: 1, pageSize: 10 } },
    () => getNewsList({ pageNum: 1, pageSize: 10 }),
    (page) => summarizeList(
      asPageRecords<{ newsId: number; title?: string; summary?: string; publishedAt?: string; createdAt?: string }>(page),
      (news) => `#${news.newsId} ${news.title || '后端未返回标题'} · ${news.publishedAt || news.createdAt || '后端未返回时间'}\n${news.summary || '后端未返回摘要'}`,
      '后端返回空新闻列表。'
    )
  )
}

async function runNotifyTool() {
  await runTrackedTool(
    '通知中心',
    { name: '查询通知列表', method: 'GET', url: '/notifications', payload: { pageNum: 1, pageSize: 10 } },
    async () => {
      const unread = await getUnreadCount()
      const notifications = await getNotifications({ pageNum: 1, pageSize: 10 })
      return { unread, notifications }
    },
    ({ unread, notifications }) => {
      const unreadCount = (unread as unknown as { unreadCount?: number; count?: number }).unreadCount ?? unread.count ?? 0
      return [
        `未读通知: ${unreadCount}`,
        summarizeList(
          asPageRecords<{ notificationId: number; title?: string; content?: string; readStatus?: number; createdAt?: string }>(notifications),
          (item) => `#${item.notificationId} ${item.title || '后端未返回标题'} · ${item.readStatus === 1 ? '已读' : '未读'} · ${item.createdAt || '-'}\n${item.content || '后端未返回内容'}`,
          '后端返回空通知列表。'
        )
      ].join('\n')
    }
  )
}

async function runUserTool() {
  await runTrackedTool(
    '用户资料',
    { name: '查询当前用户资料', method: 'GET', url: '/user/profile' },
    () => getProfile(),
    (profile) => [
      `userId: ${profile.userId}`,
      `用户名: ${profile.username || '后端未返回该字段'}`,
      `昵称: ${profile.nickname || '后端未返回该字段'}`,
      `邮箱: ${profile.email || '后端未返回该字段'}`,
      `角色: ${profile.roles?.join(', ') || '后端未返回该字段'}`
    ].join('\n')
  )
}

async function runToolCommand(command: SlashCommand, args: string[]) {
  showSlashMenu.value = false
  const text = [command.command, ...args].join(' ').trim() || command.command
  composerText.value = ''
  await runAgentChat(text)
}

function onComposerInput() {
  showSlashMenu.value = composerText.value.trim().startsWith('/')
}

function applyCommand(command: SlashCommand) {
  composerText.value = `${command.command} `
  showSlashMenu.value = false
  focusComposer()
}

async function submitComposer() {
  const text = composerText.value.trim()
  if (!text) {
    ElMessage.warning('请输入要交给 Agent 的问题或任务')
    return
  }
  const { command, args } = commandParts(text)
  const found = slashCommands.find((item) => item.command === command)
  if (found) {
    await runToolCommand(found, args)
    return
  }
  composerText.value = ''
  await runAgentChat(text)
}

function onComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submitComposer()
  }
}

function setCommand(command: string) {
  composerText.value = `${command} `
  showSlashMenu.value = false
  focusComposer()
}

function togglePin(report: AnalysisReportListItem) {
  const id = reportIdOf(report)
  if (!id) return
  const next = new Set(pinnedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  pinnedIds.value = next
}

function toggleArchive(report: AnalysisReportListItem) {
  const id = reportIdOf(report)
  if (!id) return
  const next = new Set(archivedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  archivedIds.value = next
}

async function renameSession(report: AnalysisReportListItem) {
  const id = reportIdOf(report)
  if (!id) return
  try {
    const value = await ElMessageBox.prompt('会话标题', '重命名', {
      inputValue: getReportTitle(report),
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const text = firstText(value.value)
    if (text) titleOverrides.value = { ...titleOverrides.value, [id]: text }
  } catch {
    // user cancelled
  }
}

async function hideSession(report: AnalysisReportListItem) {
  const id = reportIdOf(report)
  if (!id) return
  try {
    await ElMessageBox.confirm('仅从当前工作台隐藏，不删除后端数据。', '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const next = new Set(hiddenIds.value)
    next.add(id)
    hiddenIds.value = next
    if (selectedReportId.value === id) newSession()
  } catch {
    // user cancelled
  }
}

function getSessionTime(session: AgentSession) {
  return firstText(session.updatedAt, session.createdAt) || '无时间'
}

async function togglePinSession(session: AgentSession) {
  session.pinned ? await unpinAgentSession(session.sessionId) : await pinAgentSession(session.sessionId)
  await loadAgentSessions()
}

async function toggleArchiveSession(session: AgentSession) {
  session.archived ? await unarchiveAgentSession(session.sessionId) : await archiveAgentSession(session.sessionId)
  await loadAgentSessions()
}

async function renameAgentSessionTitle(session: AgentSession) {
  try {
    const value = await ElMessageBox.prompt('会话标题', '重命名', {
      inputValue: session.title,
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const text = firstText(value.value)
    if (text) {
      await renameAgentSession(session.sessionId, text)
      await loadAgentSessions()
    }
  } catch {
    // user cancelled
  }
}

async function deleteAgentSessionById(session: AgentSession) {
  try {
    await ElMessageBox.confirm('删除后会话不会再显示，历史消息保留在数据库中。', '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteAgentSession(session.sessionId)
    if (agentSessionId.value === session.sessionId) newSession()
    await loadAgentSessions()
  } catch {
    // user cancelled
  }
}

async function handleSessionCommand(command: string, session: AgentSession) {
  if (command === 'pin') await togglePinSession(session)
  if (command === 'archive') await toggleArchiveSession(session)
  if (command === 'rename') await renameAgentSessionTitle(session)
  if (command === 'delete') await deleteAgentSessionById(session)
}

async function copyReport() {
  if (!copyText.value) {
    ElMessage.warning('暂无可复制的报告正文')
    return
  }
  try {
    await navigator.clipboard.writeText(copyText.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function copySummary() {
  if (!currentSummary.value) {
    ElMessage.warning('暂无可复制的摘要')
    return
  }
  try {
    await navigator.clipboard.writeText(currentSummary.value)
    ElMessage.success('已复制摘要')
  } catch {
    ElMessage.error('复制失败')
  }
}

function regenerateDisabled() {
  ElMessage.info('暂未接入')
}

function logout() {
  userStore.logout()
  window.location.href = '/login'
}

onMounted(() => {
  void loadAgentTools()
  void loadAgentSessions()
})
</script>

<template>
  <div
    class="agent-workbench"
    :class="{ 'left-collapsed': leftCollapsed, 'right-collapsed': rightCollapsed }"
  >
    <aside class="session-rail">
      <div class="rail-top">
        <button class="icon-button" type="button" @click="leftCollapsed = !leftCollapsed">
          {{ leftCollapsed ? '>' : '<' }}
        </button>
        <button v-if="!leftCollapsed" class="new-chat" type="button" @click="createNewAgentSession">新建</button>
      </div>

      <template v-if="!leftCollapsed">
        <div class="session-search">
          <span>/</span>
          <input placeholder="搜索会话" disabled />
        </div>

        <el-alert v-if="historyError" class="inline-alert" type="error" :title="historyError" show-icon />

        <div v-loading="loadingHistory" class="sessions">
          <div v-if="pinnedSessions.length > 0" class="session-group">
            <p class="group-label">固定</p>
            <button
              v-for="session in pinnedSessions"
              :key="`pin-${session.sessionId}`"
              class="session-item"
              :class="{ active: session.sessionId === agentSessionId }"
              type="button"
              @click="loadAgentMessages(session.sessionId)"
            >
              <span class="status-dot" :class="statusClass(session.status)"></span>
              <span class="session-text">
                <strong>{{ session.title }}</strong>
                <small>{{ getSessionTime(session) }}</small>
              </span>
              <el-dropdown trigger="click" @command="(cmd: string) => handleSessionCommand(cmd, session)">
                <span class="more-button" @click.stop>...</span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="pin">取消固定</el-dropdown-item>
                    <el-dropdown-item command="archive">归档</el-dropdown-item>
                    <el-dropdown-item command="rename">重命名</el-dropdown-item>
                    <el-dropdown-item command="delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </button>
          </div>

          <div class="session-group">
            <p class="group-label">最近</p>
            <el-empty v-if="!loadingHistory && recentSessions.length === 0 && !historyError" description="暂无会话" />
            <button
              v-for="session in recentSessions"
              :key="`recent-${session.sessionId}`"
              class="session-item"
              :class="{ active: session.sessionId === agentSessionId }"
              type="button"
              
              @click="loadAgentMessages(session.sessionId)"
            >
              <span class="status-dot" :class="statusClass(session.status)"></span>
              <span class="session-text">
                <strong>{{ session.title }}</strong>
                <small>{{ getSessionTime(session) }} · {{ statusText(session.status) }}</small>
              </span>
              <el-dropdown trigger="click" @command="(cmd: string) => handleSessionCommand(cmd, session)">
                <span class="more-button" @click.stop>...</span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="pin">固定</el-dropdown-item>
                    <el-dropdown-item command="archive">归档</el-dropdown-item>
                    <el-dropdown-item command="rename">重命名</el-dropdown-item>
                    <el-dropdown-item command="delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </button>
          </div>

          <div class="archive-line">
            <button type="button" @click="showArchived = !showArchived">
              已归档 <span>{{ archivedSessions.length }}</span>
            </button>
          </div>

          <div v-if="showArchived" class="session-group archived">
            <button
              v-for="session in archivedSessions"
              :key="`archived-${session.sessionId}`"
              class="session-item"
              type="button"
              @click="loadAgentMessages(session.sessionId)"
            >
              <span class="status-dot muted"></span>
              <span class="session-text">
                <strong>{{ session.title }}</strong>
                <small>{{ getSessionTime(session) }}</small>
              </span>
              <el-dropdown trigger="click" @command="(cmd: string) => handleSessionCommand(cmd, session)">
                <span class="more-button" @click.stop>...</span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="archive">恢复</el-dropdown-item>
                    <el-dropdown-item command="rename">重命名</el-dropdown-item>
                    <el-dropdown-item command="delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </button>
          </div>
        </div>
      </template>
    </aside>

    <main class="chat-stage">
      <header class="stage-header">
        <div class="agent-title">
          <span class="agent-mark">AI</span>
          <div>
            <h1>光伏 Agent</h1>
            <p>独立 AI 对话工作台</p>
          </div>
        </div>
        <div class="header-actions">
          <el-tag size="small" effect="plain">Real API</el-tag>
          <el-tag size="small" type="success" effect="plain">Connected</el-tag>
          <el-dropdown trigger="click">
            <button class="user-chip" type="button">
              <span>{{ username.slice(0, 1).toUpperCase() }}</span>
              {{ username }}
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>用户中心</el-dropdown-item>
                <el-dropdown-item disabled>我的会话</el-dropdown-item>
                <el-dropdown-item disabled>设置</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <button class="icon-button" type="button" @click="rightCollapsed = !rightCollapsed">
            {{ rightCollapsed ? '<' : '>' }}
          </button>
        </div>
      </header>

      <section class="message-stream">
        <div
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="[`role-${message.role}`, `type-${message.type}`]"
        >
          <div v-if="message.role !== 'system'" class="avatar">{{ message.role === 'user' ? 'U' : message.role === 'tool' ? 'T' : 'AI' }}</div>
          <div class="message-bubble">
            <div v-if="message.role !== 'system'" class="message-meta">
              <strong>{{ message.role === 'user' ? username : message.role === 'tool' ? 'Tool' : 'Agent' }}</strong>
              <span>{{ message.time }}</span>
            </div>
            <p>{{ message.content }}</p>

            <div v-if="message.metadata?.tool" class="agent-tool-card">
              <div>
                <strong>{{ firstText(messageMeta(message, 'tool').displayName, messageMeta(message, 'tool').toolName) }}</strong>
                <el-tag size="small" effect="plain">调用中</el-tag>
              </div>
              <pre>{{ formatAgentPayload(messageMeta(message, 'tool').arguments) }}</pre>
            </div>

            <div v-if="message.metadata?.toolResult" class="agent-tool-card" :class="firstText(messageMeta(message, 'toolResult').status)">
              <div>
                <strong>{{ firstText(messageMeta(message, 'toolResult').displayName, messageMeta(message, 'toolResult').toolName) }}</strong>
                <el-tag size="small" :type="firstText(messageMeta(message, 'toolResult').status) === 'success' ? 'success' : 'danger'">{{ firstText(messageMeta(message, 'toolResult').status) }}</el-tag>
              </div>
              <small>{{ firstText(messageMeta(message, 'toolResult').summary, messageMeta(message, 'toolResult').error) }}</small>
              <details>
                <summary>dataPreview</summary>
                <pre>{{ formatAgentPayload(messageMeta(message, 'toolResult').dataPreview) }}</pre>
              </details>
            </div>

            <div v-if="message.type === 'approval'" class="approval-card">
              <div>
                <strong>{{ approvalTitle(message) }}</strong>
                <el-tag size="small" type="warning">需要确认</el-tag>
              </div>
              <p>{{ approvalReason(message) }}</p>
              <pre>{{ formatAgentPayload(messageMeta(message, 'approval').arguments) }}</pre>
              <div class="approval-actions">
                <el-button size="small" type="primary" :loading="running" @click="decideApproval(message, true)">确认执行</el-button>
                <el-button size="small" :disabled="running" @click="decideApproval(message, false)">取消</el-button>
              </div>
            </div>

            <div v-if="message.type === 'report' && message.report" class="result-card">
              <div class="result-head">
                <strong>{{ currentTitle }}</strong>
                <button type="button" @click="activeTab = 'result'; rightCollapsed = false">查看结果</button>
              </div>
              <section v-for="section in reportSections.filter((item) => item.content).slice(0, 3)" :key="section.key">
                <span>{{ section.title }}</span>
                <p>{{ section.content }}</p>
              </section>
            </div>
          </div>
        </div>
      </section>

      <section class="tool-activity" :class="{ active: running || executionSteps.some((item) => item.status !== 'pending') }">
        <div v-for="step in executionSteps" :key="step.name" class="activity-step" :class="step.status">
          <span></span>
          <strong>{{ step.name }}</strong>
          <small>{{ step.detail }}</small>
        </div>
      </section>

      <section class="composer-wrap">
        <div v-if="showSlashMenu && filteredCommands.length > 0" class="slash-menu">
          <button v-for="command in filteredCommands" :key="command.command" type="button" @click="applyCommand(command)">
            <strong>{{ command.command }}</strong>
            <span>{{ command.title }}</span>
            <small>{{ command.detail }}</small>
          </button>
        </div>

        <div class="context-pills">
          <button type="button" @click="activeTab = 'context'; rightCollapsed = false">独立会话 {{ agentSessionId ?? '-' }}</button>
          <button type="button" @click="activeTab = 'tools'; rightCollapsed = false">工具由 Agent 按对话选择</button>
          <button :class="{ on: form.includeWeather }" type="button" @click="form.includeWeather = !form.includeWeather">报告含天气</button>
          <button :class="{ on: form.includePrediction }" type="button" @click="form.includePrediction = !form.includePrediction">报告含预测</button>
        </div>

        <div class="composer">
          <button class="tool-trigger" type="button" @click="showSlashMenu = !showSlashMenu">/</button>
          <textarea
            ref="composerRef"
            v-model="composerText"
            rows="1"
            placeholder="输入任务，或使用 / 调用工具"
            @input="onComposerInput"
            @keydown="onComposerKeydown"
          ></textarea>
          <button class="send-button" type="button" :disabled="running" @click="submitComposer">
            {{ running ? '运行中' : '发送' }}
          </button>
        </div>

        <div class="quick-commands">
          <button type="button" @click="setCommand('/report')">/report</button>
          <button type="button" @click="setCommand('/predict')">/predict</button>
          <button type="button" @click="setCommand('/weather')">/weather</button>
          <button type="button" @click="setCommand('/station')">/station</button>
          <button type="button" @click="setCommand('/api')">/api</button>
        </div>
      </section>
    </main>

    <aside class="inspector">
      <button v-if="rightCollapsed" class="floating-open" type="button" @click="rightCollapsed = false">Inspector</button>

      <template v-if="!rightCollapsed">
        <header class="inspector-head">
          <strong>Inspector</strong>
          <button class="icon-button" type="button" @click="rightCollapsed = true">></button>
        </header>

        <el-tabs v-model="activeTab" class="inspector-tabs">
          <el-tab-pane label="上下文" name="context">
            <div class="context-list standalone">
              <p><span>模式</span><strong>独立 AI 对话</strong></p>
              <p><span>sessionId</span><strong>{{ agentSessionId ?? '-' }}</strong></p>
              <p><span>会话</span><strong>{{ activeSessionTitle }}</strong></p>
              <p><span>用户</span><strong>{{ username }}</strong></p>
              <p><span>reportId</span><strong>{{ currentReportId ?? '-' }}</strong></p>
            </div>
            <div class="mini-form agent-options">
              <label class="wide">
                <span>报告标题偏好</span>
                <el-input v-model.trim="form.title" placeholder="可选，生成报告时由 Agent 参考" clearable />
              </label>
              <div class="switches">
                <el-switch v-model="form.includeWeather" active-text="报告含天气" />
                <el-switch v-model="form.includePrediction" active-text="报告含预测" />
              </div>
            </div>
            <div class="side-actions">
              <el-button :loading="loadingHistory" @click="loadAgentSessions">刷新会话</el-button>
              <el-button @click="activeTab = 'tools'">查看工具</el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane label="工具" name="tools">
            <div v-if="agentTools.length > 0" class="tool-groups">
              <section v-for="(items, group) in groupedAgentTools" :key="group">
                <p>{{ group }}</p>
                <button v-for="tool in items" :key="tool.name" type="button" @click="composerText = tool.name">
                  <span>
                    <strong>{{ tool.displayName }}</strong>
                    <small>{{ tool.name }} · {{ tool.permissionLevel }}</small>
                  </span>
                  <em :class="tool.enabled ? 'connected' : 'pending'">{{ tool.requiresApproval ? '需确认' : tool.enabled ? '可调用' : '禁用' }}</em>
                </button>
              </section>
            </div>
            <div v-else class="tool-groups">
              <section v-for="(items, group) in groupedTools" :key="group">
                <p>{{ group }}</p>
                <button v-for="tool in items" :key="tool.command" type="button" @click="setCommand(tool.command)">
                  <span>
                    <strong>{{ tool.name }}</strong>
                    <small>{{ tool.command }}</small>
                  </span>
                  <em :class="tool.status">{{ tool.status === 'connected' ? '已接入' : tool.status === 'available' ? '可查看' : '待接入' }}</em>
                </button>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="结果" name="result">
            <div class="result-actions">
              <el-button size="small" :disabled="!currentReport" @click="copyReport">复制 Markdown</el-button>
              <el-button size="small" :disabled="!currentSummary" @click="copySummary">复制摘要</el-button>
              <el-button size="small" :loading="loadingHistory" @click="loadReports">刷新历史</el-button>
              <el-button size="small" :disabled="!currentReport" @click="regenerateDisabled">重新生成</el-button>
            </div>
            <el-alert v-if="detailError" class="inline-alert" type="error" :title="detailError" show-icon />
            <div v-loading="loadingDetail" class="preview-pane">
              <el-empty v-if="!currentReport && !loadingDetail" description="暂无结果" />
              <article v-else-if="currentReport">
                <div class="report-title-line">
                  <h3>{{ currentTitle }}</h3>
                  <el-tag size="small" :type="riskClass(currentRiskLevel)">{{ riskText(currentRiskLevel) }}</el-tag>
                </div>
                <div class="summary-card">
                  <span>Summary</span>
                  <p>{{ currentSummary || '后端未返回摘要' }}</p>
                </div>
                <el-collapse class="section-collapse">
                  <el-collapse-item v-for="section in reportSections" :key="section.key" :title="section.title" :name="section.key">
                    <p>{{ section.content || '后端未返回该字段' }}</p>
                  </el-collapse-item>
                </el-collapse>
                <section v-if="currentSuggestions.length > 0">
                  <h4>Suggestions</h4>
                  <ul class="suggestion-list">
                    <li v-for="suggestion in currentSuggestions" :key="suggestion">{{ suggestion }}</li>
                  </ul>
                </section>
                <section>
                  <h4>Markdown</h4>
                  <pre class="markdown-preview">{{ currentBody || '后端未返回 Markdown 正文' }}</pre>
                </section>
                <div class="meta-grid">
                  <p><span>模型名称</span><strong>{{ currentModelName }}</strong></p>
                  <p><span>生成时间</span><strong>{{ currentGeneratedAt }}</strong></p>
                  <p><span>状态</span><strong>{{ currentReport.status || '未返回' }}</strong></p>
                  <p><span>风险等级</span><strong>{{ currentRiskLevel }}</strong></p>
                </div>
              </article>
            </div>
          </el-tab-pane>

          <el-tab-pane label="调试" name="debug">
            <div class="debug-meta">
              <p><span>modelName</span><strong>{{ reportDebugMeta.modelName }}</strong></p>
              <p><span>llmEnabled</span><strong>{{ reportDebugMeta.llmEnabled === null ? '未返回' : reportDebugMeta.llmEnabled }}</strong></p>
              <p><span>llmProvider</span><strong>{{ reportDebugMeta.llmProvider }}</strong></p>
              <p><span>rawResponse</span><strong>{{ reportDebugMeta.hasRawResponse ? '存在' : '不存在' }}</strong></p>
              <p><span>promptSnapshot</span><strong>{{ reportDebugMeta.hasPromptSnapshot ? '存在' : '不存在' }}</strong></p>
              <p><span>contextSnapshot</span><strong>{{ reportDebugMeta.hasContextSnapshot ? '存在' : '不存在' }}</strong></p>
              <p><span>sessionId</span><strong>{{ agentSessionId ?? '-' }}</strong></p>
              <p><span>toolCalls</span><strong>{{ recentToolCalls.length }}</strong></p>
            </div>
            <div v-if="recentToolCalls.length > 0" class="debug-list compact">
              <article v-for="tool in recentToolCalls.slice(0, 5)" :key="tool.toolCallId" class="debug-record">
                <div>
                  <strong>{{ tool.displayName || tool.toolName }}</strong>
                  <el-tag size="small" :type="tool.status === 'SUCCESS' ? 'success' : tool.status === 'FAILED' ? 'danger' : 'warning'">{{ tool.status }}</el-tag>
                </div>
                <p>{{ tool.toolName }} · {{ tool.durationMs ?? 0 }} ms</p>
                <details>
                  <summary>arguments</summary>
                  <pre>{{ formatAgentPayload(tool.arguments) }}</pre>
                </details>
                <details>
                  <summary>result</summary>
                  <pre>{{ formatAgentPayload(tool.result) }}</pre>
                </details>
              </article>
            </div>
            <details v-if="currentReport?.rawResponse" class="raw-response">
              <summary>原始响应</summary>
              <pre>{{ currentReport.rawResponse }}</pre>
            </details>
            <el-empty v-if="debugRecords.length === 0" description="暂无调用" />
            <div v-else class="debug-list">
              <article v-for="record in debugRecords" :key="`${record.name}-${record.time}-${record.duration}`" class="debug-record">
                <div>
                  <strong>{{ record.name }}</strong>
                  <el-tag size="small" :type="record.status === 200 ? 'success' : 'danger'">{{ record.status }}</el-tag>
                </div>
                <p>{{ record.method }} {{ record.url }}</p>
                <p>{{ record.duration }} ms · {{ record.time }}</p>
                <details v-if="record.payload !== undefined">
                  <summary>payload</summary>
                  <pre>{{ JSON.stringify(record.payload, null, 2) }}</pre>
                </details>
                <details v-if="record.response !== undefined">
                  <summary>response</summary>
                  <pre>{{ JSON.stringify(record.response, null, 2) }}</pre>
                </details>
                <details v-if="record.error !== undefined">
                  <summary>error</summary>
                  <pre>{{ JSON.stringify(record.error, null, 2) }}</pre>
                </details>
              </article>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </aside>
  </div>
</template>

<style scoped>
.agent-workbench {
  height: calc(100vh - 112px);
  min-height: 680px;
  display: grid;
  grid-template-columns: 248px minmax(620px, 1fr) 328px;
  gap: 0;
  overflow: hidden;
  border-radius: 14px;
  background:
    radial-gradient(circle at 50% 0%, rgba(29, 111, 220, 0.08), transparent 32%),
    #f6f8fb;
  color: #172033;
}

.agent-workbench.left-collapsed {
  grid-template-columns: 56px minmax(620px, 1fr) 328px;
}

.agent-workbench.right-collapsed {
  grid-template-columns: 248px minmax(620px, 1fr) 0;
}

.agent-workbench.left-collapsed.right-collapsed {
  grid-template-columns: 56px minmax(620px, 1fr) 0;
}

.agent-workbench.right-collapsed .inspector {
  padding: 0;
  overflow: hidden;
  box-shadow: none;
}

button {
  font: inherit;
}

.session-rail,
.inspector {
  min-width: 0;
  background: rgba(255, 255, 255, 0.64);
  backdrop-filter: blur(16px);
}

.session-rail {
  display: flex;
  flex-direction: column;
  padding: 12px 10px;
  box-shadow: inset -1px 0 0 rgba(122, 139, 165, 0.12);
}

.rail-top,
.stage-header,
.header-actions,
.inspector-head,
.result-head,
.context-pills,
.quick-commands,
.side-actions {
  display: flex;
  align-items: center;
}

.rail-top {
  gap: 8px;
  margin-bottom: 12px;
}

.icon-button,
.tool-trigger {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 10px;
  background: rgba(23, 32, 51, 0.06);
  color: #34445f;
  cursor: pointer;
}

.icon-button:hover,
.tool-trigger:hover {
  background: rgba(29, 111, 220, 0.1);
}

.new-chat {
  flex: 1;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: #172033;
  color: #fff;
  cursor: pointer;
}

.session-search {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 0 10px;
  border-radius: 10px;
  background: rgba(23, 32, 51, 0.05);
  color: #7a879a;
}

.session-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: #7a879a;
}

.inline-alert {
  margin-bottom: 10px;
}

.sessions {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding-right: 2px;
}

.session-group {
  display: grid;
  gap: 4px;
  margin-bottom: 16px;
}

.group-label {
  margin: 0 0 4px 8px;
  color: #7a879a;
  font-size: 12px;
  font-weight: 700;
}

.session-item {
  width: 100%;
  min-height: 44px;
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) 24px;
  gap: 9px;
  align-items: center;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.session-item:hover,
.session-item.active {
  background: rgba(23, 32, 51, 0.06);
}

.session-item:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2fba74;
}

.status-dot.running {
  background: #d89822;
}

.status-dot.failed {
  background: #d94c4c;
}

.status-dot.muted {
  background: #9aa7b8;
}

.session-text {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.session-text strong,
.session-text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-text strong {
  font-size: 13px;
  font-weight: 700;
}

.session-text small,
.archive-line button,
.restore-button {
  color: #7a879a;
  font-size: 12px;
}

.more-button {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: #7a879a;
  opacity: 0;
}

.session-item:hover .more-button {
  opacity: 1;
}

.archive-line {
  margin-top: auto;
  padding-top: 8px;
}

.archive-line button,
.restore-button {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.archive-line button {
  width: 100%;
  display: flex;
  justify-content: space-between;
  padding: 8px;
}

.chat-stage {
  min-width: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  background: rgba(250, 252, 255, 0.82);
}

.stage-header {
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
}

.agent-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-mark,
.avatar,
.user-chip span {
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-weight: 800;
}

.agent-mark {
  width: 34px;
  height: 34px;
  background: #172033;
  color: #fff;
  font-size: 12px;
}

.agent-title h1,
.agent-title p,
.message-bubble p,
.result-card p,
.context-list p,
.tool-groups p,
.preview-pane h3,
.preview-pane h4,
.preview-pane p,
.debug-record p {
  margin: 0;
}

.agent-title h1 {
  font-size: 17px;
  line-height: 1.2;
}

.agent-title p {
  margin-top: 2px;
  color: #7a879a;
  font-size: 12px;
}

.header-actions {
  gap: 8px;
}

.user-chip {
  height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px 0 4px;
  border: 0;
  border-radius: 999px;
  background: rgba(23, 32, 51, 0.06);
  color: #172033;
  cursor: pointer;
}

.user-chip span {
  width: 26px;
  height: 26px;
  background: #fff;
  color: #1d6fdc;
  font-size: 12px;
}

.message-stream {
  min-height: 0;
  overflow: auto;
  padding: 22px min(8vw, 88px) 10px;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.message-row.role-user {
  justify-content: flex-end;
}

.message-row.role-system {
  justify-content: center;
}

.avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  background: rgba(23, 32, 51, 0.08);
  color: #34445f;
  font-size: 11px;
}

.role-user .avatar {
  order: 2;
  background: #1d6fdc;
  color: #fff;
}

.role-tool .avatar {
  background: #eef4f1;
  color: #2f7f5f;
}

.message-bubble {
  max-width: min(760px, 78%);
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 8px 24px rgba(20, 35, 60, 0.06);
}

.role-user .message-bubble {
  background: #172033;
  color: #fff;
  border-bottom-right-radius: 6px;
}

.role-agent .message-bubble {
  border-bottom-left-radius: 6px;
}

.role-system .message-bubble {
  max-width: none;
  padding: 7px 11px;
  background: rgba(23, 32, 51, 0.05);
  color: #7a879a;
  box-shadow: none;
  font-size: 12px;
}

.role-tool .message-bubble {
  background: rgba(238, 244, 241, 0.9);
  box-shadow: none;
}

.type-error .message-bubble {
  background: #fff1f1;
  color: #a43d3d;
}

.message-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  color: #7a879a;
  font-size: 12px;
}

.role-user .message-meta {
  color: rgba(255, 255, 255, 0.72);
}

.message-bubble p {
  line-height: 1.65;
  white-space: pre-wrap;
}

.result-card {
  display: grid;
  gap: 10px;
  margin-top: 12px;
  padding: 12px;
  border-radius: 12px;
  background: #f7f9fc;
}

.result-head {
  justify-content: space-between;
  gap: 10px;
}

.result-head button {
  border: 0;
  background: transparent;
  color: #1d6fdc;
  cursor: pointer;
}

.result-card section {
  display: grid;
  gap: 4px;
}

.result-card span {
  color: #7a879a;
  font-size: 12px;
  font-weight: 700;
}

.result-card p {
  display: -webkit-box;
  overflow: hidden;
  color: #34445f;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.agent-tool-card,
.approval-card {
  display: grid;
  gap: 8px;
  margin-top: 10px;
  padding: 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px rgba(122, 139, 165, 0.14);
}

.agent-tool-card > div,
.approval-card > div,
.approval-actions {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.agent-tool-card pre,
.approval-card pre {
  max-height: 180px;
  overflow: auto;
  margin: 0;
  padding: 8px;
  border-radius: 8px;
  background: #111827;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.agent-tool-card small {
  color: #59687d;
  line-height: 1.55;
}

.agent-tool-card.success {
  box-shadow: inset 0 0 0 1px rgba(47, 186, 116, 0.24);
}

.agent-tool-card.failed {
  box-shadow: inset 0 0 0 1px rgba(217, 76, 76, 0.28);
}

.approval-card {
  background: #fff8eb;
}

.tool-activity {
  display: none;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
  padding: 8px min(8vw, 88px);
}

.tool-activity.active {
  display: grid;
}

.activity-step {
  min-width: 0;
  display: grid;
  grid-template-columns: 7px minmax(0, 1fr);
  gap: 4px 7px;
  align-items: center;
  padding: 7px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  color: #7a879a;
}

.activity-step span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #aab6c5;
}

.activity-step.success span {
  background: #2fba74;
}

.activity-step.running span {
  background: #d89822;
}

.activity-step.failed span {
  background: #d94c4c;
}

.activity-step strong,
.activity-step small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-step strong {
  color: #34445f;
  font-size: 12px;
}

.activity-step small {
  grid-column: 2;
  font-size: 11px;
}

.composer-wrap {
  position: relative;
  padding: 10px min(8vw, 88px) 18px;
}

.context-pills,
.quick-commands {
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 7px;
}

.context-pills button,
.quick-commands button {
  border: 0;
  border-radius: 999px;
  background: rgba(23, 32, 51, 0.06);
  color: #59687d;
  font-size: 12px;
  cursor: pointer;
}

.context-pills button {
  padding: 5px 9px;
}

.context-pills button.on {
  background: rgba(29, 111, 220, 0.1);
  color: #1d6fdc;
}

.composer {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: end;
  padding: 10px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow:
    0 18px 42px rgba(20, 35, 60, 0.12),
    0 0 0 1px rgba(122, 139, 165, 0.1);
}

.composer textarea {
  min-height: 36px;
  max-height: 140px;
  resize: vertical;
  border: 0;
  outline: 0;
  padding: 8px 0;
  background: transparent;
  color: #172033;
  line-height: 1.55;
}

.send-button {
  height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 14px;
  background: #172033;
  color: #fff;
  cursor: pointer;
}

.send-button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.quick-commands {
  margin: 8px 0 0;
}

.quick-commands button {
  padding: 4px 8px;
}

.slash-menu {
  position: absolute;
  left: min(8vw, 88px);
  bottom: 104px;
  width: min(420px, calc(100% - 176px));
  display: grid;
  gap: 4px;
  padding: 8px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 42px rgba(20, 35, 60, 0.16);
  z-index: 5;
}

.slash-menu button {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  border: 0;
  border-radius: 10px;
  padding: 9px 10px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.slash-menu button:hover {
  background: rgba(23, 32, 51, 0.06);
}

.slash-menu strong {
  color: #1d6fdc;
}

.slash-menu small {
  color: #7a879a;
}

.inspector {
  position: relative;
  padding: 12px;
  box-shadow: inset 1px 0 0 rgba(122, 139, 165, 0.1);
}

.inspector-head {
  justify-content: space-between;
  height: 34px;
  margin-bottom: 8px;
}

.floating-open {
  display: none;
}

.inspector-tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.inspector-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.inspector-tabs :deep(.el-tabs__content) {
  height: calc(100vh - 214px);
  min-height: 560px;
  overflow: auto;
}

.mini-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.agent-options {
  margin-top: 12px;
}

.context-list.standalone {
  margin-top: 0;
}

.mini-form label {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.mini-form label.wide,
.switches {
  grid-column: 1 / -1;
}

.mini-form span,
.context-list span {
  color: #7a879a;
  font-size: 12px;
  font-weight: 700;
}

.mini-form :deep(.el-input-number),
.mini-form :deep(.el-input) {
  width: 100%;
}

.switches {
  display: flex;
  gap: 12px;
  align-items: center;
}

.context-list {
  display: grid;
  gap: 8px;
  margin: 14px 0;
}

.context-list p {
  display: grid;
  gap: 3px;
  padding: 9px 0;
  box-shadow: inset 0 -1px 0 rgba(122, 139, 165, 0.12);
}

.context-list strong {
  overflow-wrap: anywhere;
}

.side-actions {
  gap: 8px;
}

.tool-groups {
  display: grid;
  gap: 16px;
}

.tool-groups section {
  display: grid;
  gap: 6px;
}

.tool-groups p {
  color: #7a879a;
  font-size: 12px;
  font-weight: 800;
}

.tool-groups button {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  border: 0;
  border-radius: 12px;
  padding: 9px 10px;
  background: rgba(23, 32, 51, 0.045);
  text-align: left;
  cursor: pointer;
}

.tool-groups button:hover {
  background: rgba(29, 111, 220, 0.08);
}

.tool-groups span {
  display: grid;
  gap: 2px;
}

.tool-groups small,
.tool-groups em {
  color: #7a879a;
  font-size: 12px;
  font-style: normal;
}

.tool-groups em.connected {
  color: #2f8f62;
}

.tool-groups em.available {
  color: #1d6fdc;
}

.result-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.preview-pane article {
  display: grid;
  gap: 10px;
}

.report-title-line {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.preview-pane h3 {
  font-size: 16px;
}

.preview-pane section {
  display: grid;
  gap: 5px;
  padding: 10px 0;
  box-shadow: inset 0 -1px 0 rgba(122, 139, 165, 0.12);
}

.preview-pane h4 {
  font-size: 13px;
}

.preview-pane p {
  color: #34445f;
  line-height: 1.7;
  white-space: pre-wrap;
}

.summary-card {
  display: grid;
  gap: 5px;
  padding: 10px;
  border-radius: 8px;
  background: rgba(29, 111, 220, 0.07);
}

.summary-card span,
.meta-grid span {
  color: #7a879a;
  font-size: 12px;
  font-weight: 800;
}

.section-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 12px;
}

.suggestion-list {
  margin: 0;
  padding-left: 18px;
  color: #34445f;
  line-height: 1.7;
}

.markdown-preview,
.raw-response pre {
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 10px;
  border-radius: 8px;
  background: #111827;
  color: #e5eef9;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.meta-grid,
.debug-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.meta-grid p,
.debug-meta p {
  min-width: 0;
  display: grid;
  gap: 3px;
  margin: 0;
  padding: 8px;
  border-radius: 8px;
  background: rgba(23, 32, 51, 0.045);
}

.meta-grid strong,
.debug-meta strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #34445f;
  font-size: 12px;
}

.raw-response {
  margin-bottom: 10px;
}

.debug-list {
  display: grid;
  gap: 10px;
}

.debug-record {
  display: grid;
  gap: 7px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(23, 32, 51, 0.045);
}

.debug-record div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.debug-record p {
  color: #59687d;
  font-size: 12px;
}

.debug-record summary {
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.debug-record pre {
  max-height: 220px;
  overflow: auto;
  margin: 7px 0 0;
  padding: 10px;
  border-radius: 10px;
  background: #111827;
  color: #e5eef9;
  font-size: 12px;
  white-space: pre-wrap;
}

@media (max-width: 1320px) {
  .agent-workbench {
    grid-template-columns: 220px minmax(520px, 1fr) 300px;
  }

  .message-stream,
  .composer-wrap,
  .tool-activity {
    padding-left: 36px;
    padding-right: 36px;
  }

  .slash-menu {
    left: 36px;
    width: min(420px, calc(100% - 72px));
  }

  .tool-activity {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 980px) {
  .agent-workbench,
  .agent-workbench.left-collapsed,
  .agent-workbench.right-collapsed,
  .agent-workbench.left-collapsed.right-collapsed {
    height: auto;
    grid-template-columns: 1fr;
  }

  .session-rail,
  .inspector {
    min-height: 220px;
  }

  .message-stream {
    min-height: 420px;
  }

  .inspector-tabs :deep(.el-tabs__content) {
    height: auto;
    min-height: 320px;
  }
}
</style>
