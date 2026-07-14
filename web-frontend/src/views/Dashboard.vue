<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Cloudy, Connection, Cpu, DataLine, Location, Monitor, Refresh, Sunny, Timer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardOverview, getStationDashboard } from '../api/dashboard'
import type { DashboardEnergyFlow, DashboardHistoryPoint, DashboardRealtime, StationDashboard } from '../api/dashboard'
import type { Station } from '../api/station'

interface ChartPoint {
  time: string
  fullTime: string
  pvPowerKw?: number | null
  loadPowerKw?: number | null
  gridPowerKw?: number | null
  predictionPowerKw?: number | null
  irradianceWm2?: number | null
}

const initialLoading = ref(false)
const refreshing = ref(false)
const stations = ref<Station[]>([])
const selectedStationId = ref<number>()
const dashboard = ref<StationDashboard | null>(null)
const errorMessage = ref('')
const chartRows = ref<ChartPoint[]>([])
const historyMode = ref<'real' | 'derived' | 'empty'>('empty')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let refreshTimer: number | undefined
let requestSeq = 0

const selectedStation = computed(() => dashboard.value?.station ?? stations.value.find((item) => item.stationId === selectedStationId.value))
const realtime = computed<DashboardRealtime | null>(() => dashboard.value?.realtime ?? null)
const energyFlow = computed<DashboardEnergyFlow | null>(() => dashboard.value?.energyFlow ?? null)
const forecasts = computed(() => dashboard.value?.weather.forecast?.slice(0, 3) ?? [])
const weatherText = computed(() => dashboard.value?.weather.text || '暂无天气')
const snapshotText = computed(() => formatHourDateTime(dashboard.value?.snapshotTime))
const stationLocation = computed(() => {
  if (!selectedStation.value) return '-'
  return [selectedStation.value.province, selectedStation.value.city, selectedStation.value.address]
    .filter(Boolean)
    .join(' / ')
})
const batteryText = computed(() => {
  if (!energyFlow.value) return '--'
  if (energyFlow.value.batterySoc == null) return '未配置储能'
  const power = formatSignedPower(energyFlow.value.batteryPowerKw)
  return `${formatNumber(energyFlow.value.batterySoc, 0)}% / ${power}`
})

const realtimeMetrics = computed(() => [
  { label: '实时功率', value: formatNumber(realtime.value?.pvPowerKw, 1), unit: 'kW' },
  { label: '电压', value: formatNumber(realtime.value?.voltageV, 1), unit: 'V' },
  { label: '电流', value: formatNumber(realtime.value?.currentA, 1), unit: 'A' },
  { label: '辐照度', value: formatNumber(realtime.value?.irradianceWm2, 0), unit: 'W/m²' },
  { label: '温度', value: formatNumber(realtime.value?.temperatureC, 1), unit: '°C' },
  { label: '湿度', value: formatNumber(realtime.value?.humidityPercent, 0), unit: '%' },
  { label: '风速', value: formatNumber(realtime.value?.windSpeedMs, 1), unit: 'm/s' }
])

const serverResources = computed(() => {
  const infra = dashboard.value?.infrastructure
  return [
    { label: '后端服务', value: statusText(infra?.backendStatus), detail: 'Spring Boot API', icon: Cpu, tone: 'blue' },
    { label: '数据库', value: statusText(infra?.databaseStatus), detail: 'MySQL 连接状态', icon: Monitor, tone: 'green' },
    { label: '模型服务', value: statusText(infra?.modelServiceStatus), detail: '健康检查结果', icon: Connection, tone: 'violet' },
    { label: '任务队列', value: infra?.queueStatus || '未接入监控', detail: '无真实队列指标时不展示负载', icon: DataLine, tone: 'orange' }
  ]
})

const weatherIcon = computed(() => /晴/.test(dashboard.value?.weather.text || '') ? Sunny : Cloudy)
const trendNote = computed(() => {
  if (historyMode.value === 'real') return '最近24小时'
  if (historyMode.value === 'derived') return '最近24小时'
  return '暂无历史发电数据'
})

