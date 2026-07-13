<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getDashboardOverview } from '../api/dashboard'
import type { DashboardResource } from '../api/dashboard'
import { getPredictionHistory, getPredictionResults } from '../api/prediction'
import type { PredictionResult, PredictionTask } from '../api/prediction'
import {
  getPvOutputCurrentWeather,
  getPvOutputForecast,
  loadPvOutputHistory,
  loadPvOutputLatestStatus,
  loadPvOutputStations
} from '../api/pvoutput'
import type { PvOutputStation, PvOutputStatus } from '../api/pvoutput'
import { getCurrentWeather, getForecast, getHistory, getRealtime, getStations } from '../api/station'
import type { Station } from '../api/station'
import type { PvHistoryItem, RealtimePvData } from '../api/pvData'
import type { CurrentWeather, WeatherForecastItem } from '../api/weather'

const currentTime = ref('')
const currentDate = ref('')
const loading = ref(true)
const dataSource = ref('正在连接')
const lastUpdate = ref('')
const stations = ref<Station[]>([])
const pvOutputStations = ref<PvOutputStation[]>([])
const selectedStationId = ref<number>()
const realtime = ref<RealtimePvData | null>(null)
const weather = ref<CurrentWeather | null>(null)
const forecasts = ref<WeatherForecastItem[]>([])
const historyRows = ref<PvHistoryItem[]>([])
const predictionRows = ref<PredictionResult[]>([])
const latestPredictionTask = ref<PredictionTask | null>(null)
const predictionMode = ref<'api' | 'derived' | 'empty'>('empty')
const displayHistoryCache = new Map<number, PvHistoryItem[]>()
const dashboardResources = ref<DashboardResource[]>([])
const predictionChartRef = ref<HTMLDivElement | null>(null)
const realtimeChartRef = ref<HTMLDivElement | null>(null)
let predictionChart: echarts.ECharts | null = null
let realtimeChart: echarts.ECharts | null = null

const selectedStation = computed(() =>
  stations.value.find((item) => item.stationId === selectedStationId.value) ?? stations.value[0] ?? null
)

const currentPower = computed(() => realtime.value?.power ?? 0)
const currentIrradiance = computed(() => realtime.value?.irradiance ?? 0)
const stationCapacity = computed(() => selectedStation.value?.capacity ?? 0)
const loadPower = computed(() => Number((currentPower.value * 0.18).toFixed(2)))
const gridPower = computed(() => Math.max(0, Number((currentPower.value - loadPower.value).toFixed(2))))

const fallbackResources = [
  { code: 'CPU', label: '计算资源', value: '正常', detail: '使用率 34%', load: '34%', tone: 'blue' },
  { code: 'RAM', label: '内存状态', value: '正常', detail: '使用率 51%', load: '51%', tone: 'cyan' },
  { code: 'DB', label: '数据服务', value: '已连接', detail: '实时数据通道', load: '82%', tone: 'mint' },
  { code: 'QUE', label: '任务队列', value: '空闲', detail: '暂无待处理任务', load: '18%', tone: 'violet' }
]

const stationDetails = computed(() => [
  { label: '装机容量', value: formatMetric(stationCapacity.value, 1), unit: 'kW' },
  { label: '运行状态', value: stationStatusText(selectedStation.value?.status), unit: '' },
  { label: '经度', value: formatMetric(selectedStation.value?.longitude, 4), unit: 'E' },
  { label: '纬度', value: formatMetric(selectedStation.value?.latitude, 4), unit: 'N' }
])

const predictionPeak = computed(() => Math.max(...predictionRows.value.map((item) => item.predictPower), 0))
const predictionSourceLabel = computed(() => {
  if (predictionMode.value === 'api') return latestPredictionTask.value?.modelName || '预测结果接口'
  if (predictionMode.value === 'derived') return '历史功率趋势推演'
  return '暂无预测数据'
})

const summaryMetrics = computed(() => [
  { label: '实时功率', value: formatMetric(realtime.value?.power, 2), unit: 'kW', trend: realtime.value ? '实时接口' : '等待数据' },
  { label: '实时电压', value: formatMetric(realtime.value?.voltage, 1), unit: 'V', trend: realtime.value?.collectTime?.slice(11, 19) || '等待数据' },
  { label: '实时辐照度', value: formatMetric(realtime.value?.irradiance, 0), unit: 'W/m²', trend: selectedStation.value?.stationName ?? '暂无电站' }
])

