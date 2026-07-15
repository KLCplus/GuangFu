<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getModels } from '../api/model'
import type { ModelListItem } from '../api/model'
import { getModelIcon } from '../assets/model-icons/svg'
import {
  applyApiKey,
  deleteApiKey,
  exportCallLogs,
  getApiKeys,
  getCallLogs,
  getUsageByKey,
  getUsageByModel,
  getUsageSummary,
  getUsageTrend,
  getOpenWallet,
  rechargeWallet,
  resetOpenApiKey,
  updateApiKeyName,
  updateApiKeyStatus
} from '../api/open'
import type {
  ApiCallLog,
  ApiKey,
  ApiKeyApplyPayload,
  ApiUsageByKeyItem,
  ApiUsageByModelItem,
  ApiUsageSummary,
  ApiUsageTrendItem,
  RechargeOrder,
  Wallet
} from '../api/open'

interface ApiKeyForm {
  keyName: string
  expireDays: number
}

interface DistributionItem {
  name: string
  value: number
}

const props = withDefaults(defineProps<{
  routeBase?: string
  marketplacePath?: string
  visualTheme?: boolean
}>(), {
  routeBase: '/api',
  marketplacePath: '/marketplace',
  visualTheme: false
})

// ---- state ----

const route = useRoute()
const activePage = computed(() => route.path.split('/').pop() || 'overview')

const keysLoading = ref(false)
const statsLoading = ref(false)
const logsLoading = ref(false)
const exporting = ref(false)
const refreshing = ref(false)
const actionLoadingId = ref<number | null>(null)
const createLoading = ref(false)
const editLoading = ref(false)
const keyError = ref('')
const statsError = ref('')
const logsError = ref('')
const summaryError = ref('')
const trendError = ref('')
const modelStatsError = ref('')
const keyStatsError = ref('')
const walletError = ref('')
const walletLoading = ref(false)
const rechargeDialogVisible = ref(false)
const rechargeLoading = ref(false)

const apiKeys = ref<ApiKey[]>([])
const models = ref<ModelListItem[]>([])

// backend aggregation data
const summaryData = ref<ApiUsageSummary | null>(null)
const trendApiData = ref<ApiUsageTrendItem[]>([])
const modelApiData = ref<ApiUsageByModelItem[]>([])
const keyApiData = ref<ApiUsageByKeyItem[]>([])
const wallet = ref<Wallet | null>(null)

// server-side paginated call logs
const callLogRecords = ref<ApiCallLog[]>([])
const callLogTotal = ref(0)

const createDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const createdKey = ref<ApiKey | null>(null)
const editKeyDialogVisible = ref(false)
const editKeyForm = reactive({ keyName: '', apiKeyId: 0 })
const rechargeForm = reactive({ amount: 100, channel: 'MOCK' as 'MOCK' | 'ALIPAY' | 'WECHAT' | 'BANK' })
const lastRechargeOrder = ref<RechargeOrder | null>(null)

const createForm = reactive<ApiKeyForm>({
  keyName: '',
  expireDays: 90
})

const filters = reactive({
  days: 30,
  apiKeyId: undefined as number | undefined,
  modelId: undefined as number | undefined,
  status: ''
})

const logPage = reactive({
  pageNum: 1,
  pageSize: 10
})

const trendChartRef = ref<HTMLDivElement | null>(null)
const modelChartRef = ref<HTMLDivElement | null>(null)
const keyChartRef = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null
let modelChart: echarts.ECharts | null = null
let keyChart: echarts.ECharts | null = null

const timeOptions = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
  { label: '全部时间', value: 0 }
]

const modelNameMap = computed(() => new Map(models.value.map((model) => [model.modelId, model.modelName])))
const keyNameMap = computed(() => new Map(apiKeys.value.map((key) => [key.apiKeyId, key.keyName])))
const modelSearch = ref('')
const onlineModels = computed(() => models.value.filter((model) => model.status === 'ONLINE'))
const filteredModels = computed(() => {
  const query = modelSearch.value.trim().toLowerCase()
  return onlineModels.value.filter((model) => !query || `${model.modelName} ${model.modelCode}`.toLowerCase().includes(query)).slice(0, 6)
})

const modelOptions = computed(() => {
  if (modelApiData.value.length) {
    return modelApiData.value
      .filter((item) => item.modelId != null)
      .map((item) => ({ value: item.modelId!, label: item.modelName }))
  }
  // fallback: derive from models list
  return models.value.map((model) => ({ value: model.modelId, label: model.modelName }))
})

// ---- time range helpers ----

function filterStartTime(): string | undefined {
  if (!filters.days) return undefined
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  date.setDate(date.getDate() - (filters.days - 1))
  return formatDateTime(date)
}

function formatDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function filterParams() {
  return {
    startTime: filterStartTime(),
    apiKeyId: filters.apiKeyId,
    modelId: filters.modelId
  }
}

// ---- computed from backend data ----

const summaryCards = computed(() => {
  const s = summaryData.value
  if (!s) {
    return [
      { label: '总调用次数', value: '—', note: summaryError.value || '正在加载统计数据…', tone: 'blue' },
      { label: '成功调用', value: '—', note: '', tone: 'green' },
      { label: '失败调用', value: '—', note: '', tone: 'red' },
      { label: '平均响应时间', value: '—', note: '', tone: 'purple' }
    ]
  }
  return [
    {
      label: '总调用次数',
      value: formatNumber(s.totalCalls),
      note: '后端聚合统计',
      tone: 'blue' as const
    },
    {
      label: '成功调用',
      value: formatNumber(s.successCalls),
      note: `成功率 ${s.successRate}%`,
      tone: 'green' as const
    },
    {
      label: '失败调用',
      value: formatNumber(s.failedCalls),
      note: 'HTTP 或业务状态判定失败',
      tone: 'red' as const
    },
    {
      label: '平均响应时间',
      value: `${formatNumber(s.avgCostTimeMs)} ms`,
      note: '调用日志 costTimeMs 平均值',
      tone: 'purple' as const
    }
  ]
})

const tokenSummary = computed(() => {
  const s = summaryData.value
  if (!s) return { input: '—', output: '—', total: '—', hasData: false, note: summaryError.value || '等待统计汇总数据' }
  const hasData = s.inputTokens > 0 || s.outputTokens > 0 || s.totalTokens > 0
  return {
    input: hasData ? formatNumber(s.inputTokens) : '—',
    output: hasData ? formatNumber(s.outputTokens) : '—',
    total: hasData ? formatNumber(s.totalTokens) : '—',
    hasData,
    note: hasData ? '后端聚合统计' : 'Token 统计暂未接通'
  }
})

const trendData = computed(() => {
  return trendApiData.value.map((item) => ({
    date: item.timeBucket,
    success: item.successCalls,
    failed: item.failedCalls
  }))
})