onMounted(async () => {
  window.addEventListener('resize', resizeChart)
  await loadStations()
  startAutoRefresh()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  if (refreshTimer) window.clearInterval(refreshTimer)
  chart?.dispose()
})

async function loadStations() {
  initialLoading.value = true
  try {
    const overview = await getDashboardOverview()
    stations.value = overview.stations ?? []
    selectedStationId.value = overview.selectedStationId ?? stations.value[0]?.stationId
    if (selectedStationId.value) await loadDashboard(false)
    else clearDashboard()
  } catch (error) {
    clearDashboard()
    errorMessage.value = error instanceof Error ? error.message : '电站列表加载失败'
    ElMessage.error(errorMessage.value)
  } finally {
    initialLoading.value = false
  }
}

async function loadDashboard(showLoading = true, force = false) {
  const stationId = selectedStationId.value
  if (!stationId || (refreshing.value && !force)) return
  const seq = ++requestSeq
  refreshing.value = showLoading
  errorMessage.value = ''
  try {
    const data = await getStationDashboard(stationId)
    if (seq !== requestSeq || data.station.stationId !== selectedStationId.value) return
    dashboard.value = data
    upsertStation(data.station)
    const history = (data.history ?? []).filter(notFuture).map(mapHistoryPoint)
    chartRows.value = history.length ? history : createDerivedHistory(data)
    historyMode.value = history.length ? 'real' : chartRows.value.length ? 'derived' : 'empty'
    await nextTick()
    renderChart()
  } catch (error) {
    if (seq !== requestSeq) return
    dashboard.value = null
    chartRows.value = []
    historyMode.value = 'empty'
    resetChart()
    errorMessage.value = error instanceof Error ? error.message : '看板数据加载失败'
    if (showLoading) ElMessage.error(errorMessage.value)
  } finally {
    if (seq === requestSeq) refreshing.value = false
  }
}

async function handleStationChange() {
  requestSeq++
  dashboard.value = null
  chartRows.value = []
  historyMode.value = 'empty'
  resetChart()
  await nextTick()
  await loadDashboard(true, true)
}

async function manualRefresh() {
  await loadDashboard(true)
}

function startAutoRefresh() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    if (!refreshing.value) void loadDashboard(false)
  }, 30000)
}

function clearDashboard() {
  dashboard.value = null
  chartRows.value = []
  historyMode.value = 'empty'
  resetChart()
}

function upsertStation(station: Station) {
  const index = stations.value.findIndex((item) => item.stationId === station.stationId)
  if (index >= 0) stations.value[index] = station
  else stations.value.push(station)
}

function notFuture(item: DashboardHistoryPoint) {
  const time = new Date(item.time).getTime()
  return Number.isFinite(time) && time <= Date.now()
}

function mapHistoryPoint(item: DashboardHistoryPoint): ChartPoint {
  return {
    time: formatTimeLabel(item.time),
    fullTime: formatDateTime(item.time) || item.time,
    pvPowerKw: item.pvPowerKw,
    loadPowerKw: item.loadPowerKw,
    gridPowerKw: item.gridPowerKw,
    predictionPowerKw: item.predictionPowerKw,
    irradianceWm2: item.irradianceWm2
  }
}