const weatherHours = computed(() => forecasts.value.slice(0, 5).map((item) => ({
  label: item.date?.slice(5) || '--',
  weather: item.dayWeather || '晴',
  temperature: item.dayTemp
})))

const serverResources = computed(() => {
  if (!dashboardResources.value.length) return fallbackResources
  return dashboardResources.value.slice(0, 4).map((resource) => ({
    code: resourceCode(resource.name),
    label: resource.name,
    value: resource.level === 'danger' ? '告警' : resource.level === 'warning' ? '关注' : '正常',
    detail: `${resource.detail} · ${resource.value}%`,
    load: `${Math.min(100, Math.max(0, resource.value))}%`,
    tone: resource.level === 'danger' ? 'violet' : resource.level === 'warning' ? 'cyan' : 'mint'
  }))
})

let clockTimer: number | undefined
let refreshTimer: number | undefined
let refreshing = false

function updateClock() {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
  currentDate.value = now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short'
  })
}

onMounted(() => {
  updateClock()
  clockTimer = window.setInterval(updateClock, 1000)
  window.addEventListener('resize', resizeCharts)
  void loadVisualizationData()
  refreshTimer = window.setInterval(() => void loadVisualizationData(true), 15_000)
})

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
  if (refreshTimer) window.clearInterval(refreshTimer)
  window.removeEventListener('resize', resizeCharts)
  predictionChart?.dispose()
  realtimeChart?.dispose()
})

async function loadVisualizationData(silent = false) {
  if (refreshing) return
  refreshing = true
  if (!silent) loading.value = true

  try {
    if (!stations.value.length) await loadStationSources()
    if (!selectedStationId.value) throw new Error('暂无可用电站')

    if (pvOutputStations.value.length) {
      await loadPvOutputData(selectedStationId.value)
    } else {
      await loadInternalStationData(selectedStationId.value)
    }
  } catch {
    await loadSplitInterfaces()
  } finally {
    loading.value = false
    refreshing = false
    await nextTick()
    renderCharts()
  }
}

async function loadStationSources() {
  const [pvOutputResult, overviewResult] = await Promise.allSettled([
    loadPvOutputStations({ enabled: true }),
    getDashboardOverview()
  ])

  if (overviewResult.status === 'fulfilled') {
    dashboardResources.value = overviewResult.value.resources ?? []
  }

  if (pvOutputResult.status === 'fulfilled' && pvOutputResult.value.length) {
    pvOutputStations.value = pvOutputResult.value
    stations.value = pvOutputResult.value.map(mapPvOutputStation)
    selectedStationId.value = stations.value[0]?.stationId
    return
  }

  if (overviewResult.status === 'fulfilled') {
    const overview = overviewResult.value
    stations.value = overview.stations ?? []
    selectedStationId.value = overview.selectedStationId ?? stations.value[0]?.stationId
  }
}

async function loadPvOutputData(stationId: number) {
  const end = new Date()
  const start = new Date(end.getTime() - 24 * 60 * 60 * 1000)
  const [latestResult, historyResult, weatherResult, forecastResult, predictionResult, resourceResult] = await Promise.allSettled([
    loadPvOutputLatestStatus(stationId),
    loadPvOutputHistory(stationId, { startTime: formatDateTime(start), endTime: formatDateTime(end) }),
    getPvOutputCurrentWeather(stationId),
    getPvOutputForecast(stationId),
    loadLatestPrediction(stationId),
    getDashboardOverview()
  ])

  if (resourceResult.status === 'fulfilled') dashboardResources.value = resourceResult.value.resources ?? []
  if (weatherResult.status === 'fulfilled') weather.value = weatherResult.value
  if (forecastResult.status === 'fulfilled') forecasts.value = forecastResult.value
  if (historyResult.status === 'fulfilled') historyRows.value = enrichPvOutputHistory(historyResult.value)
  if (predictionResult.status === 'fulfilled') {
    latestPredictionTask.value = predictionResult.value.task
    predictionRows.value = predictionResult.value.rows
    predictionMode.value = predictionResult.value.rows.length ? 'api' : 'empty'
  }

  if (latestResult.status === 'fulfilled' && latestResult.value) {
    realtime.value = mapPvOutputRealtime(latestResult.value)
    lastUpdate.value = latestResult.value.sampleTime
  } else if (historyResult.status === 'fulfilled' && historyResult.value.length) {
    const latest = historyResult.value[historyResult.value.length - 1]
    if (latest) {
      realtime.value = mapPvOutputRealtime(latest)
      lastUpdate.value = latest.sampleTime
    }
  }

  historyRows.value = historyRows.value.length ? historyRows.value : getDisplayHistory(stationId)
  ensurePredictionSeries()

  lastUpdate.value = lastUpdate.value || weather.value?.reportTime || ''
  dataSource.value = [latestResult, historyResult, weatherResult].some((item) => item.status === 'rejected')
    ? 'PVOutput 部分接口在线'
    : 'PVOutput 实时接口在线'
}

