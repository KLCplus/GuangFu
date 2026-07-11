<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
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

interface ChatMessage {
  id: number
  role: MessageRole
  kind: MessageKind
  content: string
  time: string
  steps?: AgentStep[]
  tools?: ToolCard[]
  approval?: ApprovalCard
  finalActions?: boolean
}

interface SlashCommand {
  command: string
  label: string
  hint: string
  template: string
}

const userStore = useUserStore()

const agentSessions = ref<AgentSession[]>([])
const agentTools = ref<AgentToolInfo[]>([])
const agentSessionId = ref<number | null>(null)
const currentAssistantId = ref<number | null>(null)
const leftCollapsed = ref(false)
const showArchived = ref(false)
const showSlashMenu = ref(false)
const composerText = ref('')
const composerRef = ref<HTMLTextAreaElement>()
const loadingHistory = ref(false)
const loadingMessages = ref(false)
const running = ref(false)
const historyError = ref('')
const messages = ref<ChatMessage[]>([])

const slashCommands: SlashCommand[] = [
  { command: '/station', label: '查询电站', hint: '查看电站基础信息', template: '查询电站信息 ' },
  { command: '/weather', label: '查询天气', hint: '分析电站天气影响', template: '查询电站天气 ' },
  { command: '/predict', label: '查看预测', hint: '解读预测任务结果', template: '解释预测任务 ' },
  { command: '/report', label: '生成报告', hint: '创建综合分析报告', template: '生成综合分析报告 ' },
  { command: '/model', label: '查看模型', hint: '查询模型能力', template: '查询模型信息 ' },
  { command: '/api', label: 'API 使用', hint: '查看 API 调用情况', template: '查看 API 使用情况' }
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

function recordValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function commandParts(text = composerText.value) {
  const parts = text.trim().split(/\s+/).filter(Boolean)
  return { command: parts[0] || '', args: parts.slice(1) }
}

function createSystemMessage(): ChatMessage {
  return {
    id: Date.now(),
    role: 'system',
    kind: 'text',
    content: '描述你的目标，Agent 会按需查询电站、天气、预测和报告工具。',
    time: nowTime()
  }
}

function addMessage(message: Omit<ChatMessage, 'id' | 'time'>) {
  const item: ChatMessage = {
    ...message,
    id: Date.now() + Math.random(),
    time: nowTime()
  }
  messages.value.push(item)
  nextTick(() => {
    const stream = document.querySelector('.message-stream')
    stream?.scrollTo({ top: stream.scrollHeight, behavior: 'smooth' })
  })
  return item
}

function activeAssistant() {
  let item = messages.value.find((message) => message.id === currentAssistantId.value)
  if (!item) {
    item = addMessage({
      role: 'assistant',
      kind: 'working',
      content: '正在处理任务',
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
}

function upsertTool(card: ToolCard) {
  const assistant = activeAssistant()
  const tools = assistant.tools || []
  const index = tools.findIndex((item) => item.id === card.id || item.toolName === card.toolName)
  if (index >= 0) tools[index] = { ...tools[index], ...card }
  else tools.push(card)
  assistant.tools = tools
}

function humanError(error: unknown) {
  if (error instanceof Error) return error.message
  return '请求失败'
}

function sessionTime(session: AgentSession) {
  return firstText(session.updatedAt, session.createdAt) || '无时间'
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
    'weather.current': '获取天气数据',
    'prediction.list': '读取预测任务',
    'prediction.detail': '读取预测结果',
    'report.generate': '生成综合分析报告',
    'report.list': '查询历史报告',
    'report.detail': '读取报告详情',
    'model.list': '查询模型',
    'model.detail': '读取模型详情',
    'api.usage': '查看 API 使用情况',
    'api.list': '查询 API 服务'
  }
  return local[toolName] || displayName || toolName
}

function toolBusinessSummary(data: Record<string, unknown>) {
  const summary = firstText(data.summary, data.error)
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
    error: firstText(tool.errorMessage, result.errorMessage),
    dataPreview: result.data ?? result.raw ?? {}
  }
  return {
    id: tool.clientToolCallId || String(tool.toolCallId),
    toolName: tool.toolName,
    title: toolTitle(tool.toolName, tool.displayName),
    status: tool.status === 'SUCCESS' ? 'success' : tool.status === 'FAILED' ? 'failed' : tool.status === 'AWAITING_APPROVAL' ? 'waiting' : 'running',
    summary: toolBusinessSummary(data),
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
    activeAssistant().content = '正在处理任务'
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
      summary: '正在执行'
    })
    return
  }
  if (event.event === 'tool_result') {
    const toolName = firstText(data.toolName)
    const ok = firstText(data.status) === 'success'
    const title = toolTitle(toolName, firstText(data.displayName))
    const summary = toolBusinessSummary(data)
    upsertStep(`tool-${toolName}`, title, ok ? 'success' : 'failed', summary)
    upsertTool({
      id: firstText(data.toolCallId, toolName),
      toolName,
      title,
      status: ok ? 'success' : 'failed',
      summary,
      durationMs: firstNumber(data.durationMs) || undefined,
      detail: ok ? '' : firstText(data.error)
    })
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
  focusComposer()
}

function setSlashCommand(command: string) {
  composerText.value = `${command} `
  showSlashMenu.value = false
  focusComposer()
}

function onComposerInput() {
  showSlashMenu.value = composerText.value.trim().startsWith('/')
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
    addMessage({ role: 'assistant', kind: 'error', content: `未知命令：${command}。可用命令：/station /weather /predict /report /model /api` })
    composerText.value = ''
    return
  }
  composerText.value = ''
  await runAgentChat(text)
}