function createDerivedHistory(data: StationDashboard): ChartPoint[] {
  const snapshot = new Date(data.snapshotTime)
  if (!Number.isFinite(snapshot.getTime())) return []
  const bucketMs = 60 * 60 * 1000
  const endMs = Math.floor(snapshot.getTime() / bucketMs) * bucketMs
  const startMs = endMs - 24 * 60 * 60 * 1000 + bucketMs
  const capacity = Math.max(1, data.station.capacity || data.realtime.pvPowerKw || 1)
  const stationSeed = data.station.stationId || 1
  const currentPv = data.realtime.pvPowerKw ?? 0
  const currentLoad = data.realtime.loadPowerKw ?? Math.max(0.2, capacity * 0.22)
  const currentIrradiance = data.realtime.irradianceWm2 ?? 0
  const weather = data.weather.text || ''
  const rows: ChartPoint[] = []

  for (let time = startMs; time <= endMs; time += bucketMs) {
    const date = new Date(time)
    const hour = date.getHours() + date.getMinutes() / 60
    const daylight = daylightRatio(hour)
    const weatherFactor = irradianceWeatherFactor(weather)
    const noise = 1 + smoothNoise(stationSeed, time / bucketMs, 'pv') * 0.035
    const irradianceBase = daylight <= 0 ? 0 : 980 * Math.pow(Math.sin(Math.PI * daylight), 1.3) * weatherFactor
    const irradiance = time === endMs
      ? currentIrradiance
      : clamp(irradianceBase * noise, 0, 1100)
    const tempFactor = clamp(1 - 0.004 * (((data.realtime.temperatureC ?? 25) + 0.03 * irradiance) - 25), 0.78, 1.05)
    const pvBase = capacity * irradiance / 1000 * 0.84 * tempFactor
    const pvPower = time === endMs ? currentPv : clamp(pvBase, 0, capacity)
    const loadShape = loadRatio(hour)
    const loadNoise = 1 + smoothNoise(stationSeed, time / bucketMs, 'load') * 0.025
    const loadPower = time === endMs ? currentLoad : Math.max(0.1, currentLoad * loadShape * loadNoise)
    const batteryPower = data.energyFlow.batterySoc == null ? 0 : clamp((pvPower - loadPower) * 0.35, -capacity * 0.18, capacity * 0.18)
    const loss = (pvPower + loadPower + Math.abs(batteryPower)) * 0.02
    const gridPower = loadPower + Math.max(0, batteryPower) + loss - pvPower - Math.max(0, -batteryPower)

    rows.push({
      time: formatTimeLabel(date.toISOString()),
      fullTime: formatHourDateTime(date.toISOString()),
      pvPowerKw: round(pvPower, 2),
      loadPowerKw: round(loadPower, 2),
      gridPowerKw: round(gridPower, 2),
      predictionPowerKw: null,
      irradianceWm2: round(irradiance, 0)
    })
  }

  return rows
}

function daylightRatio(hour: number) {
  const sunrise = 6
  const sunset = 18.8
  if (hour <= sunrise || hour >= sunset) return 0
  return (hour - sunrise) / (sunset - sunrise)
}

function irradianceWeatherFactor(text: string) {
  if (/暴雨|大雨|中雨/.test(text)) return 0.1
  if (/雨/.test(text)) return 0.2
  if (/阴/.test(text)) return 0.42
  if (/云/.test(text)) return 0.7
  return 0.92
}

function loadRatio(hour: number) {
  if (hour < 6) return 0.62
  if (hour < 10) return 0.75 + (hour - 6) * 0.1
  if (hour < 14) return 1.02
  if (hour < 18) return 1.12
  if (hour < 22) return 0.9
  return 0.65
}

function smoothNoise(stationId: number, bucket: number, metric: string) {
  const a = stableNoise(stationId, Math.floor(bucket), metric)
  const b = stableNoise(stationId, Math.floor(bucket) - 1, metric)
  return a * 0.8 + b * 0.2
}

function stableNoise(stationId: number, bucket: number, metric: string) {
  const seed = stationId * 1103515245 + bucket * 2654435761 + hashMetric(metric)
  const value = Math.sin(seed) * 43758.5453123
  return (value - Math.floor(value)) * 2 - 1
}

function hashMetric(metric: string) {
  return metric.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0)
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function round(value: number, digits: number) {
  const factor = 10 ** digits
  return Math.round(value * factor) / factor
}


function resetChart() {
  chart?.dispose()
  chart = null
}

