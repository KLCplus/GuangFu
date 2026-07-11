<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getModels } from '../api/model'
import type { ModelListItem } from '../api/model'
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
  ApiUsageTrendItem
} from '../api/open'

interface ApiKeyForm {
  keyName: string
  expireDays: number
}

interface DistributionItem {
  name: string
  value: number
}

// ---- state ----

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

const apiKeys = ref<ApiKey[]>([])
const models = ref<ModelListItem[]>([])

// backend aggregation data
const summaryData = ref<ApiUsageSummary | null>(null)
const trendApiData = ref<ApiUsageTrendItem[]>([])
const modelApiData = ref<ApiUsageByModelItem[]>([])
const keyApiData = ref<ApiUsageByKeyItem[]>([])

// server-side paginated call logs
const callLogRecords = ref<ApiCallLog[]>([])
const callLogTotal = ref(0)

const createDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const createdKey = ref<ApiKey | null>(null)
const editKeyDialogVisible = ref(false)
const editKeyForm = reactive({ keyName: '', apiKeyId: 0 })

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
      { label: '总调用次数', value: '—', note: '正在加载统计数据…', tone: 'blue' },
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
  if (!s) return { input: '—', output: '—', total: '—', hasData: false }
  const hasData = s.inputTokens > 0 || s.outputTokens > 0 || s.totalTokens > 0
  return {
    input: formatNumber(s.inputTokens),
    output: formatNumber(s.outputTokens),
    total: formatNumber(s.totalTokens),
    hasData
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
    void loadStats()
    void loadCallLogs()
  }
)

watch(logPage, () => {
  void loadCallLogs()
})

watch([trendApiData, modelApiData, keyApiData], () => {
  void nextTick(renderCharts)
})

// ---- data loading ----

async function loadPage() {
  refreshing.value = true
  await Promise.all([loadKeys(), loadModels()])
  await loadStats()
  await loadCallLogs()
  refreshing.value = false
  void nextTick(renderCharts)
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
  try {
    const params = filterParams()
    const [summaryResult, trendResult, modelResult, keyResult] = await Promise.all([
      getUsageSummary(params),
      getUsageTrend(params),
      getUsageByModel(params),
      getUsageByKey(params)
    ])
    summaryData.value = summaryResult
    trendApiData.value = trendResult
    modelApiData.value = modelResult
    keyApiData.value = keyResult
  } catch (error) {
    summaryData.value = null
    trendApiData.value = []
    modelApiData.value = []
    keyApiData.value = []
    statsError.value = errorMessage(error, '统计数据加载失败')
  } finally {
    statsLoading.value = false
  }
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
    const headers = ['时间', 'API Key ID', '模型', '接口', '方法', '状态', 'HTTP', '耗时(ms)', '错误信息', '输入帧数', '输出点数', '总用量']
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
      log.inputTokens ?? 0,
      log.outputTokens ?? 0,
      log.totalTokens ?? 0
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

function renderTrendChart() {
  if (!trendChartRef.value) return
  trendChart = trendChart ?? echarts.init(trendChartRef.value)
  const items = trendData.value
  trendChart.setOption({
    animationDuration: 350,
    color: ['#1d6fdc', '#e05a67'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0, data: ['成功', '失败'] },
    grid: { left: 42, right: 18, top: 42, bottom: 30 },
    xAxis: {
      type: 'category',
      data: items.map((item) => item.date.length > 10 ? item.date.slice(5, 16) : item.date.slice(5)),
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

    <section class="usage-section">
      <div class="section-heading usage-heading">
        <div>
          <p class="section-kicker">Usage analytics</p>
          <h2>使用统计</h2>
          <p>{{ statsCoverageText }}</p>
        </div>
        <div class="heading-actions">
          <el-button :loading="exporting" @click="handleExport">导出 CSV</el-button>
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

      <el-alert v-if="statsError" :title="statsError" type="error" show-icon :closable="false">
        <template #default>
          <el-button text type="primary" @click="loadStats">重新加载</el-button>
        </template>
      </el-alert>

      <div v-loading="statsLoading" class="summary-grid">
        <article v-for="card in summaryCards" :key="card.label" class="summary-card" :class="`tone-${card.tone}`">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.note }}</small>
        </article>
        <article class="summary-card token-card" :class="tokenSummary.hasData ? 'tone-blue' : 'unavailable-card'">
          <span>模型调用用量</span>
          <div class="token-values">
            <div class="token-row"><em>输入帧</em><strong>{{ tokenSummary.input }}</strong></div>
            <div class="token-row"><em>输出点</em><strong>{{ tokenSummary.output }}</strong></div>
            <div class="token-row"><em>总计</em><strong>{{ tokenSummary.total }}</strong></div>
          </div>
          <small v-if="tokenSummary.hasData">后端按输入帧和输出点聚合</small>
          <small v-else>当前筛选范围暂无用量数据</small>
          <el-tag v-if="!tokenSummary.hasData" size="small" type="info" effect="plain">暂无数据</el-tag>
        </article>
      </div>

      <div v-if="!statsLoading && !statsError && !logsLoading && callLogTotal === 0" class="usage-empty">
        <el-empty description="当前筛选条件下暂无调用数据" :image-size="86" />
      </div>

      <div v-show="callLogTotal > 0" class="chart-layout">
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
            <p>数据来自后端 GET /api/open/call-logs，支持时间、Key、模型多条件服务端筛选和分页。</p>
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