function onComposerKeydown(event: KeyboardEvent) {
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
                <strong>{{ session.title }}</strong>
                <small>{{ sessionTime(session) }} · {{ sessionStatus(session) }}</small>
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
                <strong>{{ session.title }}</strong>
                <small>{{ sessionTime(session) }} · {{ sessionStatus(session) }}</small>
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
                <small>{{ sessionTime(session) }} · 已归档</small>
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
            <h1>综合分析 Agent</h1>
            <p>{{ activeSessionTitle }}</p>
          </div>
        </div>
        <div class="header-actions">
          <el-tag size="small" effect="plain">Agent</el-tag>
          <el-tag size="small" :type="connected ? 'success' : 'warning'" effect="plain">{{ connected ? 'Connected' : 'Connecting' }}</el-tag>
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

      <section v-loading="loadingMessages" class="message-stream">
        <div
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="[`role-${message.role}`, `kind-${message.kind}`]"
        >
          <div v-if="message.role !== 'system'" class="avatar">
            {{ message.role === 'user' ? username.slice(0, 1).toUpperCase() : 'AI' }}
          </div>
          <article class="message-bubble">
            <div v-if="message.role !== 'system'" class="message-meta">
              <strong>{{ message.role === 'user' ? username : 'Agent' }}</strong>
              <span>{{ message.time }}</span>
            </div>
            <p v-if="message.content" class="message-text">{{ message.content }}</p>

            <div v-if="message.steps?.length" class="run-card">
              <div class="run-head">
                <strong>执行过程</strong>
                <span>{{ message.steps.filter((step) => step.status === 'success').length }}/{{ message.steps.length }}</span>
              </div>
              <div class="run-steps">
                <div v-for="step in message.steps" :key="step.key" class="run-step" :class="step.status">
                  <span>{{ stepMark(step.status) }}</span>
                  <div>
                    <strong>{{ step.title }}</strong>
                    <small>{{ step.detail || statusLabel(step.status) }}</small>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="message.tools?.length" class="tool-list">
              <details v-for="tool in message.tools" :key="tool.id" class="tool-card" :class="tool.status">
                <summary>
                  <span>
                    <strong>{{ tool.title }}</strong>
                    <small>{{ tool.summary }}</small>
                  </span>
                  <em>{{ statusLabel(tool.status) }}<template v-if="tool.durationMs"> · {{ tool.durationMs }} ms</template></em>
                </summary>
                <p>{{ tool.detail || tool.summary }}</p>
              </details>
            </div>

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
              <button type="button" @click="continueWith('生成综合分析报告')">生成报告</button>
              <button type="button" @click="copyMessage(message)">复制结论</button>
              <button type="button" @click="continueWith('继续分析：')">继续追问</button>
            </div>
          </article>
        </div>
      </section>

      <section class="composer-wrap">
        <div v-if="showSlashMenu && filteredCommands.length > 0" class="slash-menu">
          <button v-for="command in filteredCommands" :key="command.command" type="button" @click="applyCommand(command)">
            <strong>{{ command.command }}</strong>
            <span>{{ command.label }}</span>
            <small>{{ command.hint }}</small>
          </button>
        </div>

        <div class="composer">
          <button class="tool-trigger" type="button" @click="showSlashMenu = !showSlashMenu">/</button>
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

        <div class="quick-commands">
          <button type="button" @click="setSlashCommand('/station')">/station</button>
          <button type="button" @click="setSlashCommand('/weather')">/weather</button>
          <button type="button" @click="setSlashCommand('/predict')">/predict</button>
          <button type="button" @click="setSlashCommand('/report')">/report</button>
          <button type="button" @click="setSlashCommand('/api')">/api</button>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.agent-workbench {
  height: calc(100vh - 88px);
  min-height: 0;
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr);
  overflow: hidden;
  border-radius: 12px;
  background: #f4f7fb;
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
.quick-commands,
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
  min-height: 54px;
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
  gap: 3px;
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
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(246, 249, 252, 0.94)),
    radial-gradient(circle at 80% 0%, rgba(66, 153, 225, 0.12), transparent 34%);
}