function renderChart() {
  if (!chartRef.value) {
    resetChart()
    return
  }
  if (chart && chart.getDom() !== chartRef.value) resetChart()
  chart = chart ?? echarts.init(chartRef.value)
  chart.setOption({
    color: ['#1d6fdc', '#22a06b', '#f59e0b', '#7c3aed', '#06a6b8'],
    tooltip: {
      trigger: 'axis',
      formatter(params: any) {
        const list = Array.isArray(params) ? params : [params]
        const row = chartRows.value[list[0]?.dataIndex]
        const lines = [row?.fullTime || '']
        list.forEach((item: any) => {
          if (item.value == null) return
          const unit = item.seriesName.includes('辐照') ? ' W/m²' : ' kW'
          lines.push(`${item.marker}${item.seriesName}: ${item.value}${unit}`)
        })
        return lines.join('<br/>')
      }
    },
    legend: { top: 4, itemWidth: 10, itemHeight: 10, textStyle: { color: '#40516b' } },
    grid: { left: 42, right: 52, top: 58, bottom: 42, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: chartRows.value.map((item) => item.time), axisLine: { lineStyle: { color: '#d8e3f0' } }, axisLabel: { color: '#66758c' } },
    yAxis: [
      { type: 'value', name: '功率 kW', axisLabel: { color: '#66758c' }, splitLine: { lineStyle: { color: '#edf3f9' } } },
      { type: 'value', name: '辐照 W/m²', axisLabel: { color: '#66758c' }, splitLine: { show: false } }
    ],
    series: [
      lineSeries('光伏功率', 'pvPowerKw'),
      lineSeries('负载功率', 'loadPowerKw'),
      lineSeries('电网功率', 'gridPowerKw'),
      ...(chartRows.value.some((item) => item.predictionPowerKw != null) ? [lineSeries('预测功率', 'predictionPowerKw')] : []),
      lineSeries('辐照度', 'irradianceWm2', 1)
    ]
  }, true)
}

function lineSeries(name: string, key: keyof ChartPoint, yAxisIndex = 0) {
  return { name, type: 'line', smooth: true, connectNulls: false, symbol: 'circle', symbolSize: 5, yAxisIndex, data: chartRows.value.map((item) => item[key] ?? null) }
}

function resizeChart() { chart?.resize() }

function formatNumber(value?: number | null, digits = 1) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(digits) : '--'
}

function formatSignedPower(value?: number | null) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '-- kW'
  return `${value >= 0 ? '购电 ' : '上网 '}${Math.abs(value).toFixed(1)} kW`
}


function formatHourDateTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  date.setMinutes(0, 0, 0)
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date).replace(/\//g, '-')
}

function formatDateTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date).replace(/\//g, '-')
}

