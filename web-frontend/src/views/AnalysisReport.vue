<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import PvGenerativeUi from '../components/agent/PvGenerativeUi.vue'
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
  type AgentApprovalRecord,
  type AgentMessageRecord,
  type AgentSession,
  type AgentToolCallRecord,
  type AgentToolInfo,
  type AgentSseEnvelope
} from '../api/agent'

type MessageRole = 'user' | 'assistant' | 'system'
type MessageKind = 'text' | 'working' | 'approval' | 'error'
type StepStatus = 'pending' | 'running' | 'success' | 'failed' | 'waiting' | 'cancelled'

interface AgentStep {
  key: string
  title: string
  detail: string
  status: StepStatus
}

interface ToolCard {
  id: string
  toolName: string
  title: string
  status: StepStatus
  summary: string
  highlights?: string[]
  durationMs?: number
  detail?: string
}

interface ApprovalCard {
  approvalId: number
  toolCallId?: string
  toolName: string
  title: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  fields: Array<{ label: string; value: string }>
}

interface PvUiInstruction {
  component: string
  props: Record<string, unknown>
}

interface ChatMessage {
  id: number
  anchorId: string
  role: MessageRole
  kind: MessageKind
  content: string
  time: string
  steps?: AgentStep[]
  tools?: ToolCard[]
  uiInstructions?: PvUiInstruction[]
  approval?: ApprovalCard
  finalActions?: boolean
}

interface SlashCommand {
  command: string
  label: string
  hint: string
  template: string
}

type SignatureRole = 'author' | 'reviewer' | 'confirmer'

const userStore = useUserStore()

const agentSessions = ref<AgentSession[]>([])
const agentTools = ref<AgentToolInfo[]>([])
const agentSessionId = ref<number | null>(null)
const currentAssistantId = ref<number | null>(null)
const leftCollapsed = ref(false)
const showArchived = ref(false)
const showSlashMenu = ref(false)
const slashActiveIndex = ref(0)
const composerText = ref('')
const composerRef = ref<HTMLTextAreaElement>()
const messageStreamRef = ref<HTMLElement>()
const activeQuestionId = ref('')
const loadingHistory = ref(false)
const loadingMessages = ref(false)
const running = ref(false)
const historyError = ref('')
const messages = ref<ChatMessage[]>([])
const signatureDialogVisible = ref(false)
const signatureTargetMessageId = ref<number | null>(null)
const signatureForm = ref({
  author: '',
  reviewer: '',
  confirmer: ''
})
const signatureImages = ref<Record<SignatureRole, string>>({
  author: '',
  reviewer: '',
  confirmer: ''
})
const activeSignatureRole = ref<SignatureRole>('author')
const signatureCanvasRef = ref<HTMLCanvasElement>()
const drawingSignature = ref(false)

const slashCommands: SlashCommand[] = [
  { command: '/station', label: '查询电站', hint: '查看电站基础信息', template: '查询电站信息 ' },
  { command: '/weather', label: '查询天气', hint: '分析电站天气影响', template: '查询电站天气 ' },
  { command: '/predict', label: '查看预测', hint: '解读预测任务结果', template: '解释预测任务 ' },
  { command: '/report', label: '生成报告', hint: '创建综合分析报告', template: '生成综合分析报告 ' },
  { command: '/model', label: '查看模型', hint: '查询模型能力', template: '查询模型信息 ' },
  { command: '/api', label: 'API 使用', hint: '查看 API 调用情况', template: '查看 API 使用情况' },
  { command: '/news', label: '新闻通知', hint: '查看平台新闻和公告', template: '查看新闻通知' },
  { command: '/wallet', label: '钱包余额', hint: '查看开放平台钱包', template: '查看钱包余额' },
  { command: '/profile', label: '个人信息', hint: '查看当前账号资料', template: '查看我的个人信息' },
  { command: '/cloud', label: '云图预测', hint: '说明云图预测输入要求', template: '运行云图预测' },
  { command: '/dashboard', label: '平台概览', hint: '查看仪表盘状态', template: '查看仪表盘概览' },
  { command: '/pv', label: '实时功率', hint: '查询电站实时功率', template: '查询 1 号电站实时功率' },
  { command: '/notify', label: '通知', hint: '查看通知和未读数', template: '查看未读通知' }
]

const username = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || 'User')
const pinnedSessions = computed(() => agentSessions.value.filter((session) => session.pinned && !session.archived))
const recentSessions = computed(() => agentSessions.value.filter((session) => !session.pinned && !session.archived))
const archivedSessions = computed(() => agentSessions.value.filter((session) => session.archived))
const activeSessionTitle = computed(() => agentSessions.value.find((item) => item.sessionId === agentSessionId.value)?.title || '新的 Agent 会话')
const connected = computed(() => agentTools.value.some((tool) => tool.enabled))
const filteredCommands = computed(() => {
  const text = composerText.value.trim()
  if (!text.startsWith('/')) return slashCommands
  const command = text.split(/\s+/)[0]
  return slashCommands.filter((item) => item.command.includes(command))
})
const questionAnchors = computed(() => messages.value
  .filter((message) => message.role === 'user' && message.content.trim())
  .map((message, index) => ({
    id: message.anchorId,
    index: index + 1,
    title: compactQuestion(message.content)
  })))