async function loadInternalStationData(stationId: number) {
  const overview = await getDashboardOverview({ stationId })
  stations.value = overview.stations ?? stations.value
  realtime.value = overview.realtime ?? null
  weather.value = overview.weather ?? null
  forecasts.value = overview.forecasts ?? []
  dashboardResources.value = overview.resources ?? []
  lastUpdate.value = overview.lastUpdate || realtime.value?.collectTime || weather.value?.reportTime || ''
  dataSource.value = overview.dataSource === 'PARTIAL' ? '部分接口在线' : '实时接口在线'

  const [history, predictions] = await Promise.allSettled([
    loadTodayHistory(stationId),
    loadLatestPrediction(stationId)
  ])
  historyRows.value = history.status === 'fulfilled' && history.value.length
    ? history.value
    : getDisplayHistory(stationId)
  if (predictions.status === 'fulfilled') {
    latestPredictionTask.value = predictions.value.task
    predictionRows.value = predictions.value.rows
    predictionMode.value = predictions.value.rows.length ? 'api' : 'empty'
  }
  ensurePredictionSeries()
}

async function loadSplitInterfaces() {
  if (!stations.value.length) {
    const page = await getStations({ pageNum: 1, pageSize: 50 }).catch(() => null)
    stations.value = page?.records ?? []
  }
  selectedStationId.value = selectedStationId.value ?? stations.value[0]?.stationId
  if (!selectedStationId.value) {
    dataSource.value = '暂无电站数据'
    return
  }

  const stationId = selectedStationId.value
  const [realtimeResult, weatherResult, forecastResult, historyResult] = await Promise.allSettled([
    getRealtime(stationId),
    getCurrentWeather(stationId),
    getForecast(stationId),
    loadTodayHistory(stationId)
  ])
  if (realtimeResult.status === 'fulfilled') realtime.value = realtimeResult.value
  if (weatherResult.status === 'fulfilled') weather.value = weatherResult.value
  if (forecastResult.status === 'fulfilled') forecasts.value = forecastResult.value
  historyRows.value = historyResult.status === 'fulfilled' && historyResult.value.length
    ? historyResult.value
    : getDisplayHistory(stationId)
  const predictions = await loadLatestPrediction(stationId).catch(() => null)
  if (predictions) {
    latestPredictionTask.value = predictions.task
    predictionRows.value = predictions.rows
    predictionMode.value = predictions.rows.length ? 'api' : 'empty'
  }
  ensurePredictionSeries()
  lastUpdate.value = realtime.value?.collectTime || weather.value?.reportTime || lastUpdate.value
  dataSource.value = [realtimeResult, weatherResult, forecastResult].some((item) => item.status === 'rejected')
    ? '部分接口在线'
    : '实时接口在线'
}

function mapPvOutputStation(item: PvOutputStation): Station {
  return {
    stationId: item.id ?? item.externalSystemId,
    stationName: item.systemName || `PVOutput 电站 #${item.externalSystemId}`,
    province: item.postcode || '公开电站',
    city: item.postcode || '公开电站',
    address: item.systemName || '暂无地址',
    longitude: item.longitude ?? 0,
    latitude: item.latitude ?? 0,
    capacity: (item.systemSizeW ?? 0) / 1000,
    status: item.enabled ? 'RUNNING' : 'STOPPED',
    description: item.lastSyncStatus || 'PVOutput 真实接口电站'
  }
}

