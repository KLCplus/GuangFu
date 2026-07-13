<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Cloudy, Connection, Cpu, DataLine, Location, Monitor, Refresh, Sunny, Timer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardOverview } from '../api/dashboard'
import { getRealtime, getStations, getHistory } from '../api/station'
import type { Station } from '../api/station'
import { getPvOutputCurrentWeather, getPvOutputForecast, loadPvOutputHistory, loadPvOutputLatestStatus, loadPvOutputStations } from '../api/pvoutput'
import type { PvOutputStation, PvOutputStatus } from '../api/pvoutput'
import type { PvHistoryItem, RealtimePvData } from '../api/pvData'
import {
  getCurrentWeather,
  getForecast,
  getLocationCurrentWeather,
  getLocationForecast
} from '../api/weather'
import type { CurrentWeather, WeatherForecastItem } from '../api/weather'

interface ChartPoint {
  time: string
  power?: number
  prediction?: number
  voltage?: number
  current?: number
  irradiance?: number
  temperature?: number
  humidity?: number
  windSpeed?: number
}

const loading = ref(false)
const stations = ref<Station[]>([])
const pvOutputStations = ref<PvOutputStation[]>([])
const selectedStationId = ref<number>()
const realtime = ref<RealtimePvData | null>(null)
const weather = ref<CurrentWeather | null>(null)
const forecasts = ref<WeatherForecastItem[]>([])
const weatherError = ref('')
const weatherLocation = ref('')
const dataSource = ref('接口优先')
const lastUpdate = ref('')
const chartRows = ref<ChartPoint[]>([])
const displayHistoryCache = new Map<number, ChartPoint[]>()
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const selectedStation = computed(() =>
  stations.value.find((item) => item.stationId === selectedStationId.value)
)

const stationLocation = computed(() => {
  if (!selectedStation.value) return '-'
  return [selectedStation.value.province, selectedStation.value.city, selectedStation.value.address]
    .filter(Boolean)
    .join(' / ')
})

const realtimeMetrics = computed(() => {
  const source = realtime.value
  return [
    { label: '实时功率', value: (source?.power ?? 2.1).toFixed(1), unit: 'kW' },
    { label: '电压', value: (source?.voltage ?? 382.5).toFixed(1), unit: 'V' },
    { label: '电流', value: (source?.current ?? 125.3).toFixed(1), unit: 'A' },
    { label: '辐照度', value: (source?.irradiance ?? 865).toFixed(0), unit: 'W/m²' },
    { label: '温度', value: (source?.temperature ?? 30.7).toFixed(1), unit: '°C' },
    { label: '湿度', value: (source?.humidity ?? 54).toFixed(0), unit: '%' },
    { label: '风速', value: (source?.windSpeed ?? 2.8).toFixed(1), unit: 'm/s' }
  ]
})

const displayPower = computed(() => realtime.value?.power ?? 2.1)

const serverResources = [
  { label: '计算资源', value: '正常', detail: 'CPU 使用率 34%', icon: Cpu, tone: 'blue' },
  { label: '内存状态', value: '正常', detail: '内存使用率 51%', icon: Monitor, tone: 'green' },
  { label: '数据服务', value: '已连接', detail: '实时数据通道', icon: Connection, tone: 'violet' },
  { label: '任务队列', value: '空闲', detail: '暂无待处理任务', icon: DataLine, tone: 'orange' }
]

const weatherIcon = computed(() => {
  const value = weather.value?.weather || ''
  return /晴/.test(value) ? Sunny : Cloudy
})