function nowTime() {
  return new Date().toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function firstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function firstNumber(...values: unknown[]) {
  for (const value of values) {
    const number = typeof value === 'number' ? value : typeof value === 'string' ? Number(value.replace(/[^\d]/g, '')) : NaN
    if (Number.isFinite(number) && number > 0) return number
  }
  return null
}

function messageAnchorId(id: number) {
  return `agent-message-${String(id).replace(/[^\w-]/g, '-')}`
}

function compactQuestion(value: string) {
  const text = value.replace(/\s+/g, ' ').trim()
  if (!text) return '未命名问题'
  return text.length > 34 ? `${text.slice(0, 34)}...` : text
}

function recordValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function commandParts(text = composerText.value) {
  const parts = text.trim().split(/\s+/).filter(Boolean)
  return { command: parts[0] || '', args: parts.slice(1) }
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function inlineMarkdown(value: string) {
  return escapeHtml(value)
    .replace(/!\[([^\]]*)\]\((data:image\/png;base64,[A-Za-z0-9+/=]+)\)/g, '<img class="signature-image" alt="$1" src="$2" />')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
}

function renderMarkdown(value: string) {
  const lines = value.split(/\r?\n/)
  const html: string[] = []
  let inList = false
  let inCode = false
  let inTable = false
  const closeList = () => {
    if (inList) {
      html.push('</ul>')
      inList = false
    }
  }
  const closeTable = () => {
    if (inTable) {
      html.push('</tbody></table>')
      inTable = false
    }
  }
  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      closeList()
      closeTable()
      html.push(inCode ? '</code></pre>' : '<pre><code>')
      inCode = !inCode
      continue
    }
    if (inCode) {
      html.push(`${escapeHtml(line)}\n`)
      continue
    }
    if (!line.trim()) {
      closeList()
      closeTable()
      continue
    }
    if (/^\|(.+)\|$/.test(line.trim())) {
      closeList()
      const cells = line.trim().slice(1, -1).split('|').map((cell) => cell.trim())
      if (cells.every((cell) => /^:?-{3,}:?$/.test(cell))) continue
      if (!inTable) {
        html.push('<table><tbody>')
        inTable = true
      }
      html.push(`<tr>${cells.map((cell) => `<td>${inlineMarkdown(cell)}</td>`).join('')}</tr>`)
      continue
    }
    closeTable()
    const heading = line.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      closeList()
      closeTable()
      const level = heading[1].length + 2
      html.push(`<h${level}>${inlineMarkdown(heading[2])}</h${level}>`)
      continue
    }
    const bullet = line.match(/^\s*(?:[-*]|\d+[.)])\s+(.+)$/)
    if (bullet) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${inlineMarkdown(bullet[1])}</li>`)
      continue
    }
    closeList()
    closeTable()
    html.push(`<p>${inlineMarkdown(line)}</p>`)
  }
  closeList()
  closeTable()
  if (inCode) html.push('</code></pre>')
  return html.join('')
}

function activeStep(message: ChatMessage) {
  const steps = message.steps || []
  return [...steps].reverse().find((step) => ['running', 'waiting', 'failed'].includes(step.status)) || steps[steps.length - 1]
}

function createSystemMessage(): ChatMessage {
  const id = Date.now()
  return {
    id,
    anchorId: messageAnchorId(id),
    role: 'system',
    kind: 'text',
    content: '说出你要完成的任务。业务数据会由 Agent 自动选择工具查询。',
    time: nowTime()
  }
}

function addMessage(message: Omit<ChatMessage, 'id' | 'time' | 'anchorId'> & { anchorId?: string }) {
  const id = Date.now() + Math.random()
  const item: ChatMessage = {
    ...message,
    id,
    anchorId: message.anchorId || messageAnchorId(id),
    time: nowTime()
  }
  messages.value.push(item)
  if (item.role === 'user') activeQuestionId.value = item.anchorId
  scrollToBottom('smooth', true)
  return item
}

function scrollToBottom(behavior: ScrollBehavior = 'smooth', force = false) {
  nextTick(() => {
    const stream = messageStreamRef.value
    if (!stream) return
    requestAnimationFrame(() => {
      const distance = stream.scrollHeight - stream.scrollTop - stream.clientHeight
      if (force || distance < 220) {
        stream.scrollTo({ top: stream.scrollHeight, behavior })
      }
    })
  })
}

function scrollToMessage(anchorId: string) {
  const element = document.getElementById(anchorId)
  if (!element) return
  activeQuestionId.value = anchorId
  element.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onMessageScroll() {
  const stream = messageStreamRef.value
  if (!stream || questionAnchors.value.length === 0) return
  const top = stream.getBoundingClientRect().top
  let best = questionAnchors.value[0].id
  let bestDistance = Number.POSITIVE_INFINITY
  for (const question of questionAnchors.value) {
    const element = document.getElementById(question.id)
    if (!element) continue
    const distance = Math.abs(element.getBoundingClientRect().top - top - 16)
    if (distance < bestDistance) {
      bestDistance = distance
      best = question.id
    }
  }
  activeQuestionId.value = best
}

function activeAssistant() {
  let item = messages.value.find((message) => message.id === currentAssistantId.value)
  if (!item) {
    item = addMessage({
      role: 'assistant',
      kind: 'working',
      content: '',
      steps: [],
      tools: []
    })
    currentAssistantId.value = item.id
  }
  item.steps = item.steps || []
  item.tools = item.tools || []
  return item
}

function upsertStep(key: string, title: string, status: StepStatus, detail = '') {
  const assistant = activeAssistant()
  const steps = assistant.steps || []
  const index = steps.findIndex((item) => item.key === key)
  const next = { key, title, detail, status }
  if (index >= 0) steps[index] = { ...steps[index], ...next }
  else steps.push(next)
  assistant.steps = steps
  scrollToBottom('auto')
}

function upsertTool(card: ToolCard) {
  const assistant = activeAssistant()
  const tools = assistant.tools || []
  const index = tools.findIndex((item) => item.id === card.id || item.toolName === card.toolName)
  if (index >= 0) tools[index] = { ...tools[index], ...card }
  else tools.push(card)
  assistant.tools = tools
  scrollToBottom('auto')
}

const allowedPvComponents = new Set([
  'StationSummaryCard',
  'WeatherImpactCard',
  'PredictionTrendCard',
  'PowerMetricCard',
  'RiskAssessmentCard',
  'MaintenanceRecommendationCard',
  'ReportPreviewCard',
  'ApprovalActionCard',
  'ToolProgressCard',
  'ErrorRecoveryCard'
])

function addUiInstruction(value: unknown) {
  const data = recordValue(value)
  const component = firstText(data.component)
  const props = recordValue(data.props)
  if (!allowedPvComponents.has(component)) return
  const assistant = activeAssistant()
  assistant.uiInstructions = assistant.uiInstructions || []
  assistant.uiInstructions.push({ component, props })
  scrollToBottom('auto')
}

function primaryUiInstructions(instructions: PvUiInstruction[] = []) {
  return instructions.filter((instruction) => !['ErrorRecoveryCard', 'ToolProgressCard'].includes(instruction.component))
}

function uiTitle(instruction: PvUiInstruction) {
  const titles: Record<string, string> = {
    StationSummaryCard: '电站概览',
    WeatherImpactCard: '天气影响',
    PredictionTrendCard: '预测趋势',
    PowerMetricCard: '功率指标',
    RiskAssessmentCard: '风险摘要',
    MaintenanceRecommendationCard: '运维建议',
    ReportPreviewCard: '报告预览',
    ApprovalActionCard: '确认操作',
    ToolProgressCard: '工具进度',
    ErrorRecoveryCard: '错误恢复'
  }
  return titles[instruction.component] || instruction.component
}

function uiFields(instruction: PvUiInstruction) {
  const props = instruction.props
  const fields: Array<{ label: string; value: string }> = []
  const push = (label: string, ...values: unknown[]) => {
    const value = firstText(...values)
    if (value) fields.push({ label, value })
  }
  if (instruction.component === 'StationSummaryCard') {
    push('电站', props.stationName)
    push('容量', props.capacity !== undefined ? `${props.capacity} MW` : '')
    push('状态', props.status)
    push('位置', props.location)
  } else if (instruction.component === 'WeatherImpactCard') {
    push('天气', props.weather)
    push('温度', props.temperature !== undefined ? `${props.temperature}℃` : '')
    push('湿度', props.humidity !== undefined ? `${props.humidity}%` : '')
    push('风速', props.windSpeed !== undefined ? `${props.windSpeed} m/s` : '')
    push('影响', props.impact)
  } else if (instruction.component === 'PredictionTrendCard') {
    push('摘要', props.summary)
    const highlights = asStringArray(props.highlights)
    highlights.forEach((item, index) => fields.push({ label: index === 0 ? '要点' : '', value: item }))
  } else {
    Object.entries(props).forEach(([key, value]) => push(key, value))
  }
  return fields
}

function humanError(error: unknown) {
  if (error instanceof Error) return error.message
  return '请求失败'
}

function sessionTime(session: AgentSession) {
  return firstText(session.updatedAt, session.createdAt) || '无时间'
}

function sessionTitle(session: AgentSession) {
  const title = firstText(session.title)
  if (!title || title === '新的 Agent 会话') return '未命名任务'
  return title
}

function sessionDescription(session: AgentSession) {
  const status = sessionStatus(session)
  const time = sessionTime(session)
  if (!firstText(session.title) || session.title === '新的 Agent 会话') return `${status} · 等待首次提问 · ${time}`
  return `${status} · 最近更新 ${time}`
}

function sessionStatus(session: AgentSession) {
  const value = (session.status || '').toLowerCase()
  if (session.archived) return '已归档'
  if (value.includes('run') || value.includes('pending')) return '处理中'
  if (value.includes('fail') || value.includes('error')) return '异常'
  return '可继续'
}

function statusLabel(status: StepStatus) {
  if (status === 'running') return '进行中'
  if (status === 'success') return '完成'
  if (status === 'failed') return '失败'
  if (status === 'waiting') return '等待确认'
  if (status === 'cancelled') return '已取消'
  return '等待'
}

function stepMark(status: StepStatus) {
  if (status === 'success') return '✓'
  if (status === 'failed') return '!'
  if (status === 'waiting') return '…'
  if (status === 'cancelled') return '-'
  return '•'
}

function toolTitle(toolName: string, displayName?: string) {
  const local: Record<string, string> = {
    'station.list': '查询电站列表',
    'station.detail': '查询电站信息',
    'weather.current': '获取电站天气',
    'weather.location': '获取城市天气',
    'weather.forecast': '获取天气预报',
    'weather.locationForecast': '获取地点天气预报',
    'dashboard.overview': '查询仪表盘概览',
    'pv.realtime': '查询实时功率',
    'pv.history': '查询历史功率',
    'prediction.list': '读取预测任务',
    'prediction.detail': '读取预测结果',
    'report.generate': '生成综合分析报告',
    'report.conversation': '生成会话工作报告',
    'report.list': '查询历史报告',
    'report.detail': '读取报告详情',
    'model.list': '查询模型',
    'model.detail': '读取模型详情',
    'api.usage': '查看 API 使用情况',
    'api.list': '查询 API 服务',
    'user.profile': '查询个人信息',
    'user.profile.update': '修改个人资料',
    'wallet.balance': '查询钱包余额',
    'marketplace.list': '查询市场套餐',
    'marketplace.purchase': '购买市场套餐',
    'news.list': '查询新闻通知',
    'news.detail': '查询新闻详情',
    'notification.list': '查询通知列表',
    'notification.unreadCount': '查询未读通知数',
    'notification.markRead': '标记通知已读',
    'notification.markAllRead': '全部通知已读',
    'cloud.predict': '运行云图预测'
  }
  return local[toolName] || displayName || toolName
}

function asStringArray(value: unknown) {
  return Array.isArray(value) ? value.map((item) => firstText(item)).filter(Boolean) : []
}

function toolBusinessSummary(data: Record<string, unknown>) {
  const summary = firstText(data.summary, data.error)
  const highlights = asStringArray(data.highlights)
  if (summary) return summary
  if (highlights.length) return highlights[0]
  const preview = recordValue(data.dataPreview)
  if (data.toolName === 'weather.current') {
    return firstText(
      summary,
      [
        firstText(preview.weather) ? `当前天气 ${preview.weather}` : '',
        preview.temperature !== undefined ? `温度 ${preview.temperature}℃` : '',
        preview.humidity !== undefined ? `湿度 ${preview.humidity}%` : '',
        preview.cloud !== undefined ? `云量 ${preview.cloud}%` : ''
      ].filter(Boolean).join('，')
    )
  }
  if (data.toolName === 'report.generate') {
    const reportId = firstText(preview.reportId, preview.id)
    return reportId ? `报告已生成，编号 ${reportId}` : summary
  }
  return summary || '工具已返回结果'
}

function approvalFields(args: Record<string, unknown>) {
  const fields: Array<{ label: string; value: string }> = []
  const stationId = firstText(args.stationId)
  const taskId = firstText(args.taskId)
  if (stationId) fields.push({ label: '电站', value: stationId })
  if (taskId) fields.push({ label: '预测任务', value: taskId })
  if (args.includeWeather !== undefined) fields.push({ label: '包含天气', value: args.includeWeather ? '是' : '否' })
  if (args.includePrediction !== undefined) fields.push({ label: '包含预测', value: args.includePrediction ? '是' : '否' })
  const title = firstText(args.title)
  if (title) fields.push({ label: '标题', value: title })
  return fields
}

function mapToolRecord(tool: AgentToolCallRecord): ToolCard {
  const result = recordValue(tool.result)
  const data = {
    toolName: tool.toolName,
    summary: firstText(result.summary, tool.errorMessage),
    highlights: asStringArray(result.highlights),
    error: firstText(tool.errorMessage, result.errorMessage),
    dataPreview: result.data ?? result.raw ?? {}
  }
  return {
    id: tool.clientToolCallId || String(tool.toolCallId),
    toolName: tool.toolName,
    title: toolTitle(tool.toolName, tool.displayName),
    status: tool.status === 'SUCCESS' ? 'success' : tool.status === 'FAILED' ? 'failed' : tool.status === 'AWAITING_APPROVAL' ? 'waiting' : 'running',
    summary: toolBusinessSummary(data),
    highlights: data.highlights,
    durationMs: tool.durationMs,
    detail: firstText(data.error)
  }
}

function mapApproval(record: AgentApprovalRecord): ApprovalCard {
  const args = recordValue(record.arguments)
  return {
    approvalId: record.approvalId,
    toolCallId: String(record.toolCallId),
    toolName: record.toolName || '',
    title: toolTitle(record.toolName || ''),
    reason: firstText(record.reason, '该操作会写入平台数据，需要确认。'),
    status: record.status === 'APPROVED' ? 'APPROVED' : record.status === 'REJECTED' ? 'REJECTED' : 'PENDING',
    fields: approvalFields(args)
  }
}

function mapHistoryMessage(record: AgentMessageRecord): ChatMessage {
  const role: MessageRole = record.role === 'user' ? 'user' : record.role === 'assistant' ? 'assistant' : 'system'
  const pendingApproval = record.approvals?.find((approval) => approval.status === 'PENDING')
  const tools = (record.toolCalls || []).filter((tool) => tool.status !== 'AWAITING_APPROVAL').map(mapToolRecord)
  return {
    id: record.messageId,
    anchorId: messageAnchorId(record.messageId),
    role,
    kind: pendingApproval ? 'approval' : tools.some((tool) => tool.status === 'failed') ? 'error' : 'text',
    content: record.content || (pendingApproval ? 'Agent 需要你的确认' : ''),
    time: record.createdAt ? new Date(record.createdAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : nowTime(),
    tools,
    approval: pendingApproval ? mapApproval(pendingApproval) : undefined,
    finalActions: role === 'assistant' && Boolean(record.content)
  }
}

function agentContext() {
  return {
    conversationMode: 'independent_agent'
  }
}

function visibleIntentName(data: Record<string, unknown>) {
  const toolName = firstText(data.toolName)
  if (toolName) return toolTitle(toolName)
  if (data.businessRelated) return '业务分析'
  return '对话回复'
}

function handleAgentEvent(event: AgentSseEnvelope) {
  const data = event.data || {}
  if (event.event === 'started') {
    agentSessionId.value = firstNumber(data.sessionId, agentSessionId.value)
    activeAssistant().content = ''
    upsertStep('intent', '理解用户意图', 'running', '正在分析任务目标')
    return
  }
  if (event.event === 'thinking') {
    upsertStep('intent', '理解用户意图', 'success', firstText(data.text, '已开始处理'))
    upsertStep('plan', '选择执行路径', 'running', '正在决定需要调用的工具')
    return
  }
  if (event.event === 'intent_resolved') {
    upsertStep('plan', '选择执行路径', 'success', `已识别任务类型：${visibleIntentName(data)}`)
    return
  }
  if (event.event === 'plan') {
    upsertStep('plan', '制定执行计划', 'success', firstText(data.reason, '已确定工具调用计划'))
    return
  }
  if (event.event === 'tool_call') {
    const toolName = firstText(data.toolName)
    const title = toolTitle(toolName, firstText(data.displayName))
    upsertStep(`tool-${toolName}`, title, 'running', '正在调用平台数据')
    upsertTool({
      id: firstText(data.toolCallId, toolName),
      toolName,
      title,
      status: 'running',
      summary: '正在执行',
      highlights: []
    })
    return
  }
  if (event.event === 'tool_result') {
    const toolName = firstText(data.toolName, data.tool_name)
    const ok = data.success === true || firstText(data.status) === 'success'
    const title = toolTitle(toolName, firstText(data.displayName))
    const summary = toolBusinessSummary(data)
    upsertStep(`tool-${toolName}`, title, ok ? 'success' : 'failed', summary)
    upsertTool({
      id: firstText(data.toolCallId, toolName),
      toolName,
      title,
      status: ok ? 'success' : 'failed',
      summary,
      highlights: asStringArray(data.highlights),
      durationMs: firstNumber(data.durationMs) || undefined,
      detail: ok ? '' : firstText(data.error)
    })
    if (ok && toolName === 'user.profile.update') {
      void userStore.fetchProfile().then(() => {
        window.dispatchEvent(new CustomEvent('pv:user-profile-updated'))
      })
    }
    return
  }
  if (event.event === 'step_started') {
    const stepId = firstText(data.stepId, data.step_id, data.title)
    upsertStep(stepId, firstText(data.title, '执行步骤'), 'running', firstText(data.purpose, data.detail))
    return
  }
  if (event.event === 'step_completed') {
    const stepId = firstText(data.stepId, data.step_id, data.title)
    upsertStep(stepId, firstText(data.title, '执行步骤'), 'success', firstText(data.detail, '已完成'))
    return
  }
  if (event.event === 'ui_instruction') {
    addUiInstruction(data)
    return
  }
  if (event.event === 'approval_required') {
    const assistant = activeAssistant()
    const toolName = firstText(data.toolName)
    assistant.kind = 'approval'
    assistant.content = 'Agent 需要你的确认'
    assistant.approval = {
      approvalId: firstNumber(data.approvalId) || 0,
      toolCallId: firstText(data.toolCallId),
      toolName,
      title: toolTitle(toolName, firstText(data.displayName)),
      reason: firstText(data.reason, '该操作会写入平台数据，需要确认。'),
      status: 'PENDING',
      fields: approvalFields(recordValue(data.arguments))
    }
    upsertStep(`tool-${toolName}`, assistant.approval.title, 'waiting', '等待确认')
    return
  }
  if (event.event === 'token') {
    const text = firstText(data.token, data.text)
    if (text) activeAssistant().content += text
    return
  }
  if (event.event === 'final') {
    const assistant = activeAssistant()
    assistant.kind = assistant.approval?.status === 'PENDING' ? 'approval' : 'text'
    assistant.content = firstText(data.markdown, data.content) || assistant.content || '任务已完成'
    assistant.finalActions = true
    upsertStep('final', '生成结论', 'success', '已完成')
    currentAssistantId.value = null
    scrollToBottom()
    return
  }
  if (event.event === 'run_completed') {
    const assistant = activeAssistant()
    assistant.kind = 'text'
    assistant.content = firstText(data.answer, data.content) || assistant.content || '任务已完成'
    assistant.finalActions = true
    upsertStep('final', '生成结论', 'success', '已完成')
    currentAssistantId.value = null
    scrollToBottom()
    return
  }
  if (event.event === 'error') {
    const assistant = activeAssistant()
    assistant.kind = 'error'
    assistant.content = firstText(data.message, 'Agent 执行失败')
    upsertStep('error', '处理失败', 'failed', firstText(data.detail))
    currentAssistantId.value = null
  }
}

async function runAgentChat(message: string, approvalId?: number | null) {
  if (running.value) return
  running.value = true
  showSlashMenu.value = false
  currentAssistantId.value = null
  if (!approvalId) {
    addMessage({ role: 'user', kind: 'text', content: message })
  }
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
    addMessage({ role: 'assistant', kind: 'error', content: `Agent 请求失败：${humanError(error)}` })
  } finally {
    running.value = false
    void loadAgentSessions()
    if (agentSessionId.value) void getAgentSessionToolCalls(agentSessionId.value).catch(() => undefined)
  }
}

async function decideApproval(message: ChatMessage, approved: boolean) {
  const approval = message.approval
  if (!approval?.approvalId || running.value) return
  await approveAgentApproval(approval.approvalId, approved, approved ? '同意执行' : '取消')
  approval.status = approved ? 'APPROVED' : 'REJECTED'
  upsertStep(`tool-${approval.toolName}`, approval.title, approved ? 'running' : 'cancelled', approved ? '已确认，正在执行' : '已取消')
  addMessage({ role: 'user', kind: 'text', content: approved ? '确认执行' : '取消执行' })
  await runAgentChat(approved ? '继续执行已确认的操作' : '取消刚才的操作', approval.approvalId)
}

async function loadAgentSessions() {
  loadingHistory.value = true
  historyError.value = ''
  try {
    const result = await getAgentSessions({ page: 1, size: 50 })
    agentSessions.value = result.records || []
  } catch (error) {
    agentSessions.value = []
    historyError.value = `会话加载失败：${humanError(error)}`
  } finally {
    loadingHistory.value = false
  }
}

async function loadAgentMessages(sessionId: number) {
  loadingMessages.value = true
  try {
    agentSessionId.value = sessionId
    currentAssistantId.value = null
    const history = await getAgentSessionMessages(sessionId)
    messages.value = history.length > 0 ? history.map(mapHistoryMessage) : [createSystemMessage()]
    scrollToBottom('auto', true)
  } catch (error) {
    addMessage({ role: 'assistant', kind: 'error', content: `会话加载失败：${humanError(error)}` })
  } finally {
    loadingMessages.value = false
  }
}

async function createNewAgentSession() {
  const session = await createAgentSession('新的 Agent 会话')
  await loadAgentSessions()
  agentSessionId.value = session.sessionId
  messages.value = [createSystemMessage()]
  composerText.value = ''
  currentAssistantId.value = null
  focusComposer()
}

function newLocalSession() {
  agentSessionId.value = null
  messages.value = [createSystemMessage()]
  composerText.value = ''
  currentAssistantId.value = null
  focusComposer()
}

function focusComposer() {
  nextTick(() => composerRef.value?.focus())
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
    // cancelled
  }
}

async function deleteAgentSessionById(session: AgentSession) {
  try {
    await ElMessageBox.confirm('删除后会话不会再显示，历史消息仍保留。', '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteAgentSession(session.sessionId)
    if (agentSessionId.value === session.sessionId) newLocalSession()
    await loadAgentSessions()
  } catch {
    // cancelled
  }
}

async function handleSessionCommand(command: string, session: AgentSession) {
  if (command === 'pin') await togglePinSession(session)
  if (command === 'archive') await toggleArchiveSession(session)
  if (command === 'rename') await renameAgentSessionTitle(session)
  if (command === 'delete') await deleteAgentSessionById(session)
}

function applyCommand(command: SlashCommand) {
  composerText.value = command.template
  showSlashMenu.value = false
  slashActiveIndex.value = 0
  focusComposer()
}

function selectActiveSlashCommand() {
  const command = filteredCommands.value[slashActiveIndex.value]
  if (command) applyCommand(command)
}

function onComposerInput() {
  showSlashMenu.value = composerText.value.trim().startsWith('/')
  slashActiveIndex.value = 0
}

async function submitComposer() {
  const text = composerText.value.trim()
  if (!text) {
    ElMessage.warning('请输入任务')
    return
  }
  const { command, args } = commandParts(text)
  const knownSlash = slashCommands.find((item) => item.command === command)
  if (command.startsWith('/') && !knownSlash) {
    addMessage({ role: 'assistant', kind: 'error', content: `未知命令：${command}。可用命令：/station /weather /predict /report /model /api /news /wallet /profile /cloud /dashboard /pv /notify` })
    composerText.value = ''
    return
  }
  composerText.value = ''
  await runAgentChat(text)
}

function onComposerKeydown(event: KeyboardEvent) {
  if (showSlashMenu.value && filteredCommands.value.length > 0) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      slashActiveIndex.value = (slashActiveIndex.value + 1) % filteredCommands.value.length
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      slashActiveIndex.value = (slashActiveIndex.value - 1 + filteredCommands.value.length) % filteredCommands.value.length
      return
    }
    if (event.key === 'Tab') {
      event.preventDefault()
      selectActiveSlashCommand()
      return
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      selectActiveSlashCommand()
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      showSlashMenu.value = false
      return
    }
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void submitComposer()
  }
}

async function copyMessage(message: ChatMessage) {
  if (!message.content) return
  await navigator.clipboard.writeText(message.content)
  ElMessage.success('已复制')
}

function isFormalReport(message: ChatMessage) {
  return message.role === 'assistant' && message.content.includes('电子签名') && message.content.includes('| 角色 |')
}

function signatureValue(content: string, role: string) {
  const match = content.match(new RegExp(`\|\s*${role}\s*\|\s*([^|]+?)\s*\|`))
  const value = match?.[1]?.trim() || ''
  return value.includes('____') || value.includes('data:image/png') ? '' : value
}

function signatureImageValue(content: string, role: string) {
  const row = content.split(/\r?\n/).find((line) => new RegExp(`^\|\s*${role}\s*\|`).test(line)) || ''
  const match = row.match(/!\[[^\]]*\]\((data:image\/png;base64,[A-Za-z0-9+/=]+)\)/)
  return match?.[1] || ''
}

function openSignatureDialog(message: ChatMessage) {
  signatureTargetMessageId.value = message.id
  signatureForm.value = {
    author: signatureValue(message.content, '编制人'),
    reviewer: signatureValue(message.content, '复核人'),
    confirmer: signatureValue(message.content, '确认人')
  }
  signatureImages.value = {
    author: signatureImageValue(message.content, '编制人'),
    reviewer: signatureImageValue(message.content, '复核人'),
    confirmer: signatureImageValue(message.content, '确认人')
  }
  activeSignatureRole.value = 'author'
  signatureDialogVisible.value = true
  nextTick(() => resetSignatureCanvas())
}

function roleLabel(role: SignatureRole) {
  if (role === 'author') return '编制人'
  if (role === 'reviewer') return '复核人'
  return '确认人'
}

function setSignatureRole(role: SignatureRole) {
  saveSignatureCanvas()
  activeSignatureRole.value = role
  nextTick(() => resetSignatureCanvas())
}

function canvasPoint(event: MouseEvent | TouchEvent) {
  const canvas = signatureCanvasRef.value
  if (!canvas) return null
  const rect = canvas.getBoundingClientRect()
  const source = 'touches' in event ? event.touches[0] || event.changedTouches[0] : event
  return {
    x: (source.clientX - rect.left) * (canvas.width / rect.width),
    y: (source.clientY - rect.top) * (canvas.height / rect.height)
  }
}

function signatureContext() {
  const canvas = signatureCanvasRef.value
  const context = canvas?.getContext('2d')
  if (!canvas || !context) return null
  context.lineWidth = 4
  context.lineCap = 'round'
  context.lineJoin = 'round'
  context.strokeStyle = '#172033'
  return context
}

function resetSignatureCanvas() {
  const canvas = signatureCanvasRef.value
  const context = signatureContext()
  if (!canvas || !context) return
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  const image = signatureImages.value[activeSignatureRole.value]
  if (image) {
    const img = new Image()
    img.onload = () => context.drawImage(img, 0, 0, canvas.width, canvas.height)
    img.src = image
  }
}

function beginSignature(event: MouseEvent | TouchEvent) {
  event.preventDefault()
  const context = signatureContext()
  const point = canvasPoint(event)
  if (!context || !point) return
  drawingSignature.value = true
  context.beginPath()
  context.moveTo(point.x, point.y)
}

function drawSignature(event: MouseEvent | TouchEvent) {
  if (!drawingSignature.value) return
  event.preventDefault()
  const context = signatureContext()
  const point = canvasPoint(event)
  if (!context || !point) return
  context.lineTo(point.x, point.y)
  context.stroke()
}

function endSignature() {
  if (!drawingSignature.value) return
  drawingSignature.value = false
  saveSignatureCanvas()
}

function saveSignatureCanvas() {
  const canvas = signatureCanvasRef.value
  if (!canvas) return
  signatureImages.value[activeSignatureRole.value] = canvas.toDataURL('image/png')
}

function clearSignatureCanvas() {
  signatureImages.value[activeSignatureRole.value] = ''
  resetSignatureCanvas()
}

function safeSignature(value: string) {
  const text = value.trim().replace(/[|\r\n]/g, ' ')
  return text || '________________'
}

function signatureCell(role: SignatureRole) {
  const image = signatureImages.value[role]
  if (image) return `![${roleLabel(role)}签名](${image})`
  return safeSignature(signatureForm.value[role])
}

function replaceSignatureRows(content: string) {
  const rows = content.split(/\r?\n/)
  return rows.map((row) => {
    if (/^\|\s*编制人\s*\|/.test(row)) return `| 编制人 | ${signatureCell('author')} | ____ 年 __ 月 __ 日 |`
    if (/^\|\s*复核人\s*\|/.test(row)) return `| 复核人 | ${signatureCell('reviewer')} | ____ 年 __ 月 __ 日 |`
    if (/^\|\s*确认人\s*\|/.test(row)) return `| 确认人 | ${signatureCell('confirmer')} | ____ 年 __ 月 __ 日 |`
    return row
  }).join('\n')
}

function applySignature() {
  saveSignatureCanvas()
  const message = messages.value.find((item) => item.id === signatureTargetMessageId.value)
  if (!message) return
  message.content = replaceSignatureRows(message.content)
  signatureDialogVisible.value = false
  ElMessage.success('手写签名已写入报告')
}

function printReport(message: ChatMessage) {
  if (!message.content) return
  const win = window.open('', '_blank')
  if (!win) {
    ElMessage.error('浏览器阻止了打印窗口')
    return
  }
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Agent 工作报告</title><style>
    body{margin:0;background:#eef1f5;color:#172033;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;}
    main{width:794px;min-height:1123px;margin:24px auto;padding:56px 64px;background:#fff;box-shadow:0 20px 60px rgba(31,45,61,.16);box-sizing:border-box;}
    h3:first-child{text-align:center;font-size:26px;margin:0 0 28px;} h4,h5{margin:24px 0 10px;}
    p{line-height:1.75;margin:0 0 12px;} ul{padding-left:22px;line-height:1.7;} li{margin:5px 0;}
    table{width:100%;border-collapse:collapse;margin:14px 0 20px;} td,th{border:1px solid #aeb8c6;padding:9px 10px;vertical-align:top;} tr:first-child td{background:#f4f7fb;font-weight:700;} .signature-image{max-width:150px;max-height:48px;vertical-align:middle;}
    blockquote{margin:20px 0 0;border-left:3px solid #1d6fdc;padding:8px 12px;background:#f4f7fb;color:#526179;}
    @media print{body{background:#fff;} main{width:auto;min-height:auto;margin:0;padding:0;box-shadow:none;} button{display:none;}}
  </style></head><body><main>${renderMarkdown(message.content)}</main><script>window.onload=()=>window.print()<\/script></body></html>`)
  win.document.close()
}

