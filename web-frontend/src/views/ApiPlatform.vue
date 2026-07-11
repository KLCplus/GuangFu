<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getModels } from '../api/model'
import type { ModelListItem } from '../api/model'
import {
  applyApiKey,
  deleteApiKey,
  getApiKeys,
  getCallLogs,
  resetOpenApiKey,
  updateApiKeyStatus
} from '../api/open'
import type { ApiCallLog, ApiKey, ApiKeyApplyPayload } from '../api/open'

interface ApiKeyForm {
  keyName: string
  expireDays: number
}

interface NormalizedCallLog {
  logId: number
  apiKeyId?: number
  modelId?: number
  path: string
  method: string
  requestTime: string
  status: string
  statusCode?: number
  costTime: number
  errorMessage?: string
}

interface DistributionItem {
  name: string
  value: number
}

const MAX_STATS_LOGS = 1000
const STATS_PAGE_SIZE = 100

const keysLoading = ref(false)
const statsLoading = ref(false)
const refreshing = ref(false)
const actionLoadingId = ref<number | null>(null)
const createLoading = ref(false)
const keyError = ref('')
const statsError = ref('')

const apiKeys = ref<ApiKey[]>([])
const models = ref<ModelListItem[]>([])
const statsLogs = ref<NormalizedCallLog[]>([])
const statsSourceTotal = ref(0)
const statsTruncated = ref(false)

const createDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const createdKey = ref<ApiKey | null>(null)

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
  { label: '已读取全部', value: 0 }
]

const modelNameMap = computed(() => new Map(models.value.map((model) => [model.modelId, model.modelName])))
const keyNameMap = computed(() => new Map(apiKeys.value.map((key) => [key.apiKeyId, key.keyName])))

const modelOptions = computed(() => {
  const ids = new Set(statsLogs.value.map((log) => log.modelId).filter((id): id is number => id != null))
  return Array.from(ids).map((modelId) => ({
    value: modelId,
    label: modelName(modelId)
  }))
})

const filteredLogs = computed(() => {
  const cutoff = filters.days ? startOfDaysAgo(filters.days - 1) : null
  return statsLogs.value.filter((log) => {
    if (filters.apiKeyId != null && log.apiKeyId !== filters.apiKeyId) return false
    if (filters.modelId != null && log.modelId !== filters.modelId) return false
    if (filters.status && normalizeLogStatus(log) !== filters.status) return false
    if (cutoff) {
      const requestTime = parseDate(log.requestTime)
      if (!requestTime || requestTime < cutoff) return false
    }
    return true
  })
})

const summary = computed(() => {
  const logs = filteredLogs.value
  const successCalls = logs.filter(isSuccessfulLog).length
  const failedCalls = logs.length - successCalls
  const totalLatency = logs.reduce((sum, log) => sum + log.costTime, 0)
  return {
    totalCalls: logs.length,
    successCalls,
    failedCalls,
    successRate: logs.length ? Number(((successCalls / logs.length) * 100).toFixed(1)) : 0,
    avgLatency: logs.length ? Math.round(totalLatency / logs.length) : 0
  }
})

const summaryCards = computed(() => [
  {
    label: '总调用次数',
    value: formatNumber(summary.value.totalCalls),
    note: '基于当前已读取日志与筛选条件',
    tone: 'blue'
  },
  {
    label: '成功调用',
    value: formatNumber(summary.value.successCalls),
    note: `成功率 ${summary.value.successRate}%`,
    tone: 'green'
  },
  {
    label: '失败调用',
    value: formatNumber(summary.value.failedCalls),
    note: 'HTTP 或业务状态判定失败',
    tone: 'red'
  },
  {
    label: '平均响应时间',
    value: `${formatNumber(summary.value.avgLatency)} ms`,
    note: '调用日志 costTimeMs 平均值',
    tone: 'purple'
  }
])

const trendData = computed(() => {
  const grouped = new Map<string, { success: number; failed: number }>()
  filteredLogs.value.forEach((log) => {
    const day = log.requestTime.slice(0, 10)
    if (!day) return
    const item = grouped.get(day) ?? { success: 0, failed: 0 }
    if (isSuccessfulLog(log)) item.success += 1
    else item.failed += 1
    grouped.set(day, item)
  })
  return Array.from(grouped.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, value]) => ({ date, ...value }))
})