const modelDistribution = computed<DistributionItem[]>(() => {
  return modelApiData.value.map((item) => ({
    name: item.modelName || (item.modelId == null ? '未记录模型' : `模型 #${item.modelId}`),
    value: item.totalCalls
  }))
})

const keyDistribution = computed<DistributionItem[]>(() => {
  return keyApiData.value.map((item) => ({
    name: item.keyName || (item.apiKeyId == null ? '未记录 Key' : `Key #${item.apiKeyId}`),
    value: item.totalCalls
  }))
})

const statsCoverageText = computed(() => {
  if (statsLoading.value) return '正在从后端加载统计数据…'
  if (statsError.value) return '统计数据加载失败，请重试。'
  const total = summaryData.value?.totalCalls ?? 0
  return `统计数据由后端聚合，当前筛选范围内共 ${formatNumber(total)} 条调用记录。`
})

const walletAmount = computed(() => wallet.value?.balance ?? null)
const walletMonthlyCost = computed(() => wallet.value?.monthlyCost ?? null)
const walletRecords = computed(() => wallet.value?.records ?? [])
const overviewSummary = computed(() => [
  { label: 'API Key 数量', value: formatNumber(apiKeys.value.length), note: '当前账户凭证' },
  { label: '可调用模型', value: formatNumber(onlineModels.value.length), note: '在线模型' },
  { label: '可用余额', value: formatMoney(walletAmount.value), note: '钱包账户余额' },
  { label: '本月调用次数', value: formatNumber(summaryData.value?.totalCalls ?? 0), note: '后端聚合统计' }
])
const hasTrendData = computed(() => trendApiData.value.length > 0)
const hasModelStats = computed(() => modelApiData.value.length > 0)
const hasKeyStats = computed(() => keyApiData.value.length > 0)
const hasAnyChartData = computed(() => hasTrendData.value || hasModelStats.value || hasKeyStats.value)

// ---- lifecycle ----

onMounted(() => {
  window.addEventListener('resize', resizeCharts)
  void loadPage()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  modelChart?.dispose()
  keyChart?.dispose()
})

watch(
  () => [filters.days, filters.apiKeyId, filters.modelId, filters.status],
  () => {
    logPage.pageNum = 1
    if (activePage.value === 'overview' || activePage.value === 'usage') void loadStats()
    if (activePage.value === 'billing') void loadCallLogs()
  }
)

watch(logPage, () => {
  if (activePage.value === 'billing') void loadCallLogs()
})

watch(activePage, () => {
  logPage.pageNum = 1
  void loadPage()
})

watch([trendApiData, modelApiData, keyApiData], () => {
  void nextTick(renderCharts)
})

// ---- data loading ----

async function loadPage() {
  refreshing.value = true
  const pageLoads = [loadKeys(), loadModels(), loadWallet()]
  if (activePage.value === 'overview' || activePage.value === 'usage') pageLoads.push(loadStats())
  if (activePage.value === 'billing') pageLoads.push(loadCallLogs())
  await Promise.all(pageLoads)
  refreshing.value = false
  void nextTick(renderCharts)
}

async function loadWallet() {
  walletLoading.value = true
  walletError.value = ''
  try {
    wallet.value = await getOpenWallet()
  } catch (error) {
    wallet.value = null
    walletError.value = errorMessage(error, '余额信息加载失败')
  } finally {
    walletLoading.value = false
  }
}

async function submitRecharge() {
  const amount = Number(rechargeForm.amount)
  if (!Number.isFinite(amount) || amount < 1) {
    ElMessage.warning('充值金额不能小于 1 元')
    return
  }
  rechargeLoading.value = true
  try {
    lastRechargeOrder.value = await rechargeWallet({ amount, channel: rechargeForm.channel })
    ElMessage.success('充值成功，余额已入账')
    rechargeDialogVisible.value = false
    await loadWallet()
  } catch (error) {
    ElMessage.error(errorMessage(error, '充值失败'))
  } finally {
    rechargeLoading.value = false
  }
}

async function loadKeys() {
  keysLoading.value = true
  keyError.value = ''
  try {
    apiKeys.value = await getApiKeys()
  } catch (error) {
    apiKeys.value = []
    keyError.value = errorMessage(error, 'API Key 加载失败')
  } finally {
    keysLoading.value = false
  }
}

async function loadModels() {
  try {
    models.value = await getModels()
  } catch {
    models.value = []
  }
}

async function loadStats() {
  statsLoading.value = true
  statsError.value = ''
  summaryError.value = ''
  trendError.value = ''
  modelStatsError.value = ''
  keyStatsError.value = ''
  const params = filterParams()
  const [summaryResult, trendResult, modelResult, keyResult] = await Promise.allSettled([
    getUsageSummary(params),
    getUsageTrend(params),
    getUsageByModel(params),
    getUsageByKey(params)
  ])

  if (summaryResult.status === 'fulfilled') summaryData.value = summaryResult.value
  else {
    summaryData.value = null
    summaryError.value = errorMessage(summaryResult.reason, '汇总数据加载失败')
  }
  if (trendResult.status === 'fulfilled') trendApiData.value = trendResult.value
  else {
    trendApiData.value = []
    trendError.value = errorMessage(trendResult.reason, '调用趋势加载失败')
  }
  if (modelResult.status === 'fulfilled') modelApiData.value = modelResult.value
  else {
    modelApiData.value = []
    modelStatsError.value = errorMessage(modelResult.reason, '模型统计加载失败')
  }
  if (keyResult.status === 'fulfilled') keyApiData.value = keyResult.value
  else {
    keyApiData.value = []
    keyStatsError.value = errorMessage(keyResult.reason, 'API Key 统计加载失败')
  }

  const failures = [summaryError.value, trendError.value, modelStatsError.value, keyStatsError.value]
    .filter(Boolean).length
  statsError.value = failures ? `${failures} 项统计数据加载失败，其余数据仍可正常使用。` : ''
  statsLoading.value = false
  await nextTick()
  renderCharts()
  resizeCharts()
}

async function loadCallLogs() {
  logsLoading.value = true
  logsError.value = ''
  try {
    const result = await getCallLogs({
      pageNum: logPage.pageNum,
      pageSize: logPage.pageSize,
      apiKeyId: filters.apiKeyId,
      modelId: filters.modelId,
      status: filters.status || undefined,
      startTime: filterStartTime()
    })
    callLogRecords.value = result.records
    callLogTotal.value = result.total
  } catch (error) {
    callLogRecords.value = []
    callLogTotal.value = 0
    logsError.value = errorMessage(error, '调用日志加载失败')
  } finally {
    logsLoading.value = false
  }
}

// ---- key management ----