function continueWith(text: string) {
  composerText.value = text
  focusComposer()
}

function logout() {
  userStore.logout()
  window.location.href = '/login'
}

onMounted(() => {
  messages.value = [createSystemMessage()]
  void loadAgentSessions()
  void getAgentTools().then((tools) => { agentTools.value = tools }).catch(() => { agentTools.value = [] })
})
</script>

<template>
  <div class="agent-workbench" :class="{ 'left-collapsed': leftCollapsed }">
    <aside class="session-rail">
      <div class="rail-top">
        <button class="icon-button" type="button" @click="leftCollapsed = !leftCollapsed">
          {{ leftCollapsed ? '>' : '<' }}
        </button>
        <button v-if="!leftCollapsed" class="new-chat" type="button" @click="createNewAgentSession">新建会话</button>
      </div>

      <template v-if="!leftCollapsed">
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
              <span class="status-dot"></span>
              <span class="session-text">
                <strong>{{ sessionTitle(session) }}</strong>
                <small>{{ sessionDescription(session) }}</small>
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
            <el-empty v-if="!loadingHistory && recentSessions.length === 0" description="暂无会话" />
            <button
              v-for="session in recentSessions"
              :key="`recent-${session.sessionId}`"
              class="session-item"
              :class="{ active: session.sessionId === agentSessionId }"
              type="button"
              @click="loadAgentMessages(session.sessionId)"
            >
              <span class="status-dot"></span>
              <span class="session-text">
                <strong>{{ sessionTitle(session) }}</strong>
                <small>{{ sessionDescription(session) }}</small>
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
                <strong>{{ sessionTitle(session) }}</strong>
                <small>{{ sessionDescription(session) }}</small>
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
          <span class="agent-mark">PV</span>
          <div>
            <h1>智能体工作台</h1>
            <p>{{ activeSessionTitle }}</p>
          </div>
        </div>
        <div class="header-actions">
          <el-tag size="small" :type="connected ? 'success' : 'warning'" effect="plain">{{ connected ? '已连接' : '连接中' }}</el-tag>
          <el-dropdown trigger="click">
            <button class="user-chip" type="button">
              <span>{{ username.slice(0, 1).toUpperCase() }}</span>
              {{ username }}
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>用户中心</el-dropdown-item>
                <el-dropdown-item @click="showArchived = true">已归档</el-dropdown-item>
                <el-dropdown-item disabled>设置</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section
        ref="messageStreamRef"
        v-loading="loadingMessages"
        class="message-stream"
        @scroll="onMessageScroll"
      >
        <div
          v-for="message in messages"
          :key="message.id"
          :id="message.anchorId"
          class="message-row"
          :class="[`role-${message.role}`, `kind-${message.kind}`]"
        >
          <div v-if="message.role !== 'system'" class="avatar">
            {{ message.role === 'user' ? username.slice(0, 1).toUpperCase() : 'PV' }}
          </div>
          <article class="message-bubble">
            <div v-if="message.role !== 'system'" class="message-meta">
              <strong>{{ message.role === 'user' ? username : '光伏智能体' }}</strong>
              <span>{{ message.time }}</span>
            </div>
            <div v-if="message.steps?.length" class="process-strip" :class="activeStep(message)?.status">
              <span class="process-pulse">{{ stepMark(activeStep(message)?.status || 'pending') }}</span>
              <div>
                <strong>{{ activeStep(message)?.title || '正在处理' }}</strong>
                <small>{{ activeStep(message)?.detail || statusLabel(activeStep(message)?.status || 'pending') }}</small>
              </div>
            </div>

            <details v-if="message.steps?.length" class="run-card">
              <summary>
                <span>执行轨迹</span>
                <em>{{ message.steps.length }} 个步骤</em>
              </summary>
              <div class="run-steps">
                <div v-for="step in message.steps" :key="step.key" class="run-step" :class="step.status">
                  <span>{{ stepMark(step.status) }}</span>
                  <div>
                    <strong>{{ step.title }}</strong>
                    <small>{{ step.detail || statusLabel(step.status) }}</small>
                  </div>
                </div>
              </div>
            </details>

            <div
              v-if="message.content"
              class="message-text markdown-body"
              v-html="message.role === 'assistant' ? renderMarkdown(message.content) : escapeHtml(message.content)"
            ></div>

            <PvGenerativeUi
              v-if="primaryUiInstructions(message.uiInstructions).length"
              :instructions="primaryUiInstructions(message.uiInstructions)"
              class="pv-ui-grid"
            />

            <details v-if="message.tools?.length" class="diagnostic-panel">
              <summary>
                <span>诊断明细</span>
                <em>{{ message.tools.length }} 个工具调用</em>
              </summary>
              <div class="tool-list">
                <details v-for="tool in message.tools" :key="tool.id" class="tool-card" :class="tool.status">
                  <summary>
                    <span>
                      <strong>{{ tool.title }}</strong>
                      <small>{{ tool.summary }}</small>
                    </span>
                    <em>{{ statusLabel(tool.status) }}<template v-if="tool.durationMs"> · {{ tool.durationMs }} ms</template></em>
                  </summary>
                  <ul v-if="tool.highlights?.length" class="tool-highlights">
                    <li v-for="item in tool.highlights" :key="item">{{ item }}</li>
                  </ul>
                  <p v-else>{{ tool.detail || tool.summary }}</p>
                </details>
              </div>
            </details>

            <div v-if="message.approval" class="approval-card" :class="message.approval.status.toLowerCase()">
              <div class="approval-head">
                <strong>Agent 需要你的确认</strong>
                <el-tag size="small" type="warning">{{ message.approval.status === 'PENDING' ? '等待确认' : message.approval.status === 'APPROVED' ? '已确认' : '已取消' }}</el-tag>
              </div>
              <p>操作：{{ message.approval.title }}</p>
              <p>{{ message.approval.reason }}</p>
              <dl v-if="message.approval.fields.length">
                <div v-for="field in message.approval.fields" :key="field.label">
                  <dt>{{ field.label }}</dt>
                  <dd>{{ field.value }}</dd>
                </div>
              </dl>
              <div v-if="message.approval.status === 'PENDING'" class="approval-actions">
                <el-button size="small" type="primary" :loading="running" @click="decideApproval(message, true)">确认执行</el-button>
                <el-button size="small" :disabled="running" @click="decideApproval(message, false)">取消</el-button>
              </div>
            </div>

            <div v-if="message.finalActions && message.role === 'assistant'" class="final-actions">
              <button class="primary-action" type="button" @click="continueWith('总结当前聊天内容并生成正式 Markdown 工作报告，包含电子签名栏')">生成报告</button>
              <button v-if="isFormalReport(message)" type="button" @click="openSignatureDialog(message)">填写签名</button>
              <button v-if="isFormalReport(message)" type="button" @click="printReport(message)">打印报告</button>
              <button type="button" @click="copyMessage(message)">复制结论</button>
              <button type="button" @click="continueWith('继续分析：')">继续追问</button>
            </div>
          </article>
        </div>
      </section>

      <nav v-if="questionAnchors.length > 0" class="question-rail" aria-label="问题定位">
        <button
          v-for="question in questionAnchors"
          :key="question.id"
          type="button"
          :class="{ active: activeQuestionId === question.id }"
          @click="scrollToMessage(question.id)"
        >
          <span>{{ question.index }}</span>
          <strong>{{ question.title }}</strong>
        </button>
      </nav>

      <section class="composer-wrap">
        <div v-if="showSlashMenu && filteredCommands.length > 0" class="slash-menu">
          <button
            v-for="(command, index) in filteredCommands"
            :key="command.command"
            type="button"
            :class="{ active: index === slashActiveIndex }"
            @mouseenter="slashActiveIndex = index"
            @click="applyCommand(command)"
          >
            <strong>{{ command.command }}</strong>
            <span>{{ command.label }}</span>
            <small>{{ command.hint }}</small>
          </button>
        </div>

        <div class="composer">
          <button class="tool-trigger" type="button" @click="showSlashMenu = !showSlashMenu; slashActiveIndex = 0">/</button>
          <textarea
            ref="composerRef"
            v-model="composerText"
            rows="1"
            placeholder="输入任务，或用 / 选择能力"
            @input="onComposerInput"
            @keydown="onComposerKeydown"
          ></textarea>
          <button class="send-button" type="button" :disabled="running" @click="submitComposer">
            {{ running ? '执行中' : '发送' }}
          </button>
        </div>
      </section>
    </main>

    <el-dialog v-model="signatureDialogVisible" title="手写电子签名" width="560px" append-to-body @opened="resetSignatureCanvas">
      <div class="signature-role-tabs">
        <button type="button" :class="{ active: activeSignatureRole === 'author' }" @click="setSignatureRole('author')">编制人</button>
        <button type="button" :class="{ active: activeSignatureRole === 'reviewer' }" @click="setSignatureRole('reviewer')">复核人</button>
        <button type="button" :class="{ active: activeSignatureRole === 'confirmer' }" @click="setSignatureRole('confirmer')">确认人</button>
      </div>
      <div class="signature-pad-wrap">
        <canvas
          ref="signatureCanvasRef"
          class="signature-pad"
          width="900"
          height="260"
          @mousedown="beginSignature"
          @mousemove="drawSignature"
          @mouseup="endSignature"
          @mouseleave="endSignature"
          @touchstart="beginSignature"
          @touchmove="drawSignature"
          @touchend="endSignature"
          @touchcancel="endSignature"
        ></canvas>
      </div>
      <el-form label-width="88px" class="signature-form">
        <el-form-item :label="`${roleLabel(activeSignatureRole)}姓名`">
          <el-input v-model="signatureForm[activeSignatureRole]" placeholder="可选：填写姓名，未手写时使用" />
        </el-form-item>
      </el-form>
      <p class="signature-hint">用鼠标或触摸在上方区域手写签名；切换角色前会自动保存当前签名。</p>
      <template #footer>
        <el-button @click="clearSignatureCanvas">清空当前签名</el-button>
        <el-button @click="signatureDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="applySignature">写入报告</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.agent-workbench {
  height: calc(100vh - 88px);
  min-height: 0;
  display: grid;
  grid-template-columns: 252px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid rgba(130, 150, 180, 0.18);
  border-radius: 14px;
  background: #f3f7fd;
  color: #172033;
}