const modelDistribution = computed<DistributionItem[]>(() => groupDistribution(
  filteredLogs.value,
  (log) => log.modelId == null ? '未记录模型' : modelName(log.modelId)
))

const keyDistribution = computed<DistributionItem[]>(() => groupDistribution(
  filteredLogs.value,
  (log) => log.apiKeyId == null ? '未记录 Key' : keyName(log.apiKeyId)
))

const visibleLogs = computed(() => {
  const start = (logPage.pageNum - 1) * logPage.pageSize
  return filteredLogs.value.slice(start, start + logPage.pageSize)
})

const statsCoverageText = computed(() => {
  if (statsLoading.value) return '正在读取调用日志…'
  if (statsError.value) return '调用日志读取失败，当前无法生成统计。'
  if (statsTruncated.value) {
    return `后端共有 ${formatNumber(statsSourceTotal.value)} 条日志，当前读取最近 ${formatNumber(statsLogs.value.length)} 条进行前端聚合。`
  }
  return `已读取后端返回的全部 ${formatNumber(statsLogs.value.length)} 条调用日志。`
})

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
  }
)

watch(filteredLogs, () => {
  void nextTick(renderCharts)
})

async function loadPage() {
  refreshing.value = true
  await Promise.all([loadKeys(), loadStatsLogs(), loadModels()])
  refreshing.value = false
  await nextTick(renderCharts)
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

async function loadStatsLogs() {
  statsLoading.value = true
  statsError.value = ''
  try {
    const firstPage = await getCallLogs({ pageNum: 1, pageSize: STATS_PAGE_SIZE })
    const targetCount = Math.min(firstPage.total, MAX_STATS_LOGS)
    const pageCount = Math.ceil(targetCount / STATS_PAGE_SIZE)
    const remainingPages = pageCount > 1
      ? await Promise.all(
          Array.from({ length: pageCount - 1 }, (_, index) =>
            getCallLogs({ pageNum: index + 2, pageSize: STATS_PAGE_SIZE })
          )
        )
      : []
    const records = [firstPage, ...remainingPages].flatMap((page) => page.records).slice(0, targetCount)
    statsLogs.value = records.map(normalizeCallLog)
    statsSourceTotal.value = firstPage.total
    statsTruncated.value = firstPage.total > statsLogs.value.length
  } catch (error) {
    statsLogs.value = []
    statsSourceTotal.value = 0
    statsTruncated.value = false
    statsError.value = errorMessage(error, '调用日志加载失败')
  } finally {
    statsLoading.value = false
  }
}

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
    await Promise.all([loadKeys(), loadStatsLogs()])
  } catch (error) {
    ElMessage.error(errorMessage(error, 'API Key 删除失败'))
  } finally {
    actionLoadingId.value = null
  }
}

function resetCreateForm() {
  createForm.keyName = ''
  createForm.expireDays = 90
}

function clearOneTimeKey() {
  createdKey.value = null
}

function normalizeCallLog(log: ApiCallLog): NormalizedCallLog {
  return {
    logId: log.logId,
    apiKeyId: log.apiKeyId,
    modelId: log.modelId,
    path: log.path ?? log.requestPath ?? '',
    method: log.method ?? log.requestMethod ?? '',
    requestTime: log.requestTime ?? log.createdAt ?? '',
    status: (log.status ?? log.bizStatus ?? '').toUpperCase(),
    statusCode: log.statusCode ?? log.httpStatus,
    costTime: log.costTimeMs ?? log.costTime ?? 0,
    errorMessage: log.errorMessage
  }
}

function groupDistribution(logs: NormalizedCallLog[], getName: (log: NormalizedCallLog) => string) {
  const grouped = new Map<string, number>()
  logs.forEach((log) => {
    const name = getName(log)
    grouped.set(name, (grouped.get(name) ?? 0) + 1)
  })
  return Array.from(grouped.entries())
    .map(([name, value]) => ({ name, value }))
    .sort((left, right) => right.value - left.value)
}