.stage-header {
  justify-content: space-between;
  gap: 16px;
  padding: 18px 28px 14px;
  border-bottom: 1px solid rgba(122, 139, 165, 0.12);
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
  border-radius: 50%;
  background: #172033;
  color: #fff;
  font-weight: 800;
  font-size: 13px;
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
  background: #1d6fdc;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.message-stream {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 28px 36px 22px;
  scroll-behavior: smooth;
}

.message-row {
  display: flex;
  gap: 12px;
  margin: 0 auto 18px;
  width: 100%;
  max-width: 1180px;
}

.message-row.role-user {
  justify-content: flex-end;
}

.message-row.role-user .avatar {
  order: 2;
  background: #1d6fdc;
}

.message-row.role-system {
  justify-content: center;
}

.message-bubble {
  max-width: min(940px, calc(100% - 56px));
  border-radius: 8px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 12px 30px rgba(31, 45, 61, 0.06);
}

.role-user .message-bubble {
  background: #172033;
  color: #fff;
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
  margin-bottom: 6px;
  color: #7a879a;
  font-size: 12px;
}

.role-user .message-meta {
  color: rgba(255, 255, 255, 0.72);
}

.message-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
}

.run-card,
.tool-list,
.approval-card,
.final-actions {
  margin-top: 12px;
}

.run-card {
  border: 1px solid rgba(122, 139, 165, 0.16);
  border-radius: 8px;
  background: #fbfdff;
  overflow: hidden;
}

.run-head {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(122, 139, 165, 0.12);
  color: #34445f;
}

.run-head span {
  color: #7a879a;
  font-size: 12px;
}

.run-steps {
  display: grid;
  gap: 0;
}

.run-step {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 8px;
  padding: 10px 12px;
}

.run-step + .run-step {
  border-top: 1px solid rgba(122, 139, 165, 0.08);
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

.tool-list {
  display: grid;
  gap: 8px;
}

.tool-card {
  border: 1px solid rgba(122, 139, 165, 0.16);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
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

.final-actions {
  gap: 8px;
  flex-wrap: wrap;
}

.final-actions button,
.quick-commands button {
  border: 0;
  border-radius: 999px;
  padding: 7px 11px;
  background: rgba(29, 111, 220, 0.08);
  color: #1d4f9a;
  cursor: pointer;
}

.composer-wrap {
  position: relative;
  max-width: 1180px;
  width: calc(100% - 72px);
  margin: 0 auto 22px;
}

.composer {
  min-height: 58px;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(31, 45, 61, 0.14);
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

.quick-commands {
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.slash-menu {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 124px;
  z-index: 10;
  display: grid;
  gap: 4px;
  padding: 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 50px rgba(31, 45, 61, 0.16);
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

.slash-menu button:hover {
  background: rgba(29, 111, 220, 0.08);
}

.slash-menu small {
  color: #7a879a;
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