.agent-workbench.left-collapsed {
  grid-template-columns: 58px minmax(0, 1fr);
}

button {
  font: inherit;
}

.session-rail {
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 10px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: inset -1px 0 0 rgba(122, 139, 165, 0.16);
}

.rail-top,
.stage-header,
.header-actions,
.approval-actions,
.final-actions {
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
  border-radius: 8px;
  background: rgba(23, 32, 51, 0.06);
  color: #34445f;
  cursor: pointer;
}

.new-chat {
  flex: 1;
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: #172033;
  color: #fff;
  cursor: pointer;
}

.inline-alert {
  margin-bottom: 10px;
}

.sessions {
  min-height: 0;
  flex: 1;
  overflow: auto;
  scrollbar-gutter: stable;
}

.sessions::-webkit-scrollbar,
.message-stream::-webkit-scrollbar {
  width: 10px;
}

.sessions::-webkit-scrollbar-thumb,
.message-stream::-webkit-scrollbar-thumb {
  border: 3px solid transparent;
  border-radius: 999px;
  background: rgba(99, 116, 145, 0.34);
  background-clip: content-box;
}

.sessions::-webkit-scrollbar-thumb:hover,
.message-stream::-webkit-scrollbar-thumb:hover {
  background: rgba(74, 89, 116, 0.48);
  background-clip: content-box;
}