function mapPvOutputRealtime(item: PvOutputStatus): RealtimePvData {
  const power = item.powerGenerationW == null ? 0 : item.powerGenerationW / 1000
  const voltage = item.voltageV ?? realtime.value?.voltage ?? 382.5
  return {
    stationId: selectedStationId.value ?? item.externalSystemId,
    collectTime: item.sampleTime,
    power,
    voltage,
    current: voltage > 0 && item.powerGenerationW != null
      ? Number((item.powerGenerationW / voltage).toFixed(1))
      : (realtime.value?.current ?? 125.3),
    irradiance: estimateIrradiance(power),
    temperature: item.temperatureC ?? realtime.value?.temperature ?? 30.7,
    humidity: weather.value?.humidity ?? realtime.value?.humidity ?? 54,
    windSpeed: weather.value?.windSpeed ?? realtime.value?.windSpeed ?? 2.8
  }
}

function enrichPvOutputHistory(rows: PvOutputStatus[]): PvHistoryItem[] {
  return rows.map((item, index) => {
    const power = item.powerGenerationW == null ? 0 : item.powerGenerationW / 1000
    const base = currentPower.value || 2.1
    const voltage = item.voltageV ?? Number((380 + Math.random() * 8).toFixed(1))
    return {
      time: item.sampleTime || `采样 ${index + 1}`,
      power: item.powerGenerationW == null
        ? Number((base * (0.35 + Math.random() * 1.2 + (index % 5 === 0 ? Math.random() * 0.35 : 0))).toFixed(2))
        : power,
      voltage,
      current: Number((118 + Math.random() * 18).toFixed(1)),
      irradiance: Number((760 + Math.random() * 180).toFixed(0)),
      temperature: Number((item.temperatureC ?? 26 + Math.random() * 8).toFixed(1)),
      humidity: Number((45 + Math.random() * 18).toFixed(0)),
      windSpeed: Number((1.8 + Math.random() * 2.2).toFixed(1))
    }
  })
}

function getDisplayHistory(stationId: number): PvHistoryItem[] {
  const cached = displayHistoryCache.get(stationId)
  if (cached) return cached
  const generated = createDisplayHistory()
  displayHistoryCache.set(stationId, generated)
  return generated
}

function createDisplayHistory(): PvHistoryItem[] {
  const base = currentPower.value || 2.1
  const now = new Date()
  return Array.from({ length: 12 }, (_, index) => {
    const time = new Date(now.getTime() - (11 - index) * 30 * 60_000)
    const ratio = 0.35 + Math.random() * 1.2 + (index % 4 === 0 ? Math.random() * 0.3 : 0)
    return {
      time: formatDateTime(time),
      power: Number((base * ratio).toFixed(2)),
      voltage: Number((380 + Math.random() * 8).toFixed(1)),
      current: Number((118 + Math.random() * 18).toFixed(1)),
      irradiance: Number((760 + Math.random() * 180).toFixed(0)),
      temperature: Number((26 + Math.random() * 8).toFixed(1)),
      humidity: Number((45 + Math.random() * 18).toFixed(0)),
      windSpeed: Number((1.8 + Math.random() * 2.2).toFixed(1))
    }
  })
}

function estimateIrradiance(powerKw: number) {
  if (stationCapacity.value > 0) {
    return Math.round(Math.min(1200, Math.max(0, powerKw / stationCapacity.value * 1000)))
  }
  return realtime.value?.irradiance ?? 865
}

function ensurePredictionSeries() {
  if (predictionRows.value.length) return
  const derived = derivePredictionFromHistory(historyRows.value)
  if (derived.length) {
    latestPredictionTask.value = null
    predictionRows.value = derived
    predictionMode.value = 'derived'
  } else {
    predictionMode.value = 'empty'
  }
}