function formatTimeLabel(value: string) {
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

function statusText(value?: string) {
  if (value === 'UP') return '正常'
  if (value === 'UNAVAILABLE') return '未接入'
  return value || '--'
}

function stationTagType(status?: string) {
  if (status === 'RUNNING') return 'success'
  if (status === 'MAINTENANCE') return 'warning'
  if (status === 'STOPPED' || status === 'OFFLINE') return 'info'
  if (status === 'FAULT') return 'danger'
  return 'primary'
}

function stationStatusText(status?: string) {
  const map: Record<string, string> = { RUNNING: '运行中', MAINTENANCE: '维护中', STOPPED: '已停机', OFFLINE: '离线', FAULT: '异常' }
  return map[status ?? ''] ?? '未知'
}
</script>

<template>
  <section class="page-shell dashboard-page" v-loading="initialLoading">
    <section class="page-section station-header">
      <template v-if="selectedStation">
        <div class="station-title">
          <div class="station-name-row">
            <h2>{{ selectedStation.stationName }}</h2>
            <el-tag :type="stationTagType(selectedStation.status)" effect="light">
              {{ stationStatusText(selectedStation.status) }}
            </el-tag>
          </div>
        </div>

        <div class="station-switch">
          <span>切换电站</span>
          <el-select v-model="selectedStationId" class="station-select" size="large" @change="handleStationChange">
            <el-option
              v-for="station in stations"
              :key="station.stationId"
              :label="station.stationName"
              :value="station.stationId"
            />
          </el-select>
          <el-button :icon="Refresh" :loading="refreshing" size="large" type="primary" @click="manualRefresh">刷新</el-button>
        </div>
      </template>
      <el-empty v-else class="full-empty" description="暂无电站数据" />
    </section>

    <div class="dashboard-top-grid">
      <section class="page-section overview-card">
        <div class="overview-weather">
          <div><span>{{ weatherText }}</span><strong>{{ stationLocation || '暂无位置' }}</strong></div>
          <span class="sync-state">{{ snapshotText ? `更新于 ${snapshotText}` : '暂无同步时间' }}</span>
        </div>
        <div class="overview-power">
          <span>当前功率</span>
          <strong>{{ formatNumber(realtime?.pvPowerKw, 1) }}<small> kW</small></strong>
          <el-tag v-if="selectedStation" :type="stationTagType(selectedStation.status)" effect="light">{{ stationStatusText(selectedStation.status) }}</el-tag>
        </div>
        <div class="metric-strip">
          <div v-for="item in realtimeMetrics.slice(1, 7)" :key="item.label">
            <span>{{ item.label }}</span><strong>{{ item.value }} <small>{{ item.unit }}</small></strong>
          </div>
        </div>
      </section>

      <section class="page-section realtime-panel">
        <div class="panel-head">
          <div><p class="page-kicker">实时监控</p><h3>实时能源流</h3></div>
          <div class="collect-time">
            <el-icon><Timer /></el-icon>
            <span>{{ snapshotText || '暂无更新时间' }}</span>
          </div>
        </div>
        <div class="energy-flow">
          <img src="/images/dashboard.png" alt="光伏能源流拓扑图" />
          <span class="flow-label flow-load">负载<br><strong>{{ formatNumber(energyFlow?.loadPowerKw, 1) }} kW</strong></span>
          <span class="flow-label flow-solar">光伏发电<br><strong>{{ formatNumber(energyFlow?.pvPowerKw, 1) }} kW</strong></span>
          <span class="flow-label flow-grid">电网<br><strong>{{ formatSignedPower(energyFlow?.gridPowerKw) }}</strong></span>
          <span class="flow-label flow-battery">储能<br><strong>{{ batteryText }}</strong></span>
        </div>
      </section>

      <aside class="weather-card">
        <div class="weather-head">
          <h3>天气实况</h3>
          <div v-if="selectedStation" class="weather-location">
            <el-icon><Location /></el-icon>
            <span>{{ selectedStation.city || '--' }}</span>
          </div>
        </div>

        <template v-if="dashboard?.weather && dashboard.weather.dataSource !== 'UNAVAILABLE'">
          <div class="weather-list">
            <div class="weather-tile weather-condition"><span>天气</span><strong><el-icon><component :is="weatherIcon" /></el-icon>{{ dashboard.weather.text }}</strong></div>
            <div class="weather-tile"><span>温度</span><strong>{{ formatNumber(dashboard.weather.temperatureC, 1) }}°C</strong></div>
            <div class="weather-tile"><span>湿度</span><strong>{{ formatNumber(dashboard.weather.humidityPercent, 0) }}%</strong></div>
            <div class="weather-tile"><span>风速</span><strong>{{ dashboard.weather.windScale || `${formatNumber(dashboard.weather.windSpeedMs, 1)} m/s` }}</strong></div>
            <div class="weather-tile"><span>风向</span><strong>{{ dashboard.weather.windDirection || '--' }}</strong></div>
          </div>

          <div v-if="forecasts.length" class="forecast-mini">
            <div v-for="item in forecasts" :key="item.date">
              <span>{{ item.date.slice(5) }}</span>
              <strong>{{ item.dayWeather }}</strong>
              <em>{{ item.nightTemp }}-{{ item.dayTemp }}°C</em>
            </div>
          </div>

          <p class="weather-update">最近天气更新时间：{{ formatHourDateTime(dashboard.weather.updateTime) || '--' }}</p>
        </template>
        <el-empty v-else :description="errorMessage || '天气暂不可用'" />
      </aside>
    </div>

    <div class="dashboard-bottom-grid">
      <section class="page-section trend-panel">
        <div class="panel-head"><div><p class="page-kicker">历史数据</p><h3>功率与发电趋势</h3></div><span class="data-note">{{ trendNote }}</span></div>
        <div v-if="chartRows.length" ref="chartRef" class="realtime-chart" />
        <el-empty v-else description="暂无历史发电数据" />
      </section>
    </div>

    <section class="page-section server-panel">
      <div class="panel-head"><div><p class="page-kicker">基础设施</p><h3>服务器信息</h3></div><span class="data-note">最近检测：{{ formatHourDateTime(dashboard?.infrastructure.checkedAt) || '--' }}</span></div>
      <div class="server-grid">
        <div v-for="item in serverResources" :key="item.label" class="server-item">
          <span class="server-icon" :class="`is-${item.tone}`"><el-icon><component :is="item.icon" /></el-icon></span>
          <div><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.detail }}</small></div><i class="server-dot" />
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.dashboard-page { gap: 14px; background: #f3f5f7; }
.station-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 18px 20px; }
.station-title { min-width: 0; }
.station-name-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 4px; }
.station-name-row h2, .panel-head h3, .weather-head h3 { margin: 0; color: #172033; }
.station-name-row h2 { font-size: 22px; }
.station-switch { display: flex; align-items: center; gap: 9px; }
.station-switch span { color: #667085; font-size: 13px; white-space: nowrap; }
.station-select { width: 220px; }
.dashboard-top-grid { display: grid; grid-template-columns: minmax(260px, 4fr) minmax(430px, 5fr) minmax(230px, 3fr); gap: 14px; align-items: stretch; }
.overview-card { padding: 18px; }
.overview-weather, .overview-power { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.overview-weather span, .sync-state { color: #667085; font-size: 12px; }
.overview-weather strong { display: block; margin-top: 7px; color: #172033; font-size: 15px; }
.sync-state { text-align: right; }
.overview-power { align-items: center; margin: 42px 0 34px; }
.overview-power > span { color: #667085; font-size: 13px; }
.overview-power strong { margin-left: auto; color: #172033; font-size: 34px; font-weight: 600; }
.overview-power small, .metric-strip small, .station-detail-card small { color: #98a2b3; font-size: 12px; font-weight: 500; }
.metric-strip { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border-top: 1px solid #e5e7eb; }
.metric-strip > div { min-width: 0; padding: 14px 10px 0 0; }
.metric-strip > div + div { padding-left: 12px; border-left: 1px solid #e5e7eb; }
.metric-strip span { display: block; color: #98a2b3; font-size: 12px; }
.metric-strip strong { display: block; margin-top: 6px; color: #344054; font-size: 17px; }
.page-section, .weather-card { border: 1px solid #e5e7eb; border-radius: 14px; background: #fff; box-shadow: 0 2px 8px rgba(16, 24, 40, .025); }
.realtime-panel, .trend-panel { min-width: 0; padding: 16px 18px; }
.panel-head, .weather-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.panel-head h3, .weather-head h3 { font-size: 17px; }
.collect-time, .weather-location { display: inline-flex; align-items: center; gap: 6px; color: #98a2b3; font-size: 12px; white-space: nowrap; }
.energy-flow { position: relative; min-height: 292px; overflow: hidden; display: flex; justify-content: center; align-items: center; background: #fff; }
.energy-flow img { width: 100%; height: 292px; object-fit: contain; object-position: center; }
.flow-label { position: absolute; min-width: 82px; padding: 6px 8px; border: 1px solid #e5e7eb; border-radius: 8px; background: rgba(255,255,255,.94); color: #667085; font-size: 11px; line-height: 1.25; }
.flow-label strong { color: #172033; font-size: 13px; font-weight: 600; }
.flow-load { left: 3%; top: 51%; }.flow-solar { left: 20%; bottom: 5%; }.flow-grid { right: 3%; top: 51%; }.flow-battery { right: 17%; bottom: 5%; }
.weather-card { min-height: 100%; padding: 16px; color: #172033; }
.weather-head { align-items: center; }.weather-location { color: #667085; font-weight: 500; }
.weather-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }.weather-tile { min-height: 58px; padding: 10px; border: 1px solid #eef0f3; border-radius: 8px; background: #fafafa; }.weather-tile span { display: block; color: #98a2b3; font-size: 12px; }.weather-tile strong { display: block; margin-top: 5px; color: #344054; font-size: 16px; }
.weather-condition strong { display: flex; align-items: center; gap: 6px; }.weather-condition .el-icon { color: #f5b83d; font-size: 19px; }
.forecast-mini { display: grid; gap: 6px; margin-top: 10px; }.forecast-mini div { display: grid; grid-template-columns: 42px 1fr auto; align-items: center; gap: 6px; min-height: 30px; border-bottom: 1px solid #eef0f3; color: #667085; font-size: 11px; }.forecast-mini em { font-style: normal; color: #98a2b3; }.weather-update { margin: 10px 0 0; color: #98a2b3; font-size: 11px; line-height: 1.4; }
.dashboard-bottom-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 14px; align-items: stretch; }.trend-panel { min-height: 360px; }.realtime-chart { width: 100%; height: 300px; }.data-note { color: #98a2b3; font-size: 12px; }.server-panel { padding: 16px 18px; }.server-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }.server-item { position: relative; display: flex; align-items: center; gap: 11px; min-width: 0; padding: 12px; border: 1px solid #eef0f3; border-radius: 10px; background: #fafafa; }.server-icon { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; flex: 0 0 34px; border-radius: 9px; }.server-icon .el-icon { font-size: 18px; }.server-icon.is-blue { color: #3478c8; background: #eaf3ff; }.server-icon.is-green { color: #2d9b6b; background: #eaf8f1; }.server-icon.is-violet { color: #7b61b7; background: #f2edff; }.server-icon.is-orange { color: #c7862d; background: #fff4df; }.server-item > div { display: grid; min-width: 0; gap: 3px; }.server-item > div > span { color: #667085; font-size: 12px; }.server-item strong { color: #172033; font-size: 15px; font-weight: 600; }.server-item small { overflow: hidden; color: #98a2b3; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.server-dot { width: 6px; height: 6px; flex: 0 0 6px; margin-left: auto; border-radius: 50%; background: #43b581; }
.full-empty { grid-column: 1 / -1; }
@media (max-width: 1280px) { .dashboard-top-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.realtime-panel { grid-column: 1 / -1; order: -1; }.station-header { align-items: flex-start; flex-direction: column; } }
@media (max-width: 980px) { .dashboard-top-grid, .dashboard-bottom-grid { grid-template-columns: 1fr; }.realtime-panel { grid-column: auto; order: initial; }.station-detail-card { min-height: 0; } }
@media (max-width: 720px) { .station-switch { align-items: stretch; flex-direction: column; width: 100%; }.station-select { width: 100%; }.overview-power { margin: 28px 0; }.metric-strip { grid-template-columns: repeat(3, minmax(0, 1fr)); row-gap: 12px; }.energy-flow, .energy-flow img { min-height: 240px; height: 240px; }.flow-label { transform: scale(.9); }.realtime-chart { height: 270px; }.server-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (prefers-reduced-motion: reduce) { .energy-flow * { animation: none !important; transition: none !important; } }
</style>