.session-group {
  display: grid;
  gap: 5px;
  margin-bottom: 18px;
}

.group-label {
  margin: 0 0 4px 8px;
  color: #7a879a;
  font-size: 12px;
  font-weight: 700;
}

.session-item {
  width: 100%;
  min-height: 62px;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) 24px;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  padding: 8px;
  background: transparent;
  text-align: left;
  color: #22304a;
  cursor: pointer;
}

.session-item:hover,
.session-item.active {
  background: rgba(29, 111, 220, 0.1);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #36c275;
}

.status-dot.muted {
  background: #9aa6b7;
}

.session-text {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.session-text strong,
.session-text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-text strong {
  font-size: 13px;
}

.session-text small {
  color: #7a879a;
  font-size: 12px;
  line-height: 1.35;
}

.more-button {
  color: #7a879a;
  cursor: pointer;
}

.archive-line button {
  width: 100%;
  border: 0;
  background: transparent;
  color: #617089;
  text-align: left;
  padding: 8px;
  cursor: pointer;
}

.chat-stage {
  position: relative;
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background:
    radial-gradient(circle at 78% 8%, rgba(90, 142, 255, 0.10), transparent 28%),
    linear-gradient(180deg, #f6f9ff 0%, #f3f7fd 100%);
}

.stage-header {
  justify-content: space-between;
  gap: 16px;
  padding: 14px 28px 12px;
  border-bottom: 1px solid rgba(130, 150, 180, 0.16);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
}

.agent-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-mark,
.avatar {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(180deg, #eaf2ff, #dbe9ff);
  color: #1d6fdc;
  border: 1px solid rgba(66, 126, 220, 0.20);
  font-weight: 800;
  font-size: 13px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,.8);
}

.agent-title h1 {
  margin: 0;
  font-size: 20px;
  letter-spacing: 0;
}

.agent-title p {
  margin: 2px 0 0;
  color: #7a879a;
  font-size: 13px;
}

.header-actions {
  gap: 8px;
}

.user-chip {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 999px;
  padding: 4px 10px 4px 4px;
  background: rgba(23, 32, 51, 0.06);
  color: #22304a;
  cursor: pointer;
}

.user-chip span {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #2f75e6;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.message-stream {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 28px 248px 28px 40px;
  scroll-behavior: smooth;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.message-row {
  display: flex;
  gap: 12px;
  margin: 0 auto 22px;
  width: 100%;
  max-width: 1120px;
  scroll-margin-top: 18px;
}

.message-row.role-user {
  justify-content: flex-end;
}

.message-row.role-user .avatar {
  order: 2;
  border-radius: 50%;
  background: #2f75e6;
  color: #fff;
}

.message-row.role-system {
  justify-content: center;
}

.message-bubble {
  max-width: min(980px, calc(100% - 56px));
  border-radius: 14px;
  padding: 0;
  background: transparent;
  box-shadow: none;
}

.role-user .message-bubble {
  max-width: min(720px, calc(100% - 56px));
  padding: 13px 16px 14px;
  border: 1px solid rgba(86, 128, 210, 0.18);
  background: #edf4ff;
  color: #1c2c46;
  box-shadow: 0 10px 28px rgba(80, 112, 180, 0.10);
}

.role-system .message-bubble {
  background: transparent;
  box-shadow: none;
  color: #7a879a;
  text-align: center;
}

.kind-error .message-bubble {
  background: #fff4f4;
  color: #b42318;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 10px;
  color: #7a879a;
  font-size: 12px;
  font-weight: 600;
}

.role-user .message-meta {
  color: #647798;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.message-text {
  margin: 0;
  line-height: 1.75;
}

.role-assistant .markdown-body {
  margin-top: 12px;
  border: 1px solid rgba(130, 150, 180, 0.18);
  border-radius: 16px;
  padding: 18px 22px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 16px 42px rgba(70, 96, 140, 0.08);
}

.role-assistant .message-meta {
  padding-left: 2px;
}


.process-strip,
.run-card,
.pv-ui-grid,
.diagnostic-panel,
.tool-list,
.approval-card,
.final-actions {
  margin-top: 12px;
}

.process-strip {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  max-width: 440px;
  padding: 9px 11px;
  border: 1px solid rgba(130, 150, 180, 0.18);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 34px rgba(70, 96, 140, 0.08);
}

.process-strip strong {
  display: block;
  font-size: 13px;
}

.process-strip small {
  color: #6b778c;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-pulse {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eef4fb;
  color: #1d6fdc;
  font-size: 12px;
  font-weight: 800;
}

.process-strip.running .process-pulse,
.process-strip.waiting .process-pulse {
  background: #fff4d8;
  color: #a05a00;
}

.process-strip.failed .process-pulse {
  background: #ffe8e8;
  color: #b42318;
}

.run-card {
  max-width: 560px;
  border: 1px solid rgba(130, 150, 180, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  overflow: hidden;
}

.run-card summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 9px 12px;
  color: #40506b;
  cursor: pointer;
  list-style: none;
}

.run-card summary::-webkit-details-marker {
  display: none;
}

.run-card summary span {
  font-size: 13px;
  font-weight: 700;
}

.run-card summary em {
  color: #7a879a;
  font-size: 12px;
  font-style: normal;
}

.run-card[open] summary {
  border-bottom: 1px solid rgba(130, 150, 180, 0.12);
}

.run-steps {
  display: grid;
  gap: 0;
}

.run-step {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 8px;
  padding: 8px 12px;
}

.run-step + .run-step {
  border-top: 0;
}

.run-step > span {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e8eef6;
  color: #617089;
  font-size: 12px;
  font-weight: 800;
}

.run-step.success > span {
  background: #e7f8ef;
  color: #138a4d;
}

.run-step.running > span,
.run-step.waiting > span {
  background: #fff4d8;
  color: #a05a00;
}

.run-step.failed > span {
  background: #ffe8e8;
  color: #b42318;
}

.run-step strong {
  display: block;
  font-size: 13px;
}

.run-step small {
  color: #7a879a;
}

.pv-ui-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
  max-width: 760px;
}

.pv-ui-card {
  border: 1px solid rgba(47, 117, 230, 0.18);
  border-radius: 8px;
  padding: 12px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(70, 96, 140, 0.07);
}

.pv-ui-card header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.pv-ui-card header strong {
  color: #1d2f4f;
  font-size: 14px;
}

.pv-ui-card header span {
  color: #8090a8;
  font-size: 11px;
}

.pv-ui-card dl {
  display: grid;
  gap: 6px;
  margin: 0;
}

.pv-ui-card dl div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
}

.pv-ui-card dt {
  color: #7a879a;
  font-size: 12px;
}

.pv-ui-card dd {
  margin: 0;
  color: #2b3950;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.pv-ui-card.WeatherImpactCard {
  border-color: rgba(22, 163, 74, 0.22);
}

.pv-ui-card.PredictionTrendCard {
  border-color: rgba(126, 87, 194, 0.22);
}

.tool-list {
  display: grid;
  gap: 8px;
}

.diagnostic-panel {
  max-width: 760px;
  border: 1px solid rgba(130, 150, 180, 0.14);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.64);
  overflow: hidden;
}