function renderCharts() {
  renderTrendChart()
  renderModelChart()
  renderKeyChart()
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  trendChart = trendChart ?? echarts.init(trendChartRef.value)
  trendChart.setOption({
    animationDuration: 350,
    color: ['#1d6fdc', '#e05a67'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0, data: ['成功', '失败'] },
    grid: { left: 42, right: 18, top: 42, bottom: 30 },
    xAxis: {
      type: 'category',
      data: trendData.value.map((item) => item.date.slice(5)),
      axisLine: { lineStyle: { color: '#d9e3ef' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf2f7' } }
    },
    series: [
      {
        name: '成功',
        type: 'line',
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 7,
        areaStyle: { color: 'rgba(29, 111, 220, 0.08)' },
        data: trendData.value.map((item) => item.success)
      },
      {
        name: '失败',
        type: 'line',
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 7,
        data: trendData.value.map((item) => item.failed)
      }
    ]
  }, true)
}

function renderModelChart() {
  if (!modelChartRef.value) return
  modelChart = modelChart ?? echarts.init(modelChartRef.value)
  modelChart.setOption({
    animationDuration: 350,
    color: ['#1d6fdc', '#4ba3f2', '#58b89b', '#8069dd', '#e6a23c', '#e05a67'],
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 次（{d}%）' },
    legend: { type: 'scroll', bottom: 0, left: 'center' },
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
  keyChart.setOption({
    animationDuration: 350,
    color: ['#1d6fdc'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 20, right: 18, top: 10, bottom: 20, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf2f7' } }
    },
    yAxis: {
      type: 'category',
      data: data.map((item) => item.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { width: 110, overflow: 'truncate' }
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

function normalizeLogStatus(log: NormalizedCallLog) {
  if (log.status === 'SUCCESS') return 'SUCCESS'
  if (log.status === 'FAILED') return 'FAILED'
  return (log.statusCode ?? 200) < 400 ? 'SUCCESS' : 'FAILED'
}

function isSuccessfulLog(log: NormalizedCallLog) {
  return normalizeLogStatus(log) === 'SUCCESS'
}

function logStatusLabel(log: NormalizedCallLog) {
  return isSuccessfulLog(log) ? '成功' : '失败'
}

function logStatusType(log: NormalizedCallLog) {
  return isSuccessfulLog(log) ? 'success' : 'danger'
}

function maskedKey(key: ApiKey) {
  const prefix = key.apiKeyPrefix?.trim()
  return prefix ? `${prefix}_${'•'.repeat(18)}` : '未返回 Key 前缀'
}

function oneTimeKey() {
  return createdKey.value?.apiKey?.trim() ?? ''
}

function keyName(apiKeyId: number) {
  return keyNameMap.value.get(apiKeyId) ?? `Key #${apiKeyId}`
}

function modelName(modelId: number) {
  return modelNameMap.value.get(modelId) ?? `模型 #${modelId}`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatDate(value?: string) {
  if (!value) return '从未使用'
  return value.replace('T', ' ')
}

function parseDate(value?: string) {
  if (!value) return null
  const parsed = new Date(value.replace(' ', 'T'))
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function startOfDaysAgo(days: number) {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  date.setDate(date.getDate() - days)
  return date
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
    <section class="key-section">
      <div class="section-heading key-heading">
        <div>
          <p class="section-kicker">Access credentials</p>
          <h1>API Keys</h1>
          <p class="security-copy">
            完整 API Key 仅在创建或重新生成时展示一次。请妥善保存，不要在浏览器脚本、公开仓库或客户端代码中暴露密钥。
          </p>
        </div>
        <el-button type="primary" size="large" @click="createDialogVisible = true">创建 API Key</el-button>
      </div>

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
                <el-tooltip content="后端暂未提供修改 Key 名称接口" placement="top">
                  <span><el-button link disabled>编辑</el-button></span>
                </el-tooltip>
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

    <section class="usage-section">
      <div class="section-heading usage-heading">
        <div>
          <p class="section-kicker">Usage analytics</p>
          <h2>使用统计</h2>
          <p>{{ statsCoverageText }}</p>
        </div>
        <div class="heading-actions">
          <el-tooltip content="后端暂未提供统计导出接口" placement="top">
            <span><el-button disabled>导出</el-button></span>
          </el-tooltip>
          <el-button :loading="refreshing" @click="loadPage">刷新数据</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-select v-model="filters.days" class="filter-control" aria-label="时间范围">
          <el-option v-for="option in timeOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
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

      <el-alert v-if="statsTruncated" type="warning" show-icon :closable="false">
        当前统计只覆盖最近 {{ formatNumber(statsLogs.length) }} 条调用日志；如需全量、按时间或模型精确统计，需要后端补充聚合接口。
      </el-alert>
      <el-alert v-if="statsError" :title="statsError" type="error" show-icon :closable="false">
        <template #default>
          <el-button text type="primary" @click="loadStatsLogs">重新加载</el-button>
        </template>
      </el-alert>

      <div v-loading="statsLoading" class="summary-grid">
        <article v-for="card in summaryCards" :key="card.label" class="summary-card" :class="`tone-${card.tone}`">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.note }}</small>
        </article>
        <article class="summary-card unavailable-card">
          <span>Token 使用量</span>
          <strong>—</strong>
          <small>调用日志暂未返回 Token 字段</small>
          <el-tag size="small" type="info" effect="plain">暂未接通</el-tag>
        </article>
      </div>

      <div v-if="!statsLoading && !statsError && filteredLogs.length === 0" class="usage-empty">
        <el-empty description="当前筛选条件下暂无调用数据" :image-size="86" />
      </div>

      <div v-show="filteredLogs.length" class="chart-layout">
        <section class="chart-card trend-card">
          <div class="chart-heading">
            <div>
              <h3>调用趋势</h3>
              <p>按调用日期聚合成功与失败次数</p>
            </div>
          </div>
          <div ref="trendChartRef" class="chart-canvas"></div>
        </section>

        <section class="chart-card">
          <div class="chart-heading">
            <div>
              <h3>模型调用占比</h3>
              <p>根据调用日志中的 modelId 聚合</p>
            </div>
          </div>
          <div ref="modelChartRef" class="chart-canvas compact-chart"></div>
        </section>

        <section class="chart-card">
          <div class="chart-heading">
            <div>
              <h3>API Key 调用情况</h3>
              <p>最多展示当前筛选结果中的前 8 个 Key</p>
            </div>
          </div>
          <div ref="keyChartRef" class="chart-canvas compact-chart"></div>
        </section>
      </div>

      <section class="log-card">
        <div class="chart-heading log-heading">
          <div>
            <h3>调用记录</h3>
            <p>记录来自 GET /api/open/call-logs，当前表格对已读取日志进行前端筛选和分页。</p>
          </div>
          <span>{{ formatNumber(filteredLogs.length) }} 条</span>
        </div>

        <el-table v-loading="statsLoading" :data="visibleLogs" stripe empty-text="暂无调用记录">
          <el-table-column label="时间" min-width="168">
            <template #default="{ row }: { row: NormalizedCallLog }">{{ formatDate(row.requestTime) }}</template>
          </el-table-column>
          <el-table-column label="API Key" min-width="150">
            <template #default="{ row }: { row: NormalizedCallLog }">
              {{ row.apiKeyId == null ? '未记录' : keyName(row.apiKeyId) }}
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="160">
            <template #default="{ row }: { row: NormalizedCallLog }">
              {{ row.modelId == null ? '未记录' : modelName(row.modelId) }}
            </template>
          </el-table-column>
          <el-table-column label="接口" min-width="190">
            <template #default="{ row }: { row: NormalizedCallLog }">
              <span class="path-cell"><b>{{ row.method }}</b>{{ row.path }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }: { row: NormalizedCallLog }">
              <el-tag :type="logStatusType(row)" effect="light">{{ logStatusLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="statusCode" label="HTTP" width="82" />
          <el-table-column label="耗时" width="100">
            <template #default="{ row }: { row: NormalizedCallLog }">{{ row.costTime }} ms</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
        </el-table>

        <div class="pagination-row">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="filteredLogs.length"
            :current-page="logPage.pageNum"
            :page-size="logPage.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="logPage.pageNum = $event"
            @size-change="logPage.pageSize = $event; logPage.pageNum = 1"
          />
        </div>
      </section>
    </section>

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

@media (max-width: 680px) {
  .summary-grid,
  .chart-layout {
    grid-template-columns: 1fr;
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
</style>