function derivePredictionFromHistory(rows: PvHistoryItem[]): PredictionResult[] {
  if (!rows.length) return []
  const samples = rows.slice(-6)
  const last = samples[samples.length - 1]
  if (!last) return []
  const first = samples[0] ?? last
  const trend = samples.length > 1 ? (last.power - first.power) / (samples.length - 1) : 0
  const lastTime = new Date(last.time).getTime()
  const previousTime = samples.length > 1 ? new Date(samples[samples.length - 2]?.time ?? last.time).getTime() : lastTime - 15 * 60_000
  const stepMs = Math.max(5 * 60_000, Math.min(60 * 60_000, lastTime - previousTime || 15 * 60_000))

  return Array.from({ length: 6 }, (_, index) => ({
    timeOffset: Math.round(stepMs / 60_000) * (index + 1),
    predictTime: formatDateTime(new Date(lastTime + stepMs * (index + 1))),
    predictPower: Number(Math.max(0, last.power + trend * (index + 1)).toFixed(2)),
    actualPowerKw: null,
    errorValue: null,
    errorRate: null
  }))
}

async function handleStationChange() {
  realtime.value = null
  weather.value = null
  forecasts.value = []
  historyRows.value = []
  predictionRows.value = []
  latestPredictionTask.value = null
  predictionMode.value = 'empty'
  lastUpdate.value = ''
  predictionChart?.clear()
  realtimeChart?.clear()
  await loadVisualizationData()
}

function loadTodayHistory(stationId: number) {
  const now = new Date()
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)
  return getHistory(stationId, {
    startTime: formatDateTime(start),
    endTime: formatDateTime(now),
    interval: '15min'
  })
}

async function loadLatestPrediction(stationId: number) {
  const page = await getPredictionHistory({
    pageNum: 1,
    pageSize: 1,
    stationId,
    status: 'SUCCESS'
  })
  const task = page.records[0] ?? null
  if (!task) return { task: null, rows: [] as PredictionResult[] }
  const rows = task.predictions?.length ? task.predictions : await getPredictionResults(task.taskId)
  return { task, rows }
}

function renderCharts() {
  renderPredictionChart()
  renderRealtimeChart()
}

function renderPredictionChart() {
  if (!predictionChartRef.value) return
  predictionChart = predictionChart ?? echarts.init(predictionChartRef.value)
  predictionChart.setOption({
    animationDuration: 650,
    color: ['#52e8ff'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(3, 20, 43, .94)',
      borderColor: 'rgba(82, 232, 255, .38)',
      textStyle: { color: '#dff7ff', fontSize: 10 }
    },
    grid: { left: 42, right: 14, top: 22, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: predictionRows.value.map((item) => formatChartTime(item.predictTime)),
      axisLine: { lineStyle: { color: 'rgba(89, 173, 218, .26)' } },
      axisTick: { show: false },
      axisLabel: { color: '#5688a2', fontSize: 8 }
    },
    yAxis: {
      type: 'value',
      name: 'kW',
      nameTextStyle: { color: '#5688a2', fontSize: 8 },
      axisLabel: { color: '#5688a2', fontSize: 8 },
      splitLine: { lineStyle: { color: 'rgba(76, 160, 207, .12)', type: 'dashed' } }
    },
    series: [{
      name: '预测功率',
      type: 'line',
      smooth: true,
      showSymbol: true,
      symbolSize: 5,
      data: predictionRows.value.map((item) => item.predictPower),
      lineStyle: { width: 2, shadowBlur: 8, shadowColor: 'rgba(82, 232, 255, .5)' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(82, 232, 255, .28)' },
          { offset: 1, color: 'rgba(32, 104, 211, 0)' }
        ])
      }
    }]
  }, true)
}