.diagnostic-panel > summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 9px 12px;
  color: #5f6e84;
  cursor: pointer;
  list-style: none;
}

.diagnostic-panel > summary::-webkit-details-marker {
  display: none;
}

.diagnostic-panel > summary span {
  font-size: 13px;
  font-weight: 700;
}

.diagnostic-panel > summary em {
  color: #8a97aa;
  font-size: 12px;
  font-style: normal;
}

.diagnostic-panel[open] {
  background: rgba(255, 255, 255, 0.88);
}

.diagnostic-panel .tool-list {
  padding: 0 10px 10px;
}

.tool-card {
  border: 1px solid rgba(130, 150, 180, 0.18);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.96);
  overflow: hidden;
  box-shadow: none;
}

.tool-card summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  cursor: pointer;
  list-style: none;
}

.tool-card summary::-webkit-details-marker {
  display: none;
}

.tool-card summary span {
  display: grid;
  gap: 3px;
}

.tool-card small,
.tool-card em {
  color: #7a879a;
  font-size: 12px;
  font-style: normal;
}

.tool-card p {
  margin: 0;
  padding: 0 12px 12px;
  color: #526179;
}

.tool-highlights {
  margin: 0;
  padding: 0 12px 12px 28px;
  color: #526179;
  line-height: 1.65;
}