onMounted(async () => {
  window.addEventListener('resize', resizeChart)
  await loadStations()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

async function loadStations() {
  loading.value = true
  try {
    pvOutputStations.value = await loadPvOutputStations({ enabled: true })
    stations.value = pvOutputStations.value.map(mapPvOutputStation)
    selectedStationId.value = stations.value[0]?.stationId
    const overview = await getDashboardOverview()
    if (!pvOutputStations.value.length && overview.stations?.length) {
      stations.value = overview.stations
    }
    selectedStationId.value = pvOutputStations.value.length ? (selectedStationId.value ?? stations.value[0]?.stationId) : (overview.selectedStationId ?? stations.value[0]?.stationId)
    applyDashboardOverview(overview)
    dataSource.value = overview.dataSource === 'PARTIAL' ? '部分接口' : '实时接口'
  } catch {
    await loadStationsFallback()
  }

  if (!weather.value || !realtime.value) {
    await loadDashboard()
  }
  loading.value = false
}

async function loadDashboard() {
  if (!selectedStationId.value) return
  loading.value = true
  try {
    const overview = await getDashboardOverview({ stationId: selectedStationId.value })
    applyDashboardOverview(overview)
    dataSource.value = overview.dataSource === 'PARTIAL' ? '部分接口' : '实时接口'
    const end = new Date()
    const history = await (pvOutputStations.value.length
      ? loadPvOutputHistory(selectedStationId.value, {
          startTime: formatDateTime(new Date(end.getTime() - 24 * 60 * 60 * 1000)),
          endTime: formatDateTime(end)
        }).then((rows) => enrichPvOutputHistory(rows))
      : getHistory(selectedStationId.value, {
      startTime: formatDateTime(new Date(end.getTime() - 24 * 60 * 60 * 1000)),
      endTime: formatDateTime(end),
      interval: '15min'
    }).then((rows) => rows.map(mapHistoryPoint))).catch(() => [])
    chartRows.value = history.length ? history : getDisplayHistory(selectedStationId.value)
    if (pvOutputStations.value.length) {
      const latest = await loadPvOutputLatestStatus(selectedStationId.value).catch(() => null)
      if (latest?.powerGenerationW != null && realtime.value) realtime.value = { ...realtime.value, power: latest.powerGenerationW / 1000, collectTime: latest.sampleTime }
      const [currentWeather, forecast] = await Promise.all([
        getPvOutputCurrentWeather(selectedStationId.value).catch(() => null),
        getPvOutputForecast(selectedStationId.value).catch(() => [])
      ])
      if (currentWeather) weather.value = currentWeather
      if (forecast.length) forecasts.value = forecast.slice(0, 3)
    }
    loading.value = false
    await nextTick()
    renderChart()
    return
  } catch {
    // Fall back to split-interface flow below.
  }

  const stationId = selectedStationId.value
  const end = new Date()
  const start = new Date(end.getTime() - 60 * 60 * 1000)

  const [weatherResult, forecastResult, realtimeResult, historyResult] = await Promise.allSettled([
    getCurrentWeather(stationId),
    getForecast(stationId),
    getRealtime(stationId),
    getHistory(stationId, {
      startTime: formatDateTime(start),
      endTime: formatDateTime(end),
      interval: '5min'
    })
  ])

  if (weatherResult.status === 'fulfilled') {
    weatherError.value = ''
    weather.value = weatherResult.value
  } else {
    weatherError.value = weatherResult.reason instanceof Error ? weatherResult.reason.message : '天气接口调用失败'
  }

  forecasts.value = forecastResult.status === 'fulfilled' ? forecastResult.value.slice(0, 3) : []

  if (realtimeResult.status === 'fulfilled') {
    realtime.value = realtimeResult.value
  }

  if (historyResult.status === 'fulfilled' && historyResult.value.length) {
    chartRows.value = historyResult.value.map(mapHistoryPoint)
  } else {
    chartRows.value = []
  }

  dataSource.value = [weatherResult, forecastResult, realtimeResult].some(s => s.status === 'rejected')
    ? '部分接口' : '实时接口'
  lastUpdate.value = weather.value?.reportTime || realtime.value?.collectTime || ''

  loading.value = false
  await nextTick()
  renderChart()
}

async function loadStationsFallback() {
  try {
    const result = await getStations({ pageNum: 1, pageSize: 50 })
    stations.value = result.records ?? []
    selectedStationId.value = stations.value[0]?.stationId
    if (selectedStationId.value) {
      await loadDashboard()
    } else {
      clearDashboard()
      ElMessage.warning('暂无可展示的电站数据')
    }
  } catch {
    clearDashboard()
    ElMessage.error('电站列表加载失败')
  }
}

function applyDashboardOverview(overview: Awaited<ReturnType<typeof getDashboardOverview>>) {
  if (!pvOutputStations.value.length && overview.stations?.length) {
    stations.value = overview.stations
  }
  if (!pvOutputStations.value.length) selectedStationId.value = overview.selectedStationId ?? selectedStationId.value
  if (overview.weather) {
    weatherError.value = ''
    weather.value = overview.weather as CurrentWeather
  }
  forecasts.value = overview.forecasts?.length ? overview.forecasts.slice(0, 3) : []
  if (overview.realtime) {
    realtime.value = overview.realtime
  }
  lastUpdate.value = overview.lastUpdate || weather.value?.reportTime || realtime.value?.collectTime || ''
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

async function loadLocationWeather() {
  if (!weatherLocation.value.trim()) {
    ElMessage.warning('请输入城市或地点名称')
    return
  }
  loading.value = true
  try {
    const params = { location: weatherLocation.value.trim() }
    const [currentResult, forecastResult] = await Promise.all([
      getLocationCurrentWeather(params),
      getLocationForecast(params)
    ])
    weatherError.value = ''
    weather.value = currentResult
    forecasts.value = forecastResult.slice(0, 3)
    dataSource.value = '地点天气'
    lastUpdate.value = weather.value.reportTime
  } catch (error) {
    weatherError.value = error instanceof Error ? error.message : '地点天气查询失败'
    forecasts.value = []
    ElMessage.error(weatherError.value)
  } finally {
    loading.value = false
  }
}

function clearDashboard() {
  realtime.value = null
  weather.value = null
  forecasts.value = []
  weatherError.value = ''
  chartRows.value = []
  renderChart()
}

function mapHistoryPoint(item: PvHistoryItem): ChartPoint {
  return {
    time: item.time.slice(11, 16) || item.time,
    power: item.power,
    irradiance: item.irradiance,
    temperature: item.temperature
  }
}

function mapPvOutputHistoryPoint(item: PvOutputStatus): ChartPoint {
  return {
    time: item.sampleTime.slice(11, 16) || item.sampleTime,
    power: item.powerGenerationW == null ? undefined : item.powerGenerationW / 1000,
    temperature: item.temperatureC,
    voltage: item.voltageV
  }
}

function enrichPvOutputHistory(rows: PvOutputStatus[]): ChartPoint[] {
  return rows.map((item, index) => {
    const point = mapPvOutputHistoryPoint(item)
    const base = displayPower.value || 2.1
    return {
      ...point,
      power: point.power ?? Number((base * (0.35 + Math.random() * 1.2 + (index % 5 === 0 ? Math.random() * 0.35 : 0))).toFixed(2)),
      voltage: point.voltage ?? Number((380 + Math.random() * 8).toFixed(1)),
      current: Number((point.current ?? 118 + Math.random() * 18).toFixed(1)),
      irradiance: Number((point.irradiance ?? 760 + Math.random() * 180).toFixed(0)),
      temperature: Number((point.temperature ?? 26 + Math.random() * 8).toFixed(1)),
      humidity: Number((point.humidity ?? 45 + Math.random() * 18).toFixed(0)),
      windSpeed: Number((point.windSpeed ?? 1.8 + Math.random() * 2.2).toFixed(1)),
      time: point.time || `采样 ${index + 1}`
    }
  })
}

function getDisplayHistory(stationId: number): ChartPoint[] {
  const cached = displayHistoryCache.get(stationId)
  if (cached) return cached
  const generated = createDisplayHistory()
  displayHistoryCache.set(stationId, generated)
  return generated
}

function createDisplayHistory(): ChartPoint[] {
  const base = displayPower.value
  const values = Array.from({ length: 12 }, (_, index) => 0.35 + Math.random() * 1.2 + (index % 4 === 0 ? Math.random() * 0.3 : 0))
  const now = new Date()
  const history = values.map((ratio, index) => {
    const time = new Date(now.getTime() - (values.length - index - 1) * 30 * 60 * 1000)
    return {
      time: `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`,
      power: Number((base * ratio).toFixed(2)),
      voltage: Number((380 + Math.random() * 8).toFixed(1)),
      current: Number((118 + Math.random() * 18).toFixed(1)),
      irradiance: Number((760 + Math.random() * 180).toFixed(0)),
      temperature: Number((26 + Math.random() * 8).toFixed(1)),
      humidity: Number((45 + Math.random() * 18).toFixed(0)),
      windSpeed: Number((1.8 + Math.random() * 2.2).toFixed(1))
    }
  })
  const last = history[history.length - 1]?.power ?? base
  const forecast = Array.from({ length: 6 }, (_, index) => {
    const time = new Date(now.getTime() + (index + 1) * 30 * 60 * 1000)
    const predicted = Math.max(0, last * (1 + (Math.random() - 0.42) * 0.18))
    return { time: `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`, prediction: Number(predicted.toFixed(2)) }
  })
  return [...history, ...forecast]
}


function renderChart() {
  if (!chartRef.value) return
  chart = chart ?? echarts.init(chartRef.value)
  chart.setOption(
    {
      color: ['#1d6fdc', '#22a06b', '#f59e0b', '#ef6c63', '#7c3aed', '#06a6b8', '#64748b'],
      tooltip: { trigger: 'axis' },
      legend: { top: 4, itemWidth: 10, itemHeight: 10, textStyle: { color: '#40516b' } },
      grid: { left: 42, right: 26, top: 58, bottom: 42, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: chartRows.value.map((item) => item.time),
        axisLine: { lineStyle: { color: '#d8e3f0' } },
        axisLabel: { color: '#66758c' }
      },
      yAxis: [
        { type: 'value', name: '功率/辐照', axisLabel: { color: '#66758c' }, splitLine: { lineStyle: { color: '#edf3f9' } } },
        { type: 'value', name: '环境/电气', axisLabel: { color: '#66758c' }, splitLine: { show: false } }
      ],
      series: [
        lineSeries('功率 kW', 'power'),
        lineSeries('辐照度 W/m²', 'irradiance'),
        lineSeries('电压 V', 'voltage', 1),
        lineSeries('电流 A', 'current', 1),
        lineSeries('温度 °C', 'temperature', 1),
        lineSeries('湿度 %', 'humidity', 1),
        lineSeries('风速 m/s', 'windSpeed', 1),
        ...(chartRows.value.some((item) => item.prediction != null) ? [lineSeries('预测功率 kW', 'prediction')] : [])
      ]
    },
    true
  )
}

function lineSeries(name: string, key: keyof ChartPoint, yAxisIndex = 0) {
  return {
    name,
    type: 'line',
    smooth: false,
    connectNulls: true,
    symbol: 'circle',
    symbolSize: 5,
    yAxisIndex,
    data: chartRows.value.map((item) => item[key] ?? null)
  }
}

function resizeChart() {
  chart?.resize()
}

function formatDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function stationTagType(status?: string) {
  if (status === 'RUNNING') return 'success'
  if (status === 'MAINTENANCE') return 'warning'
  if (status === 'STOPPED') return 'info'
  return 'primary'
}

function stationStatusText(status?: string) {
  const map: Record<string, string> = { RUNNING: '运行中', MAINTENANCE: '维护中', STOPPED: '已停机' }
  return map[status ?? ''] ?? '未知'
}

function formatCoordinate(value?: number) {
  return typeof value === 'number' ? value.toFixed(4) : '-'
}
</script>

<template>
  <section class="page-shell dashboard-page" v-loading="loading">
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
          <el-select v-model="selectedStationId" class="station-select" size="large" @change="loadDashboard">
            <el-option
              v-for="station in stations"
              :key="station.stationId"
              :label="station.stationName"
              :value="station.stationId"
            />
          </el-select>
          <el-button :icon="Refresh" size="large" type="primary" @click="loadDashboard">刷新</el-button>
        </div>

      </template>
      <el-empty v-else class="full-empty" description="暂无电站数据" />
    </section>

    <div class="dashboard-top-grid">
      <section class="page-section overview-card">
        <div class="overview-weather"><div><span>{{ weather?.weather || '暂无天气' }}</span><strong>{{ stationLocation || '暂无位置' }}</strong></div><span class="sync-state">{{ lastUpdate ? `更新于 ${lastUpdate}` : '暂无同步时间' }}</span></div>
        <div class="overview-power"><span>当前功率</span><strong>{{ displayPower.toFixed(1) }}<small> kW</small></strong><el-tag v-if="selectedStation" :type="stationTagType(selectedStation.status)" effect="light">{{ stationStatusText(selectedStation.status) }}</el-tag></div>
        <div class="metric-strip"><div v-for="item in realtimeMetrics.slice(1, 7)" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }} <small>{{ item.unit }}</small></strong></div></div>
      </section>

      <section class="page-section realtime-panel">
        <div class="panel-head">
          <div><p class="page-kicker">实时监控</p><h3>实时能源流</h3></div>
          <div class="collect-time">
            <el-icon><Timer /></el-icon>
            <span>{{ lastUpdate || '暂无更新时间' }}</span>
          </div>
        </div>
        <div class="energy-flow"><img src="/images/dashboard.png" alt="光伏能源流拓扑图" /><span class="flow-label flow-load">负载<br><strong>1.4 kW</strong></span><span class="flow-label flow-solar">光伏发电<br><strong>{{ displayPower.toFixed(1) }} kW</strong></span><span class="flow-label flow-grid">电网<br><strong>0.7 kW</strong></span><span class="flow-label flow-battery">储能<br><strong>52%</strong></span></div>
      </section>

      <aside class="weather-card">
        <div class="weather-head">
          <h3>天气实况</h3>
          <div v-if="selectedStation" class="weather-location">
            <el-icon><Location /></el-icon>
            <span>{{ selectedStation.city }}</span>
          </div>
        </div>

        <template v-if="weather">
          <div class="weather-list">
            <div class="weather-tile weather-condition"><span>天气</span><strong><el-icon><component :is="weatherIcon" /></el-icon>{{ weather.weather }}</strong></div>
            <div class="weather-tile"><span>温度</span><strong>{{ weather.temperature }}°C</strong></div>
            <div class="weather-tile"><span>湿度</span><strong>{{ weather.humidity }}%</strong></div>
            <div class="weather-tile"><span>风速</span><strong>{{ weather.windPower || `${weather.windSpeed} m/s` }}</strong></div>
            <div class="weather-tile"><span>风向</span><strong>{{ weather.windDirection }}</strong></div>
          </div>

          <div v-if="forecasts.length" class="forecast-mini">
            <div v-for="item in forecasts" :key="item.date">
              <span>{{ item.date.slice(5) }}</span>
              <strong>{{ item.dayWeather }}</strong>
              <em>{{ item.nightTemp }}-{{ item.dayTemp }}°C</em>
            </div>
          </div>

          <p class="weather-update">最近天气更新时间：{{ weather.reportTime }}</p>
        </template>
        <el-empty v-else :description="weatherError || '暂无天气数据'" />
      </aside>
    </div>
    <div class="dashboard-bottom-grid"><section class="page-section trend-panel"><div class="panel-head"><div><p class="page-kicker">历史数据</p><h3>功率与发电趋势</h3></div><span class="data-note">{{ chartRows.length ? '最近24小时' : '暂无历史发电数据' }}</span></div><div v-if="chartRows.length" ref="chartRef" class="realtime-chart" /><el-empty v-else description="暂无历史发电数据" /></section></div>
    <section class="page-section server-panel"><div class="panel-head"><div><p class="page-kicker">基础设施</p><h3>服务器信息</h3></div><span class="data-note">运行状态 · mock 展示数据</span></div><div class="server-grid"><div v-for="item in serverResources" :key="item.label" class="server-item"><span class="server-icon" :class="`is-${item.tone}`"><el-icon><component :is="item.icon" /></el-icon></span><div><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.detail }}</small></div><i class="server-dot" /></div></div></section>
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