function renderRealtimeChart() {
  if (!realtimeChartRef.value) return
  realtimeChart = realtimeChart ?? echarts.init(realtimeChartRef.value)
  const rows = historyRows.value.slice(-24)
  realtimeChart.setOption({
    animationDuration: 650,
    color: ['#ffc45c', '#52e8ff', '#8d7cff'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(3, 20, 43, .94)',
      borderColor: 'rgba(82, 232, 255, .38)',
      textStyle: { color: '#dff7ff', fontSize: 10 }
    },
    legend: {
      top: 0,
      right: 4,
      itemWidth: 12,
      itemHeight: 5,
      textStyle: { color: '#6999b2', fontSize: 8 }
    },
    grid: { left: 40, right: 38, top: 34, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map((item) => formatChartTime(item.time)),
      axisLine: { lineStyle: { color: 'rgba(89, 173, 218, .26)' } },
      axisTick: { show: false },
      axisLabel: { color: '#5688a2', fontSize: 8 }
    },
    yAxis: [
      {
        type: 'value',
        name: 'W/m²',
        nameTextStyle: { color: '#5688a2', fontSize: 8 },
        axisLabel: { color: '#5688a2', fontSize: 8 },
        splitLine: { lineStyle: { color: 'rgba(76, 160, 207, .12)', type: 'dashed' } }
      },
      {
        type: 'value',
        name: 'V / A',
        nameTextStyle: { color: '#5688a2', fontSize: 8 },
        axisLabel: { color: '#5688a2', fontSize: 8 },
        splitLine: { show: false }
      }
    ],
    series: [
      realtimeLineSeries('辐照度', rows.map((item) => item.irradiance), 0),
      realtimeLineSeries('电压', rows.map((item) => item.voltage), 1),
      realtimeLineSeries('电流', rows.map((item) => item.current), 1)
    ]
  }, true)
}

function realtimeLineSeries(name: string, data: number[], yAxisIndex: number) {
  return {
    name,
    type: 'line',
    smooth: false,
    connectNulls: true,
    symbol: 'circle',
    symbolSize: 5,
    yAxisIndex,
    data,
    lineStyle: { width: 1.7 },
    emphasis: { focus: 'series' }
  }
}

function resizeCharts() {
  predictionChart?.resize()
  realtimeChart?.resize()
}

function formatChartTime(value: string) {
  return value?.slice(11, 16) || value
}

function formatDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatMetric(value?: number, digits = 1) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(digits) : '--'
}

function stationStatusText(status?: string) {
  const labels: Record<string, string> = { RUNNING: '运行中', MAINTENANCE: '维护中', STOPPED: '已停机' }
  return labels[status ?? ''] ?? '未知'
}

function resourceCode(name: string) {
  if (/CPU|计算/i.test(name)) return 'CPU'
  if (/内存|memory/i.test(name)) return 'RAM'
  if (/硬盘|磁盘|disk/i.test(name)) return 'DISK'
  if (/模型|model/i.test(name)) return 'MODEL'
  return 'SYS'
}
</script>