.tool-highlights li + li {
  margin-top: 3px;
}

.tool-card.failed {
  border-color: rgba(180, 35, 24, 0.24);
}

.approval-card {
  border: 1px solid rgba(215, 138, 0, 0.28);
  border-radius: 8px;
  padding: 12px;
  background: #fffaf0;
}

.approval-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.approval-card p {
  margin: 6px 0;
}

.approval-card dl {
  display: grid;
  gap: 6px;
  margin: 10px 0;
}

.approval-card dl div {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px;
}

.approval-card dt {
  color: #7a879a;
}

.approval-card dd {
  margin: 0;
}

.approval-actions {
  gap: 8px;
}

.signature-role-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.signature-role-tabs button {
  border: 1px solid rgba(122, 139, 165, 0.24);
  border-radius: 8px;
  padding: 8px 10px;
  background: #fff;
  color: #34445f;
  cursor: pointer;
}

.signature-role-tabs button.active {
  border-color: rgba(29, 111, 220, 0.52);
  background: rgba(29, 111, 220, 0.08);
  color: #1d6fdc;
}

.signature-pad-wrap {
  border: 1px dashed rgba(122, 139, 165, 0.48);
  border-radius: 10px;
  padding: 8px;
  background: #f8fafc;
}

.signature-pad {
  width: 100%;
  height: 174px;
  display: block;
  border-radius: 8px;
  background: #fff;
  cursor: crosshair;
  touch-action: none;
}