async function submitCreateKey() {
  const keyName = createForm.keyName.trim()
  if (!keyName) {
    ElMessage.warning('请输入 API Key 名称')
    return
  }

  createLoading.value = true
  try {
    const payload: ApiKeyApplyPayload = { keyName, expireDays: createForm.expireDays }
    createdKey.value = await applyApiKey(payload)
    createDialogVisible.value = false
    resultDialogVisible.value = true
    ElMessage.success('API Key 创建成功')
    await loadKeys()
  } catch (error) {
    ElMessage.error(errorMessage(error, 'API Key 创建失败'))
  } finally {
    createLoading.value = false
  }
}

function openEditKeyDialog(row: ApiKey) {
  editKeyForm.keyName = row.keyName
  editKeyForm.apiKeyId = row.apiKeyId
  editKeyDialogVisible.value = true
}

async function submitEditKeyName() {
  const keyName = editKeyForm.keyName.trim()
  if (!keyName) {
    ElMessage.warning('请输入 Key 名称')
    return
  }

  editLoading.value = true
  try {
    await updateApiKeyName(editKeyForm.apiKeyId, { keyName })
    editKeyDialogVisible.value = false
    ElMessage.success('API Key 名称已更新')
    await loadKeys()
  } catch (error) {
    ElMessage.error(errorMessage(error, '名称修改失败'))
  } finally {
    editLoading.value = false
  }
}