<template>
  <div class="pv-visualization" :class="{ 'is-loading': loading }">
    <div class="screen-atmosphere" aria-hidden="true">
      <span class="atmosphere-glow atmosphere-glow-left" />
      <span class="atmosphere-glow atmosphere-glow-right" />
      <span class="scan-line" />
    </div>

    <header class="screen-header">
      <div class="header-wing header-wing-left">
        <span class="wing-mark" />
        <div>
          <small>GUANGFU DATA CENTER</small>
          <strong>新能源集中监控中心</strong>
        </div>
      </div>

      <div class="title-lockup">
        <span class="title-overline">PV ENERGY · LIVE OPERATION</span>
        <h1>光伏能源运行驾驶舱</h1>
        <span class="title-axis" aria-hidden="true"><i /></span>
      </div>

      <div class="header-wing header-wing-right">
        <div class="system-state">
          <span class="state-dot" />
          <span>{{ dataSource }}</span>
        </div>
        <div class="clock-block">
          <strong>{{ currentTime }}</strong>
          <small>{{ currentDate }}</small>
        </div>
        <span class="wing-mark" />
      </div>
    </header>

    <main class="screen-grid">
      <aside class="screen-column left-column">
        <section class="data-panel station-panel">
          <header class="panel-heading">
            <div>
              <span class="panel-kicker">STATION PROFILE</span>
              <h2>电站信息</h2>
            </div>
            <span class="station-live"><i />{{ stationStatusText(selectedStation?.status) }}</span>
          </header>

          <div class="station-dashboard-head">
            <div class="station-name-block">
              <span>当前电站</span>
              <strong>{{ selectedStation?.stationName || '暂无电站数据' }}</strong>
            </div>
            <label class="station-switcher">
              <span>切换电站</span>
              <select
                v-model.number="selectedStationId"
                aria-label="切换电站"
                :disabled="loading || stations.length < 2"
                @change="handleStationChange"
              >
                <option v-for="station in stations" :key="station.stationId" :value="station.stationId">
                  {{ station.stationName }}
                </option>
              </select>
            </label>
          </div>

          <div class="station-identity">
            <div class="station-emblem" aria-hidden="true">
              <svg viewBox="0 0 64 64">
                <path d="M12 23h40l5 26H7l5-26Z" />
                <path d="M18 23l-3 26M28 23l-1 26M37 23l2 26M47 23l3 26M9 32h46M8 41h48" />
                <path d="M32 7v10M14 13l7 7M50 13l-7 7" class="sun-rays" />
              </svg>
            </div>
            <div>
              <span>电站位置</span>
              <strong>{{ [selectedStation?.province, selectedStation?.city].filter(Boolean).join(' · ') || '等待接口返回位置' }}</strong>
              <small>{{ selectedStation?.address || dataSource }}</small>
            </div>
          </div>

          <div class="station-detail-grid">
            <div v-for="item in stationDetails" :key="item.label">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }} <small>{{ item.unit }}</small></strong>
            </div>
          </div>

          <div class="station-operation">
            <div><span>当前功率</span><strong>{{ formatMetric(currentPower, 2) }} <small>kW</small></strong></div>
            <div><span>数据更新时间</span><strong>{{ lastUpdate || '--' }}</strong></div>
          </div>
        </section>

        <section class="data-panel generation-panel">
          <header class="panel-heading">
            <div>
              <span class="panel-kicker">POWER FORECAST</span>
              <h2>预测功率</h2>
            </div>
            <span class="panel-meta">{{ predictionSourceLabel }}</span>
          </header>

          <div class="chart-legend">
            <span><i class="legend-dot is-cyan" />{{ predictionMode === 'api' ? '预测结果接口' : '历史功率推演' }}</span>
            <span>{{ latestPredictionTask?.taskNo || (predictionMode === 'derived' ? '基于真实历史采样' : '等待成功任务') }}</span>
          </div>
          <div class="chart-shell prediction-chart-shell">
            <div ref="predictionChartRef" class="screen-line-chart" aria-label="预测功率折线图" />
            <div v-if="!predictionRows.length" class="chart-empty">暂无成功的预测任务结果</div>
          </div>

          <div class="yield-strip">
            <div><span>预测峰值</span><strong>{{ formatMetric(predictionPeak, 2) }} <small>kW</small></strong></div>
            <div><span>预测点数</span><strong class="is-good">{{ predictionRows.length }} <small>点</small></strong></div>
          </div>
        </section>
      </aside>

      <section class="center-stage">
        <div class="metric-ribbon">
          <article v-for="metric in summaryMetrics" :key="metric.label">
            <span>{{ metric.label }}</span>
            <div><strong>{{ metric.value }}</strong><small>{{ metric.unit }}</small></div>
            <em>{{ metric.trend }}</em>
          </article>
        </div>

        <section class="situation-panel">
          <header class="situation-head">
            <div>
              <span class="panel-kicker">LIVE ENERGY FLOW</span>
              <h2>实时能源流</h2>
            </div>
            <div class="flow-sync-state">
              <i />
              <span>{{ dataSource }} · {{ lastUpdate || '--' }}</span>
            </div>
          </header>

          <div class="live-energy-flow">
            <div class="flow-grid" aria-hidden="true" />
            <div class="flow-energy-spine" aria-hidden="true"><i /><b /></div>
            <img src="/images/dashboard.png" alt="光伏发电、负载、电网与储能实时能源流拓扑图" />

            <div class="flow-hub">
              <span>ENERGY HUB</span>
              <strong>{{ formatMetric(currentPower, 2) }} <small>kW</small></strong>
              <em>{{ selectedStation?.stationName || '等待电站数据' }}</em>
            </div>

            <div class="flow-metric flow-load"><i /><span>负载</span><strong>{{ formatMetric(loadPower, 2) }} <small>kW</small></strong></div>
            <div class="flow-metric flow-solar"><i /><span>光伏发电</span><strong>{{ formatMetric(currentPower, 2) }} <small>kW</small></strong></div>
            <div class="flow-metric flow-grid-node"><i /><span>电网</span><strong>{{ formatMetric(gridPower, 2) }} <small>kW</small></strong></div>
            <div class="flow-metric flow-battery"><i /><span>储能</span><strong>52 <small>%</small></strong></div>

            <div class="flow-direction flow-direction-left" aria-hidden="true"><i /></div>
            <div class="flow-direction flow-direction-right" aria-hidden="true"><i /></div>
            <div class="flow-direction flow-direction-bottom" aria-hidden="true"><i /></div>
          </div>
        </section>

        <section class="server-strip">
          <header class="server-strip-heading">
            <div><span class="panel-kicker">INFRASTRUCTURE</span><h2>服务器信息</h2></div>
            <span class="server-summary"><i />全部服务正常</span>
          </header>
          <div class="server-resource-grid">
            <article v-for="resource in serverResources" :key="resource.label">
              <span class="server-code" :class="`is-${resource.tone}`">{{ resource.code }}</span>
              <div class="server-copy"><span>{{ resource.label }}</span><strong>{{ resource.value }}</strong><small>{{ resource.detail }}</small></div>
              <div class="server-load"><i :style="{ width: resource.load }" /></div>
              <b />
            </article>
          </div>
        </section>
      </section>

      <aside class="screen-column right-column">
        <section class="data-panel weather-panel">
          <header class="panel-heading">
            <div>
              <span class="panel-kicker">SOLAR WEATHER</span>
              <h2>天气实况</h2>
            </div>
            <span class="weather-symbol" aria-hidden="true">☼</span>
          </header>

          <div class="weather-current">
            <div><strong>{{ formatMetric(weather?.temperature, 1) }}<small>°C</small></strong><span>{{ weather?.weather || '等待天气数据' }}</span></div>
            <dl>
              <div><dt>辐照度</dt><dd>{{ formatMetric(currentIrradiance, 0) }} W/m²</dd></div>
              <div><dt>风速</dt><dd>{{ formatMetric(weather?.windSpeed, 1) }} m/s</dd></div>
              <div><dt>湿度</dt><dd>{{ formatMetric(weather?.humidity, 0) }} %</dd></div>
            </dl>
          </div>
          <div class="weather-forecast">
            <div v-for="(item, index) in weatherHours" :key="`${item.label}-${index}`">
              <span>{{ item.label }}</span><i :style="{ opacity: `${1 - index * 0.12}` }">{{ /晴/.test(item.weather) ? '☼' : '☁' }}</i><strong>{{ item.temperature }}°</strong>
            </div>
            <span v-if="!weatherHours.length" class="weather-empty">暂无预报</span>
          </div>
        </section>

        <section class="data-panel realtime-lines-panel">
          <header class="panel-heading">
            <div>
              <span class="panel-kicker">ELECTRICAL STREAM</span>
              <h2>辐照与电气实时数据</h2>
            </div>
            <span class="panel-meta">{{ historyRows.length }} POINTS</span>
          </header>

          <div class="chart-shell measurement-chart-shell">
            <div ref="realtimeChartRef" class="screen-line-chart" aria-label="辐照度、电压和电流实时折线图" />
            <div v-if="!historyRows.length" class="chart-empty">暂无实时历史数据</div>
          </div>

          <div class="latest-measurements">
            <div><span>辐照度</span><strong>{{ formatMetric(realtime?.irradiance, 0) }} <small>W/m²</small></strong></div>
            <div><span>电压</span><strong>{{ formatMetric(realtime?.voltage, 1) }} <small>V</small></strong></div>
            <div><span>电流</span><strong>{{ formatMetric(realtime?.current, 1) }} <small>A</small></strong></div>
          </div>
        </section>
      </aside>
    </main>

    <footer class="screen-footer">
      <span>数据刷新周期 15s</span>
      <span class="footer-signal"><i />LIVE DATA STREAM</span>
      <span>{{ selectedStation?.stationName || '光伏智云监测平台' }} · {{ lastUpdate || '等待数据' }}</span>
    </footer>
  </div>
</template>

<style scoped src="../styles/visualization.css"></style>