.signature-form {
  padding-top: 14px;
}

.signature-hint {
  margin: 0;
  color: #7a879a;
  font-size: 12px;
}

.final-actions {
  gap: 8px;
  flex-wrap: wrap;
  max-width: 820px;
}

.final-actions button {
  min-height: 34px;
  border: 1px solid rgba(94, 112, 144, 0.18);
  border-radius: 999px;
  padding: 7px 14px;
  background: rgba(255, 255, 255, 0.92);
  color: #2b3950;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(70, 96, 140, 0.06);
}

.final-actions button:hover {
  border-color: rgba(29, 111, 220, 0.34);
  color: #1d6fdc;
}

.final-actions button.primary-action {
  border-color: #1f6eea;
  background: #1f6eea;
  color: #fff;
}

.composer-wrap {
  position: relative;
  max-width: 980px;
  width: calc(100% - 64px);
  margin: 0 auto 18px;
  pointer-events: none;
}

.composer {
  min-height: 58px;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid rgba(130, 150, 180, 0.20);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 44px rgba(70, 96, 140, 0.14);
  pointer-events: auto;
}

.question-rail {
  position: absolute;
  top: 78px;
  right: 22px;
  bottom: 98px;
  z-index: 4;
  width: 198px;
  display: grid;
  align-content: start;
  gap: 6px;
  overflow-y: auto;
  padding: 2px 2px 12px;
  opacity: 0.38;
  transition: opacity 0.18s ease, transform 0.18s ease;
  scrollbar-width: thin;
}

.question-rail:hover,
.question-rail:focus-within {
  opacity: 1;
}

.question-rail button {
  min-height: 40px;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  border: 1px solid rgba(130, 150, 180, 0.14);
  border-radius: 10px;
  padding: 7px 9px;
  background: rgba(255, 255, 255, 0.78);
  color: #5e6d84;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(70, 96, 140, 0.06);
}

.question-rail button:hover,
.question-rail button.active {
  border-color: rgba(29, 111, 220, 0.34);
  background: rgba(239, 246, 255, 0.98);
  color: #1d4f9f;
}

.question-rail span {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(29, 111, 220, 0.10);
  color: #1d6fdc;
  font-size: 12px;
  font-weight: 800;
}

.question-rail strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.composer textarea {
  width: 100%;
  min-height: 28px;
  max-height: 120px;
  resize: vertical;
  border: 0;
  outline: 0;
  color: #172033;
  line-height: 1.6;
}

.send-button {
  min-width: 72px;
  height: 38px;
  border: 0;
  border-radius: 12px;
  background: #172033;
  color: #fff;
  cursor: pointer;
}

.send-button:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.slash-menu {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 72px;
  z-index: 10;
  display: grid;
  gap: 4px;
  padding: 8px;
  border: 1px solid rgba(122, 139, 165, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 50px rgba(31, 45, 61, 0.16);
  pointer-events: auto;
}

.slash-menu button {
  display: grid;
  grid-template-columns: 92px 120px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  border: 0;
  border-radius: 8px;
  padding: 9px 10px;
  background: transparent;
  color: #22304a;
  text-align: left;
  cursor: pointer;
}

.slash-menu button:hover,
.slash-menu button.active {
  background: rgba(29, 111, 220, 0.08);
}

.slash-menu button.active strong {
  color: #1d6fdc;
}

.slash-menu small {
  color: #7a879a;
}

.markdown-body :deep(p) {
  margin: 0 0 12px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5) {
  margin: 16px 0 8px;
  color: #172033;
  line-height: 1.35;
}

.markdown-body :deep(h3:first-child),
.markdown-body :deep(h4:first-child),
.markdown-body :deep(h5:first-child) {
  margin-top: 0;
}

.markdown-body :deep(ul) {
  margin: 8px 0 14px;
  padding-left: 22px;
}

.markdown-body :deep(li) {
  margin: 5px 0;
}

.markdown-body :deep(code) {
  border-radius: 5px;
  padding: 2px 5px;
  background: rgba(23, 32, 51, 0.07);
  color: #172033;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.92em;
}

.markdown-body :deep(pre) {
  overflow: auto;
  margin: 12px 0;
  border-radius: 10px;
  padding: 12px;
  background: #172033;
  color: #f8fafc;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-body {
  color: #172033;
}

.markdown-body :deep(h3:first-child) {
  text-align: center;
  font-size: 24px;
  margin-bottom: 18px;
}

.markdown-body :deep(.signature-image) {
  max-width: 150px;
  max-height: 48px;
  display: inline-block;
  vertical-align: middle;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 14px 0 18px;
  background: #fff;
}

.markdown-body :deep(td),
.markdown-body :deep(th) {
  border: 1px solid rgba(122, 139, 165, 0.32);
  padding: 9px 10px;
  vertical-align: top;
}

.markdown-body :deep(tr:first-child td) {
  background: #f4f7fb;
  font-weight: 700;
}

.markdown-body :deep(blockquote) {
  margin: 18px 0 0;
  border-left: 3px solid #1d6fdc;
  padding: 8px 12px;
  background: rgba(29, 111, 220, 0.06);
  color: #526179;
}

.role-assistant .message-bubble:has(.markdown-body table) {
  max-width: min(920px, calc(100% - 56px));
  border: 1px solid rgba(122, 139, 165, 0.16);
  background: #fff;
  box-shadow: 0 18px 50px rgba(31, 45, 61, 0.08);
}

@media (max-width: 980px) {
  .agent-workbench {
    grid-template-columns: 1fr;
    height: auto;
    min-height: calc(100vh - 96px);
  }

  .session-rail {
    display: none;
  }

  .stage-header,
  .message-stream {
    padding-left: 16px;
    padding-right: 16px;
  }

  .question-rail {
    display: none;
  }

  .composer-wrap {
    width: calc(100% - 32px);
  }

  .slash-menu button {
    grid-template-columns: 76px minmax(0, 1fr);
  }

  .slash-menu small {
    display: none;
  }
}
</style>