async function toggleKeyStatus(row: ApiKey) {
  const willEnable = normalizeKeyStatus(row.status) !== 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      willEnable
        ? `确认启用「${row.keyName}」吗？启用后该 Key 可继续调用开放接口。`
        : `确认停用「${row.keyName}」吗？停用后使用该 Key 的请求将被拒绝。`,
      willEnable ? '启用 API Key' : '停用 API Key',
      {
        type: willEnable ? 'info' : 'warning',
        confirmButtonText: willEnable ? '确认启用' : '确认停用',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  actionLoadingId.value = row.apiKeyId
  try {
    await updateApiKeyStatus(row.apiKeyId, { status: willEnable ? 'ACTIVE' : 'DISABLED' })
    ElMessage.success(willEnable ? 'API Key 已启用' : 'API Key 已停用')
    await loadKeys()
  } catch (error) {
    ElMessage.error(errorMessage(error, '状态更新失败'))
  } finally {
    actionLoadingId.value = null
  }
}

async function resetKey(row: ApiKey) {
  try {
    await ElMessageBox.confirm(
      `确认重新生成「${row.keyName}」吗？旧 Key 将立即失效，新 Key 只在本次响应中完整展示。`,
      '重新生成 API Key',
      {
        type: 'warning',
        confirmButtonText: '重新生成',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  actionLoadingId.value = row.apiKeyId
  try {
    createdKey.value = await resetOpenApiKey(row.apiKeyId)
    resultDialogVisible.value = true
    ElMessage.success('API Key 已重新生成')
    await loadKeys()
  } catch (error) {
    ElMessage.error(errorMessage(error, 'API Key 重新生成失败'))
  } finally {
    actionLoadingId.value = null
  }
}

async function deleteKey(row: ApiKey) {
  try {
    await ElMessageBox.confirm(
      `确认永久删除「${row.keyName}」吗？删除后不可恢复。`,
      '删除 API Key',
      {
        type: 'error',
        confirmButtonText: '永久删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  actionLoadingId.value = row.apiKeyId
  try {
    await deleteApiKey(row.apiKeyId)
    ElMessage.success('API Key 已删除')
    await Promise.all([loadKeys(), loadStats()])
  } catch (error) {
    ElMessage.error(errorMessage(error, 'API Key 删除失败'))
  } finally {
    actionLoadingId.value = null
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const params = filterParams()
    const logs = await exportCallLogs({
      ...params,
      status: filters.status || undefined
    })
    if (!logs.length) {
      ElMessage.warning('当前筛选条件下无数据可导出')
      return
    }
    const headers = ['时间', 'API Key ID', '模型', '接口', '方法', '状态', 'HTTP', '耗时(ms)', '错误信息', '输入 Token', '输出 Token', '总 Token']
    const rows = logs.map((log) => [
      formatDate(log.requestTime ?? log.createdAt),
      log.apiKeyId ?? '',
      log.modelName ?? (log.modelId ? `模型 #${log.modelId}` : ''),
      log.path ?? log.requestPath ?? '',
      log.method ?? log.requestMethod ?? '',
      log.status ?? log.bizStatus ?? '',
      log.statusCode ?? log.httpStatus ?? '',
      log.costTimeMs ?? log.costTime ?? 0,
      (log.errorMessage ?? '').replace(/,/g, ' '),
      log.inputTokens ?? '',
      log.outputTokens ?? '',
      log.totalTokens ?? ''
    ])
    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
    const BOM = '﻿'
    const blob = new Blob([BOM + csvContent], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `api-call-logs-${formatDateTime(new Date()).slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${logs.length} 条记录`)
  } catch (error) {
    ElMessage.error(errorMessage(error, '导出失败'))
  } finally {
    exporting.value = false
  }
}

function resetCreateForm() {
  createForm.keyName = ''
  createForm.expireDays = 90
}

function clearOneTimeKey() {
  createdKey.value = null
}

// ---- chart rendering ----

function renderCharts() {
  renderTrendChart()
  renderModelChart()
  renderKeyChart()
}

function apiChartTheme() {
  if (!props.visualTheme) {
    return {
      text: '#526278',
      axis: '#d9e3ef',
      split: '#edf2f7',
      tooltipBackground: '#ffffff',
      tooltipBorder: '#dce6f1',
      tooltipText: '#10274c'
    }
  }
  return {
    text: '#79a8ba',
    axis: 'rgba(82, 232, 255, 0.24)',
    split: 'rgba(82, 232, 255, 0.1)',
    tooltipBackground: '#071d3d',
    tooltipBorder: 'rgba(82, 232, 255, 0.28)',
    tooltipText: '#dff7ff'
  }
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  trendChart = trendChart ?? echarts.init(trendChartRef.value)
  const items = trendData.value
  const theme = apiChartTheme()
  trendChart.setOption({
    animationDuration: 350,
    color: props.visualTheme ? ['#52e8ff', '#ff7187'] : ['#1d6fdc', '#e05a67'],
    tooltip: { trigger: 'axis', backgroundColor: theme.tooltipBackground, borderColor: theme.tooltipBorder, textStyle: { color: theme.tooltipText } },
    legend: { top: 0, right: 0, data: ['成功', '失败'], textStyle: { color: theme.text } },
    grid: { left: 42, right: 18, top: 42, bottom: 30 },
    xAxis: {
      type: 'category',
      data: items.map((item) => item.date.length > 10 ? item.date.slice(5, 16) : item.date.slice(5)),
      axisLine: { lineStyle: { color: theme.axis } },
      axisTick: { show: false },
      axisLabel: { color: theme.text }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: theme.text },
      splitLine: { lineStyle: { color: theme.split } }
    },
    series: [
      {
        name: '成功',
        type: 'line',
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 7,
        areaStyle: { color: props.visualTheme ? 'rgba(82, 232, 255, 0.1)' : 'rgba(29, 111, 220, 0.08)' },
        data: items.map((item) => item.success)
      },
      {
        name: '失败',
        type: 'line',
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 7,
        data: items.map((item) => item.failed)
      }
    ]
  }, true)
}

function renderModelChart() {
  if (!modelChartRef.value) return
  modelChart = modelChart ?? echarts.init(modelChartRef.value)
  const theme = apiChartTheme()
  modelChart.setOption({
    animationDuration: 350,
    color: props.visualTheme ? ['#52e8ff', '#3f91ff', '#42f5c2', '#a990ff', '#ffc45c', '#ff7187'] : ['#1d6fdc', '#4ba3f2', '#58b89b', '#8069dd', '#e6a23c', '#e05a67'],
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 次（{d}%）', backgroundColor: theme.tooltipBackground, borderColor: theme.tooltipBorder, textStyle: { color: theme.tooltipText } },
    legend: { type: 'scroll', bottom: 0, left: 'center', textStyle: { color: theme.text }, pageTextStyle: { color: theme.text }, pageIconColor: theme.text, pageIconInactiveColor: '#365f73' },
    series: [{
      type: 'pie',
      radius: ['46%', '70%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { show: false },
      emphasis: { label: { show: true, fontWeight: 700 } },
      data: modelDistribution.value
    }]
  }, true)
}

function renderKeyChart() {
  if (!keyChartRef.value) return
  keyChart = keyChart ?? echarts.init(keyChartRef.value)
  const data = keyDistribution.value.slice(0, 8).reverse()
  const theme = apiChartTheme()
  keyChart.setOption({
    animationDuration: 350,
    color: [props.visualTheme ? '#52e8ff' : '#1d6fdc'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: theme.tooltipBackground, borderColor: theme.tooltipBorder, textStyle: { color: theme.tooltipText } },
    grid: { left: 20, right: 18, top: 10, bottom: 20, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: theme.text },
      splitLine: { lineStyle: { color: theme.split } }
    },
    yAxis: {
      type: 'category',
      data: data.map((item) => item.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { width: 110, overflow: 'truncate', color: theme.text }
    },
    series: [{
      type: 'bar',
      barMaxWidth: 18,
      itemStyle: { borderRadius: [0, 5, 5, 0] },
      data: data.map((item) => item.value)
    }]
  }, true)
}

function resizeCharts() {
  trendChart?.resize()
  modelChart?.resize()
  keyChart?.resize()
}

// ---- display helpers ----

function normalizeKeyStatus(status?: string) {
  return status === 'ACTIVE' ? 'ACTIVE' : status === 'EXPIRED' ? 'EXPIRED' : 'DISABLED'
}

function keyStatusLabel(status?: string) {
  const normalized = normalizeKeyStatus(status)
  if (normalized === 'ACTIVE') return '启用'
  if (normalized === 'EXPIRED') return '已过期'
  return '已停用'
}

function keyStatusType(status?: string) {
  const normalized = normalizeKeyStatus(status)
  if (normalized === 'ACTIVE') return 'success'
  if (normalized === 'EXPIRED') return 'warning'
  return 'info'
}

function logStatusLabel(log: ApiCallLog) {
  const status = (log.status ?? log.bizStatus ?? '').toUpperCase()
  return status === 'SUCCESS' ? '成功' : '失败'
}

function logStatusType(log: ApiCallLog) {
  const status = (log.status ?? log.bizStatus ?? '').toUpperCase()
  return status === 'SUCCESS' ? 'success' : 'danger'
}

function maskedKey(key: ApiKey) {
  const prefix = key.apiKeyPrefix?.trim()
  return prefix ? `${prefix}_${'•'.repeat(18)}` : '未返回 Key 前缀'
}

function oneTimeKey() {
  return createdKey.value?.apiKey?.trim() ?? ''
}

function keyName(apiKeyId: number | null | undefined) {
  if (apiKeyId == null) return '未记录'
  return keyNameMap.value.get(apiKeyId) ?? `Key #${apiKeyId}`
}

function modelName(modelId: number | null | undefined) {
  if (modelId == null) return '未记录'
  return modelNameMap.value.get(modelId) ?? `模型 #${modelId}`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatDate(value?: string) {
  if (!value) return '从未使用'
  return value.replace('T', ' ')
}

function formatMoney(value?: number | null) {
  return value == null ? '—' : `¥${value.toFixed(2)}`
}

function walletRecordTypeLabel(type?: string) {
  if (type === 'RECHARGE') return '充值'
  if (type === 'CONSUME') return '消费'
  if (type === 'REFUND') return '退款'
  if (type === 'ADJUST') return '调整'
  return type || '流水'
}

function walletRecordTagType(type?: string) {
  if (type === 'RECHARGE' || type === 'REFUND') return 'success'
  if (type === 'CONSUME') return 'warning'
  return 'info'
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

async function copyText(value: string) {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('API Key 已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<template>
  <section class="api-page">
    <section v-if="activePage === 'overview'" class="overview-section">
      <div class="section-heading">
        <div>
          <h1>API 开放平台</h1>
        </div>
        <el-button :loading="refreshing" @click="loadPage">刷新数据</el-button>
      </div>
      <div class="overview-summary">
        <article v-for="item in overviewSummary" :key="item.label">
          <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
        </article>
      </div>
      <section class="model-panel">
        <div class="panel-heading">
          <div><h2>可调用模型</h2></div>
          <div class="model-tools"><el-input v-model="modelSearch" clearable placeholder="搜索名称或模型 ID" /><el-button text type="primary" @click="$router.push(props.marketplacePath)">查看全部模型</el-button></div>
        </div>
        <el-alert v-if="onlineModels.length === 0" title="暂无可调用模型或模型目录加载失败" type="info" :closable="false" />
        <div v-else class="model-grid">
          <article v-for="model in filteredModels" :key="model.modelId" class="model-card">
            <div class="model-card-head"><img class="model-glyph" :src="getModelIcon(model.modelCode, model.modelType)" alt="" /><div><h3>{{ model.modelName }}</h3><p>{{ model.provider || model.modelFamily || '平台模型' }}</p></div><el-tag size="small" type="success" effect="light">可用</el-tag></div>
            <p class="model-description">{{ model.shortDescription || model.description || '暂无模型简介' }}</p>
            <div class="model-meta"><code>{{ model.modelCode }}</code><span>{{ model.modelType }}</span></div>
            <div v-if="model.tags?.length" class="model-tags"><el-tag v-for="tag in model.tags.slice(0, 3)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></div>
          </article>
        </div>
        <el-empty v-if="onlineModels.length > 0 && filteredModels.length === 0" description="没有匹配的模型" :image-size="64" />
      </section>
      <section class="service-note"><div><h2>API 服务</h2></div><div class="service-links"><el-button text @click="$router.push(`${props.routeBase}/keys`)">管理 API Keys</el-button><el-button text @click="$router.push(`${props.routeBase}/usage`)">查看使用统计</el-button><el-button text @click="$router.push(`${props.routeBase}/billing`)">查看流水消费</el-button></div></section>
    </section>
    <section v-if="activePage === 'keys'" class="key-section">
      <div class="section-heading key-heading">
        <div>
          <h1>API Keys</h1>
        </div>
        <el-button type="primary" size="large" @click="createDialogVisible = true">创建 API Key</el-button>
      </div>

      <el-alert
        class="key-security-alert"
        title="完整 API Key 仅在创建或重新生成时展示一次，请妥善保存。"
        type="warning"
        show-icon
        :closable="false"
      />

      <el-alert v-if="keyError" :title="keyError" type="error" show-icon :closable="false">
        <template #default>
          <el-button text type="primary" @click="loadKeys">重新加载</el-button>
        </template>
      </el-alert>

      <div class="key-table-shell">
        <el-table v-loading="keysLoading" :data="apiKeys" class="key-table" empty-text="暂无 API Key">
          <el-table-column prop="keyName" label="名称" min-width="170" />
          <el-table-column label="Key" min-width="280">
            <template #default="{ row }: { row: ApiKey }">
              <code class="masked-key">{{ maskedKey(row) }}</code>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="165">
            <template #default="{ row }: { row: ApiKey }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="最近使用" min-width="165">
            <template #default="{ row }: { row: ApiKey }">{{ formatDate(row.lastUsedAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }: { row: ApiKey }">
              <el-tag :type="keyStatusType(row.status)" effect="light">{{ keyStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="270" fixed="right">
            <template #default="{ row }: { row: ApiKey }">
              <div class="key-actions">
                <el-button link type="primary" @click="openEditKeyDialog(row)">编辑</el-button>
                <el-button
                  link
                  type="primary"
                  :loading="actionLoadingId === row.apiKeyId"
                  :disabled="row.status === 'EXPIRED'"
                  @click="toggleKeyStatus(row)"
                >
                  {{ normalizeKeyStatus(row.status) === 'ACTIVE' ? '停用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="primary"
                  :loading="actionLoadingId === row.apiKeyId"
                  @click="resetKey(row)"
                >
                  重新生成
                </el-button>
                <el-button
                  link
                  type="danger"
                  :loading="actionLoadingId === row.apiKeyId"
                  @click="deleteKey(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section v-if="activePage === 'billing'" v-loading="walletLoading" class="wallet-section">
      <div class="wallet-heading">
        <div>
          <h1>余额与流水</h1>
        </div>
        <el-button type="primary" @click="rechargeDialogVisible = true">去充值</el-button>
      </div>
      <el-alert v-if="walletError" :title="walletError" type="error" show-icon :closable="false">
        <template #default>
          <el-button text type="primary" @click="loadWallet">重新加载</el-button>
        </template>
      </el-alert>
      <div class="wallet-grid">
        <article>
          <span>可用余额</span>
          <strong>{{ formatMoney(walletAmount) }}</strong>
          <small>来自钱包账户表</small>
        </article>
        <article>
          <span>本月消费金额</span>
          <strong>{{ formatMoney(walletMonthlyCost) }}</strong>
          <small>来自本月消费流水聚合</small>
        </article>
      </div>
      <div class="wallet-records">
        <div class="wallet-records-heading">
          <h2>资金流水</h2>
          <span>{{ walletRecords.length }} 条</span>
        </div>
        <el-table :data="walletRecords" size="small" empty-text="暂无钱包流水">
          <el-table-column label="时间" min-width="160">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="walletRecordTagType(row.type)" effect="light">{{ walletRecordTypeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="说明" min-width="180" show-overflow-tooltip />
          <el-table-column label="金额" width="120">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="余额" width="120">
            <template #default="{ row }">{{ formatMoney(row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
        </el-table>
      </div>
      <p class="wallet-disclaimer">充值为本地联调模拟支付。</p>
    </section>

    <section v-if="activePage === 'usage' || activePage === 'billing'" class="usage-section">
      <div class="section-heading usage-heading">
        <div>
          <h2>{{ activePage === 'billing' ? '调用记录' : 'Dashboard' }}</h2>
        </div>
        <div v-if="activePage === 'usage'" class="heading-actions">
          <el-button :loading="exporting" @click="handleExport">导出 CSV</el-button>
          <el-button :loading="refreshing" @click="loadPage">刷新数据</el-button>
        </div>
      </div>

      <template v-if="activePage === 'usage'">
      <div class="filter-bar reference-toolbar">
        <div class="range-tabs" role="tablist" aria-label="时间范围">
          <button v-for="option in timeOptions" :key="option.value" :class="{ active: filters.days === option.value }" type="button" @click="filters.days = option.value">{{ option.value === 0 ? '全部' : option.value === 7 ? '7D' : option.value === 30 ? '1M' : '3M' }}</button>
        </div>
        <el-select v-model="filters.apiKeyId" class="filter-control" clearable placeholder="全部 API Key">
          <el-option v-for="key in apiKeys" :key="key.apiKeyId" :label="key.keyName" :value="key.apiKeyId" />
        </el-select>
        <el-select v-model="filters.modelId" class="filter-control" clearable placeholder="全部模型">
          <el-option v-for="model in modelOptions" :key="model.value" :label="model.label" :value="model.value" />
        </el-select>
        <el-select v-model="filters.status" class="filter-control" clearable placeholder="全部状态">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </div>

      <el-alert v-if="statsError" :title="statsError" type="error" show-icon :closable="false">
        <template #default>
          <el-button text type="primary" @click="loadStats">重新加载</el-button>
        </template>
      </el-alert>

      <div v-loading="statsLoading" class="summary-grid reference-summary">
        <article v-for="card in summaryCards" :key="card.label" class="summary-card" :class="`tone-${card.tone}`">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </article>
        <article class="summary-card token-card" :class="tokenSummary.hasData ? 'tone-blue' : 'unavailable-card'">
          <span>Token 使用量</span>
          <div class="token-values">
            <div class="token-row"><em>输入</em><strong>{{ tokenSummary.input }}</strong></div>
            <div class="token-row"><em>输出</em><strong>{{ tokenSummary.output }}</strong></div>
            <div class="token-row"><em>总计</em><strong>{{ tokenSummary.total }}</strong></div>
          </div>
        </article>
      </div>

      <div v-if="!statsLoading && !statsError && !hasAnyChartData && summaryData?.totalCalls === 0" class="usage-empty">
        <el-empty description="当前筛选条件下暂无调用数据" :image-size="86" />
      </div>

      <div class="chart-layout reference-dashboard">
        <section class="chart-card trend-card">
          <div class="chart-heading">
            <div>
              <h3>调用趋势</h3>
            <span class="chart-total">{{ formatNumber(summaryData?.totalCalls ?? 0) }}</span>
            </div>
          </div>
          <el-alert v-if="trendError" :title="trendError" type="error" :closable="false" show-icon />
          <div v-else-if="hasTrendData" ref="trendChartRef" class="chart-canvas"></div>
          <el-empty v-else description="暂无趋势数据" :image-size="64" />
        </section>

        <section class="chart-card">
          <div class="chart-heading">
            <div>
              <h3>模型调用占比</h3>
            <span class="chart-total error-total">{{ formatNumber(summaryData?.failedCalls ?? 0) }}</span>
            </div>
          </div>
          <el-alert v-if="modelStatsError" :title="modelStatsError" type="error" :closable="false" show-icon />
          <div v-else-if="hasModelStats" ref="modelChartRef" class="chart-canvas compact-chart"></div>
          <el-empty v-else description="暂无模型调用数据" :image-size="64" />
        </section>

        <section class="chart-card">
          <div class="chart-heading">
            <div>
              <h3>API Key 调用情况</h3>
            <span class="chart-total">Top models</span>
            </div>
          </div>
          <el-alert v-if="keyStatsError" :title="keyStatsError" type="error" :closable="false" show-icon />
          <div v-else-if="hasKeyStats" ref="keyChartRef" class="chart-canvas compact-chart"></div>
          <el-empty v-else description="暂无 API Key 调用数据" :image-size="64" />
        </section>
      </div>

      </template>
      <section v-if="activePage === 'billing'" class="log-card">
        <div class="chart-heading log-heading">
          <div>
            <h3>调用记录</h3>
          </div>
          <span>{{ formatNumber(callLogTotal) }} 条</span>
        </div>

        <el-alert v-if="logsError" :title="logsError" type="error" show-icon :closable="false" style="margin-bottom:14px">
          <template #default>
            <el-button text type="primary" @click="loadCallLogs">重新加载</el-button>
          </template>
        </el-alert>

        <el-table v-loading="logsLoading" :data="callLogRecords" stripe empty-text="暂无调用记录">
          <el-table-column label="时间" min-width="168">
            <template #default="{ row }: { row: ApiCallLog }">{{ formatDate(row.requestTime ?? row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="API Key" min-width="150">
            <template #default="{ row }: { row: ApiCallLog }">
              {{ keyName(row.apiKeyId) }}
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="160">
            <template #default="{ row }: { row: ApiCallLog }">
              {{ row.modelName ?? modelName(row.modelId) }}
            </template>
          </el-table-column>
          <el-table-column label="接口" min-width="190">
            <template #default="{ row }: { row: ApiCallLog }">
              <span class="path-cell"><b>{{ row.method ?? row.requestMethod }}</b>{{ row.path ?? row.requestPath }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }: { row: ApiCallLog }">
              <el-tag :type="logStatusType(row)" effect="light">{{ logStatusLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="HTTP" width="82">
            <template #default="{ row }: { row: ApiCallLog }">{{ row.statusCode ?? row.httpStatus }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="100">
            <template #default="{ row }: { row: ApiCallLog }">{{ row.costTimeMs ?? row.costTime ?? 0 }} ms</template>
          </el-table-column>
          <el-table-column label="错误信息" min-width="180" show-overflow-tooltip>
            <template #default="{ row }: { row: ApiCallLog }">{{ row.errorMessage }}</template>
          </el-table-column>
        </el-table>

        <div class="pagination-row">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="callLogTotal"
            :current-page="logPage.pageNum"
            :page-size="logPage.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="(val: number) => { logPage.pageNum = val }"
            @size-change="(val: number) => { logPage.pageSize = val; logPage.pageNum = 1 }"
          />
        </div>
      </section>
    </section>

    <el-dialog v-model="rechargeDialogVisible" title="钱包充值" width="440px">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="充值金额" required>
          <el-input-number
            v-model="rechargeForm.amount"
            :min="1"
            :max="100000"
            :step="50"
            controls-position="right"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="支付渠道">
          <el-select v-model="rechargeForm.channel" style="width: 220px">
            <el-option label="模拟支付" value="MOCK" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="微信支付" value="WECHAT" />
            <el-option label="银行转账" value="BANK" />
          </el-select>
          <p class="form-tip">当前环境会模拟支付成功并立即入账。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rechargeLoading" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createDialogVisible" title="创建 API Key" width="480px" @closed="resetCreateForm">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="名称" required>
          <el-input
            v-model="createForm.keyName"
            maxlength="128"
            show-word-limit
            placeholder="例如：生产环境预测服务"
          />
        </el-form-item>
        <el-form-item label="有效期（天）">
          <el-input-number
            v-model="createForm.expireDays"
            :min="1"
            :max="3650"
            :step="30"
            controls-position="right"
          />
          <p class="form-tip">后端允许 1–3650 天，到期后 Key 将无法继续调用。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreateKey">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resultDialogVisible"
      title="保存你的 API Key"
      width="560px"
      :close-on-click-modal="false"
      @closed="clearOneTimeKey"
    >
      <div class="key-result">
        <el-alert
          title="完整 Key 只在本次响应中展示，关闭后无法再次查看。"
          type="warning"
          show-icon
          :closable="false"
        />
        <div v-if="oneTimeKey()" class="one-time-key">
          <code>{{ oneTimeKey() }}</code>
          <el-button type="primary" plain @click="copyText(oneTimeKey())">复制</el-button>
        </div>
        <el-empty v-else description="后端本次响应未返回完整 API Key" :image-size="72" />
      </div>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editKeyDialogVisible" title="修改 API Key 名称" width="440px">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="名称" required>
          <el-input
            v-model="editKeyForm.keyName"
            maxlength="128"
            show-word-limit
            placeholder="例如：生产环境预测服务"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editKeyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="submitEditKeyName">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.api-page {
  display: grid;
  gap: 28px;
}

.key-section,
.usage-section {
  display: grid;
  gap: 18px;
}

.usage-section {
  padding-top: 28px;
  border-top: 1px solid var(--color-border);
}

.section-heading,
.heading-actions,
.chart-heading,
.key-actions,
.one-time-key {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.section-heading {
  align-items: flex-end;
}

.key-heading > div,
.usage-heading > div {
  max-width: 880px;
}

.section-kicker {
  margin: 0 0 5px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.section-heading h1,
.section-heading h2,
.chart-heading h3 {
  margin: 0;
  color: #10274c;
}

.section-heading h1 {
  font-size: 32px;
}

.section-heading h2 {
  font-size: 26px;
}

.security-copy,
.section-heading p:not(.section-kicker),
.chart-heading p,
.form-tip {
  margin: 7px 0 0;
  color: var(--color-muted);
  line-height: 1.7;
}

.key-table-shell,
.chart-card,
.log-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #dce6f1;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(20, 65, 120, 0.05);
}

.wallet-section {
  display: grid;
  gap: 16px;
  padding: 22px;
  border: 1px solid #dce6f1;
  border-radius: 14px;
  background: linear-gradient(135deg, #fff 0%, #f6f9fd 100%);
  box-shadow: 0 8px 24px rgba(20, 65, 120, 0.05);
}

.wallet-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.wallet-heading h2 {
  margin: 0;
  color: #10274c;
  font-size: 24px;
}

.wallet-heading p:not(.section-kicker),
.wallet-disclaimer {
  margin: 6px 0 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.65;
}

.wallet-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.wallet-records {
  display: grid;
  gap: 10px;
}

.wallet-records-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.wallet-records-heading h3 {
  margin: 0;
  font-size: 16px;
}

.wallet-records-heading span {
  color: var(--color-text-muted);
  font-size: 13px;
}

.wallet-grid article {
  padding: 18px 20px;
  border: 1px solid #e1e8f1;
  border-radius: 12px;
  background: #fff;
}

.wallet-grid span,
.wallet-grid small {
  display: block;
  color: #728198;
  font-size: 12px;
}

.wallet-grid strong {
  display: block;
  margin: 10px 0 8px;
  color: #10274c;
  font-size: 28px;
}

.key-table :deep(.el-table__header th) {
  height: 54px;
  color: #526278;
  background: #f7f9fc;
}

.key-table :deep(.el-table__row td) {
  height: 66px;
}

.masked-key {
  color: #34455d;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
}

.key-actions {
  justify-content: flex-start;
  gap: 3px;
  white-space: nowrap;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px;
  border: 1px solid #dce6f1;
  border-radius: 10px;
  background: #ffffff;
}

.filter-control {
  width: 190px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  min-height: 136px;
}

.summary-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 132px;
  padding: 18px;
  overflow: hidden;
  flex-direction: column;
  border: 1px solid #dce6f1;
  border-radius: 12px;
  background: #ffffff;
}

.summary-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  background: #1d6fdc;
  content: '';
}

.summary-card.tone-green::before {
  background: #27a67a;
}

.summary-card.tone-red::before {
  background: #dc6570;
}

.summary-card.tone-purple::before {
  background: #8069dd;
}

.summary-card > span,
.summary-card small {
  color: #728198;
  font-size: 12px;
}

.summary-card strong {
  display: block;
  margin: 12px 0 8px;
  overflow: hidden;
  color: #10274c;
  font-size: 25px;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-card.tone-blue::before {
  background: #1d6fdc;
}

.unavailable-card {
  background: #f8fafc;
}

.unavailable-card::before {
  background: #a5b1c1;
}

.unavailable-card .el-tag {
  align-self: flex-start;
  margin-top: auto;
}

.token-values {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 8px 0;
}

.token-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.token-row em {
  color: #728198;
  font-size: 12px;
  font-style: normal;
}

.token-row strong {
  margin: 0;
  font-size: 15px;
}

.usage-empty {
  display: grid;
  min-height: 260px;
  place-items: center;
  border: 1px dashed #d1dce9;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.65);
}

.chart-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.trend-card {
  grid-column: 1 / -1;
}

.chart-card,
.log-card {
  padding: 18px;
}

.chart-heading {
  align-items: flex-start;
}

.chart-heading h3 {
  font-size: 17px;
}

.chart-heading p {
  font-size: 12px;
}

.chart-canvas {
  width: 100%;
  height: 330px;
  margin-top: 8px;
}

.compact-chart {
  height: 300px;
}

.log-card {
  display: grid;
  gap: 16px;
}

.log-heading > span {
  color: var(--color-muted);
  font-size: 13px;
}

.path-cell {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
}

.path-cell b {
  padding: 2px 5px;
  border-radius: 4px;
  color: #27649f;
  background: #eaf3ff;
  font-size: 10px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
}

.form-tip {
  font-size: 12px;
}

.key-result {
  display: grid;
  gap: 16px;
}

.one-time-key {
  align-items: stretch;
  padding: 12px;
  border: 1px solid #c9d8e9;
  border-radius: 10px;
  background: #f7faff;
}

.one-time-key code {
  min-width: 0;
  padding: 8px 4px;
  overflow-wrap: anywhere;
  color: #10274c;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
}

@media (max-width: 1280px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-grid,
  .chart-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.overview-section { display: grid; gap: 20px; }
.key-heading { align-items: center; }
.wallet-heading h1 { margin: 0; color: #111827; font-size: 28px; font-weight: 650; letter-spacing: -0.02em; }
.wallet-heading { align-items: center; }
.wallet-grid article { min-height: 128px; padding: 22px 24px; border-radius: 8px; }
.wallet-grid strong { margin: 14px 0 8px; font-size: 32px; font-weight: 650; letter-spacing: -0.02em; }
.wallet-records-heading h2 { margin: 0; color: #111827; font-size: 18px; font-weight: 600; }
.usage-section:has(.log-card) { padding-top: 20px; border-top: 0; }
.log-card { border-radius: 8px; }
.key-heading h1 { color: #111827; font-size: 28px; font-weight: 650; letter-spacing: -0.02em; }
.page-description { margin: 8px 0 0 !important; color: #6b7280 !important; font-size: 14px; line-height: 1.5 !important; }
.key-security-alert { border: 1px solid #f0dfb1; border-radius: 6px; background: #fffbeb; }
.key-security-alert :deep(.el-alert__title) { color: #765b18; font-size: 13px; font-weight: 500; }
.key-table-shell { border-radius: 6px; }
.key-table :deep(.el-table__header th) { height: 46px; color: #6b7280; background: #fafafa; font-size: 12px; font-weight: 500; text-transform: uppercase; }
.key-table :deep(.el-table__row td) { height: 58px; }
.key-table :deep(.el-table__row:hover > td) { background: #fafafa !important; }
.key-actions :deep(.el-button) { font-size: 13px; }
.overview-section .section-heading { align-items: center; }
.overview-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.overview-summary article { min-height: 104px; padding: 16px 18px; border: 1px solid #e5e7eb; border-radius: 9px; background: #fff; }
.overview-summary span, .overview-summary small { display: block; color: #6b7280; font-size: 12px; }
.overview-summary strong { display: block; margin: 11px 0 6px; color: #172033; font-size: 26px; line-height: 1; }
.model-panel, .service-note { padding: 20px; border: 1px solid #e5e7eb; border-radius: 9px; background: #fff; }
.panel-heading, .service-note { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.panel-heading h2, .service-note h2 { margin: 0; color: #172033; font-size: 18px; }
.panel-heading p, .service-note p { margin: 6px 0 0; color: #6b7280; font-size: 13px; }
.model-tools { display: flex; align-items: center; gap: 10px; }
.model-tools .el-input { width: 220px; }
.model-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 18px; }
.model-card { min-width: 0; padding: 16px; border: 1px solid #e5e7eb; border-radius: 8px; }
.model-card-head { display: flex; align-items: flex-start; gap: 10px; }
.model-card-head > div { min-width: 0; flex: 1; }
.model-glyph { width: 36px; height: 36px; flex: none; border-radius: 9px; object-fit: cover; }
.model-card h3 { margin: 0; overflow: hidden; color: #172033; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.model-card-head p { margin: 3px 0 0; color: #7b8492; font-size: 12px; }
.model-description { display: -webkit-box; min-height: 36px; margin: 14px 0 12px; overflow: hidden; color: #4b5563; font-size: 13px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.model-meta { display: flex; align-items: center; justify-content: space-between; color: #8a93a1; font-size: 12px; }
.model-meta code { max-width: 72%; overflow: hidden; color: #536174; font-family: Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.model-tags { display: flex; gap: 6px; margin-top: 12px; }
.service-note { align-items: flex-start; background: #f8fafc; }
.service-links { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 4px; }
.key-section, .usage-section { gap: 16px; }
.key-table-shell, .chart-card, .log-card, .wallet-section { border-color: #e5e7eb; border-radius: 9px; box-shadow: none; }
.wallet-section { padding: 20px; background: #fff; }
.summary-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); min-height: 116px; }
.summary-card { min-height: 116px; border-color: #e5e7eb; border-radius: 9px; }
.chart-canvas { height: 280px; }
.compact-chart { height: 270px; }

@media (max-width: 1024px) {
  .overview-summary, .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .model-grid { grid-template-columns: 1fr; }
  .chart-layout { grid-template-columns: 1fr; }
  .trend-card { grid-column: auto; }
}
@media (max-width: 640px) {
  .overview-summary, .summary-grid, .wallet-grid { grid-template-columns: 1fr; }
  .panel-heading, .service-note { align-items: flex-start; flex-direction: column; }
  .model-tools { width: 100%; flex-wrap: wrap; }
  .model-tools .el-input { width: 100%; }
  .service-links { justify-content: flex-start; }
}

/* Usage dashboard: compact, data-first layout. */
.usage-section { gap: 14px; }
.usage-heading h2 { color: #111827; font-size: 26px; font-weight: 650; letter-spacing: -0.02em; }
.usage-heading > div > p { margin-top: 5px !important; color: #8a93a1 !important; font-size: 12px !important; }
.filter-bar { align-items: center; padding: 10px; border-color: #e7e9ed; border-radius: 7px; background: #fff; }
.filter-control { width: 158px; }
.summary-grid { grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; min-height: 108px; }
.summary-card { min-height: 108px; padding: 16px; border-color: #e7e9ed; border-radius: 7px; }
.summary-card::before { height: 2px; }
.summary-card strong { margin: 10px 0 7px; color: #111827; font-size: 26px; font-weight: 650; }
.summary-card small { color: #9299a5; }
.chart-layout { gap: 12px; }
.chart-card { padding: 16px; border-color: #e7e9ed; border-radius: 7px; }
.chart-heading h3 { color: #20252d; font-size: 16px; font-weight: 600; }
.chart-heading p { display: none; }
.chart-canvas { height: 270px; margin-top: 2px; }
.compact-chart { height: 250px; }
.usage-empty { min-height: 220px; border-radius: 7px; }

@media (max-width: 680px) {
  .summary-grid,
  .chart-layout,
  .wallet-grid {
    grid-template-columns: 1fr;
  }

  .wallet-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-control {
    width: 100%;
  }

  .heading-actions,
  .one-time-key {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
  }
}

/* Reference dashboard treatment: quiet canvas, compact cards, chart-first hierarchy. */
.usage-section { gap: 16px; padding: 22px 24px 30px; background: #f5f7f8; border-radius: 18px; }
.usage-heading { margin-bottom: -2px; }
.usage-heading h2 { color: #17212b; font-size: 22px; letter-spacing: -.03em; }
.heading-actions .el-button { border: 1px solid #e4e8eb; border-radius: 8px; background: #fff; }
.reference-toolbar { padding: 0; border: 0; background: transparent; }
.range-tabs { display: flex; padding: 3px; border: 1px solid #e3e8eb; border-radius: 9px; background: #fff; }
.range-tabs button { min-width: 38px; height: 28px; padding: 0 9px; border: 0; border-radius: 6px; color: #75808a; background: transparent; font-size: 11px; cursor: pointer; }
.range-tabs button.active { color: #1769a8; background: #dff2ff; font-weight: 700; }
.reference-toolbar .filter-control { width: 150px; }
.reference-summary { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); min-height: 0; }
.reference-summary .summary-card { min-height: 98px; padding: 14px 16px; border: 1px solid #e6eaec; border-radius: 10px; box-shadow: 0 1px 2px rgba(25,45,60,.03); }
.reference-summary .summary-card::before { display: none; }
.reference-summary .summary-card span { color: #82909b; font-size: 12px; }
.reference-summary .summary-card strong { margin: 8px 0 0; color: #26313a; font-size: 25px; }
.reference-summary .summary-card:nth-child(1) { background: #eef8f3; }
.reference-summary .summary-card:nth-child(2) { background: #f0f7ff; }
.reference-summary .summary-card:nth-child(3) { background: #fff4f2; }
.reference-summary .summary-card:nth-child(4) { background: #f7f2ff; }
.reference-summary .token-card { background: #fff !important; }
.reference-dashboard { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.reference-dashboard .trend-card { grid-column: 1 / -1; grid-row: 2; min-height: 330px; }
.reference-dashboard .chart-card { min-height: 274px; padding: 16px; border: 1px solid #e6eaec; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(25,45,60,.03); }
.reference-dashboard .chart-card:nth-child(2), .reference-dashboard .chart-card:nth-child(3) { grid-row: 1; }
.reference-dashboard .chart-heading { min-height: 42px; }
.reference-dashboard .chart-heading h3 { color: #34414b; font-size: 14px; font-weight: 650; }
.reference-dashboard .chart-heading p { display: none; }
.chart-total { color: #2b3943; font-size: 22px; font-weight: 700; }
.error-total { color: #34414b; }
.reference-dashboard .chart-canvas { height: 205px; margin-top: 8px; }
.reference-dashboard .compact-chart { height: 205px; }

@media (max-width: 680px) { .usage-section { padding: 16px; } .reference-summary, .reference-dashboard { grid-template-columns: 1fr; } .reference-dashboard .trend-card { grid-column: auto; grid-row: 3; } .reference-dashboard .chart-card:nth-child(2), .reference-dashboard .chart-card:nth-child(3) { grid-row: auto; } .reference-toolbar .filter-control { width: 100%; } }
</style>
