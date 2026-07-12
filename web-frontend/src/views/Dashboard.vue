<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Location, Refresh, Timer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardOverview } from '../api/dashboard'
import {
  getPvOutputCurrentWeather,
  getPvOutputForecast,
  loadPvOutputHistory,
  loadPvOutputLatestStatus,
  loadPvOutputStations
} from '../api/pvoutput'
import type { PvOutputStation, PvOutputStatus } from '../api/pvoutput'
import { getRealtime, getStations, getHistory } from '../api/station'
import type { Station } from '../api/station'
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
  consumptionPower?: number
  voltage?: number
  current?: number
  irradiance?: number
  temperature?: number
  humidity?: number
  windSpeed?: number
}

interface DashboardStation extends Omit<Station, 'province' | 'city' | 'address' | 'longitude' | 'latitude'> {
  province?: string
  city?: string
  address?: string
  longitude?: number
  latitude?: number
}

const loading = ref(false)
const stations = ref<DashboardStation[]>([])
const pvOutputStations = ref<PvOutputStation[]>([])
const selectedStationId = ref<number>()
const realtime = ref<RealtimePvData | null>(null)
const pvOutputStatus = ref<PvOutputStatus | null>(null)
const isPvOutputMode = ref(false)
const weather = ref<CurrentWeather | null>(null)
const forecasts = ref<WeatherForecastItem[]>([])
const weatherError = ref('')
const weatherLocation = ref('')
const dataSource = ref('接口优先')
const lastUpdate = ref('')
const chartRows = ref<ChartPoint[]>([])
const chartRefs = new Map<string, HTMLDivElement>()
const charts = new Map<string, echarts.ECharts>()
let realtimeTimer: ReturnType<typeof setInterval> | undefined
let realtimeRequesting = false
const REALTIME_INTERVAL_MS = 10_000

const chartPanels = computed(() => {
  const panels = [
    {
      key: 'generation',
      eyebrow: '发电表现',
      title: '功率与辐照度',
      description: '判断光照变化与发电输出是否同步',
      series: [
        { name: '功率', unit: 'kW', field: 'power' as const, color: '#1677ff', axis: 0 },
        { name: '辐照度', unit: 'W/m²', field: 'irradiance' as const, color: '#f5a623', axis: 1 }
      ]
    },
    {
      key: 'electrical',
      eyebrow: '电气状态',
      title: '电压与电流',
      description: '观察逆变侧输出稳定性与异常波动',
      series: [
        { name: '电压', unit: 'V', field: 'voltage' as const, color: '#7357d9', axis: 0 },
        { name: '电流', unit: 'A', field: 'current' as const, color: '#e35d6a', axis: 1 }
      ]
    },
    {
      key: 'environment',
      eyebrow: '环境影响',
      title: '温湿度与风速',
      description: '辅助识别组件温升与现场散热条件',
      series: [
        { name: '温度', unit: '°C', field: 'temperature' as const, color: '#e76f3c', axis: 0 },
        { name: '湿度', unit: '%', field: 'humidity' as const, color: '#20a6a1', axis: 1 },
        { name: '风速', unit: 'm/s', field: 'windSpeed' as const, color: '#5b83c5', axis: 2 }
      ]
    }
  ]
  return isPvOutputMode.value
    ? panels.filter((panel) => panel.key !== 'environment' || panel.series.some((series) => hasChartData(series.field)))
    : panels
})

const selectedStation = computed(() =>
  stations.value.find((item) => item.stationId === selectedStationId.value)
)

const selectedPvOutputStation = computed(() =>
  pvOutputStations.value.find((item) => item.id === selectedStationId.value)
)

const stationLocation = computed(() => {
  if (!selectedStation.value) return '-'
  return [selectedStation.value.province, selectedStation.value.city, selectedStation.value.address]
    .filter(Boolean)
    .join(' / ')
})

const realtimeMetrics = computed(() => {
  if (isPvOutputMode.value && pvOutputStatus.value) {
    const status = pvOutputStatus.value
    return [
      { label: '发电功率', value: formatNumber((status.powerGenerationW ?? 0) / 1000, 2), unit: 'kW' },
      { label: '当日发电量', value: formatNumber((status.energyGenerationWh ?? 0) / 1000, 2), unit: 'kWh' },
      { label: '用电功率', value: formatNumber((status.powerConsumptionW ?? 0) / 1000, 2), unit: 'kW' },
      { label: '当日用电量', value: formatNumber((status.energyConsumptionWh ?? 0) / 1000, 2), unit: 'kWh' },
      { label: '电压', value: formatOptionalNumber(status.voltageV, 1), unit: 'V' },
      { label: '温度', value: formatOptionalNumber(status.temperatureC, 1), unit: '°C' },
      { label: '归一化输出', value: formatOptionalNumber(status.normalisedOutput, 2), unit: '' }
    ]
  }
  if (!realtime.value) return []
  return [
    { label: '实时功率', value: realtime.value.power.toFixed(1), unit: 'kW' },
    { label: '电压', value: realtime.value.voltage.toFixed(1), unit: 'V' },
    { label: '电流', value: realtime.value.current.toFixed(1), unit: 'A' },
    { label: '辐照度', value: realtime.value.irradiance.toFixed(0), unit: 'W/m²' },
    { label: '温度', value: realtime.value.temperature.toFixed(1), unit: '°C' },
    { label: '湿度', value: realtime.value.humidity.toFixed(0), unit: '%' },
    { label: '风速', value: realtime.value.windSpeed.toFixed(1), unit: 'm/s' }
  ]
})

onMounted(async () => {
  window.addEventListener('resize', resizeChart)
  await loadStations()
  startRealtimePolling()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  charts.forEach((item) => item.dispose())
  charts.clear()
  chartRefs.clear()
  if (realtimeTimer) clearInterval(realtimeTimer)
})

async function loadStations() {
  loading.value = true
  try {
    const overview = await getDashboardOverview()
    if (overview.stations?.length) {
      isPvOutputMode.value = false
      stations.value = overview.stations
      selectedStationId.value = overview.selectedStationId ?? stations.value[0]?.stationId
      applyDashboardOverview(overview)
      dataSource.value = overview.dataSource === 'PARTIAL' ? '部分接口' : '实时接口'
    } else {
      applyDashboardOverview(overview)
      await loadPvOutputStationList()
    }
  } catch {
    await loadStationsFallback()
  }

  if (selectedStationId.value) {
    await loadDashboard()
  }
  loading.value = false
}

async function loadDashboard() {
  if (!selectedStationId.value) return
  if (isPvOutputMode.value) {
    await loadPvOutputDashboard()
    return
  }
  loading.value = true
  try {
    const overview = await getDashboardOverview({ stationId: selectedStationId.value })
    applyDashboardOverview(overview)
    dataSource.value = overview.dataSource === 'PARTIAL' ? '部分接口' : '实时接口'
    await loadInternalHistory(selectedStationId.value)
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
  } else if (realtime.value) {
    chartRows.value = [mapRealtimePoint(realtime.value)]
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

function startRealtimePolling() {
  if (realtimeTimer) clearInterval(realtimeTimer)
  realtimeTimer = setInterval(pollRealtime, REALTIME_INTERVAL_MS)
}

async function pollRealtime() {
  if (!selectedStationId.value || isPvOutputMode.value || realtimeRequesting || document.hidden) return
  realtimeRequesting = true
  try {
    const latest = await getRealtime(selectedStationId.value)
    realtime.value = latest
    lastUpdate.value = latest.collectTime
    appendRealtimePoint(latest)
    await nextTick()
    renderChart()
  } catch {
    // 请求失败时保留最后一次实测结果，不生成或补写任何模拟值。
  } finally {
    realtimeRequesting = false
  }
}

function appendRealtimePoint(item: RealtimePvData) {
  const point = mapRealtimePoint(item)
  const index = chartRows.value.findIndex((row) => row.time === point.time)
  if (index >= 0) chartRows.value.splice(index, 1, point)
  else chartRows.value.push(point)
  chartRows.value = chartRows.value.slice(-60)
}

async function changeStation() {
  chartRows.value = []
  realtime.value = null
  charts.forEach((item) => item.clear())
  await loadDashboard()
}

async function loadInternalHistory(stationId: number) {
  const end = new Date()
  const start = new Date(end.getTime() - 60 * 60 * 1000)
  try {
    const rows = await getHistory(stationId, {
      startTime: formatDateTime(start),
      endTime: formatDateTime(end),
      interval: '5min'
    })
    chartRows.value = rows.length
      ? rows.map(mapHistoryPoint)
      : realtime.value ? [mapRealtimePoint(realtime.value)] : []
  } catch {
    chartRows.value = realtime.value ? [mapRealtimePoint(realtime.value)] : []
  }
}

async function loadStationsFallback() {
  try {
    const result = await getStations({ pageNum: 1, pageSize: 50 })
    stations.value = result.records ?? []
    if (stations.value.length) {
      isPvOutputMode.value = false
      selectedStationId.value = stations.value[0]?.stationId
    } else {
      await loadPvOutputStationList()
    }
  } catch (internalError) {
    try {
      await loadPvOutputStationList()
    } catch (pvOutputError) {
      clearDashboard()
      ElMessage.error(message(pvOutputError, message(internalError, '电站列表加载失败')))
    }
  }
}

async function loadPvOutputStationList() {
  const records = await loadPvOutputStations({ enabled: true })
  pvOutputStations.value = records.filter((item) => typeof item.id === 'number')
  stations.value = pvOutputStations.value.map(mapPvOutputStation)
  isPvOutputMode.value = true
  selectedStationId.value = stations.value[0]?.stationId
  dataSource.value = 'PVOutput 公开数据'
  clearDashboard()
  if (!stations.value.length) {
    ElMessage.warning('PVOutput 暂无可展示的公开电站')
  }
}

async function loadPvOutputDashboard() {
  const station = selectedPvOutputStation.value
  if (!station?.id) return
  loading.value = true
  clearDashboard()

  const end = new Date()
  const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000)
  const [latestResult, historyResult, weatherResult, forecastResult] = await Promise.allSettled([
    loadPvOutputLatestStatus(station.id),
    loadPvOutputHistory(station.id, {
      startTime: formatIsoDateTime(start),
      endTime: formatIsoDateTime(end)
    }),
    getPvOutputCurrentWeather(station.id),
    getPvOutputForecast(station.id)
  ])

  pvOutputStatus.value = latestResult.status === 'fulfilled' ? latestResult.value : null
  if (pvOutputStatus.value) {
    realtime.value = mapPvOutputRealtime(station.id, pvOutputStatus.value)
  }
  if (historyResult.status === 'fulfilled' && historyResult.value.length) {
    chartRows.value = historyResult.value.map(mapPvOutputHistoryPoint)
  } else if (pvOutputStatus.value) {
    chartRows.value = [mapPvOutputHistoryPoint(pvOutputStatus.value)]
  }

  if (weatherResult.status === 'fulfilled') {
    weather.value = weatherResult.value
    weatherError.value = ''
  } else {
    weatherError.value = weatherResult.reason instanceof Error
      ? weatherResult.reason.message
      : '该公开电站暂无天气数据'
  }
  forecasts.value = forecastResult.status === 'fulfilled' ? forecastResult.value.slice(0, 3) : []

  const partial = [latestResult, historyResult, weatherResult, forecastResult]
    .some((result) => result.status === 'rejected')
  dataSource.value = partial ? 'PVOutput（部分数据）' : 'PVOutput 公开数据'
  lastUpdate.value = pvOutputStatus.value?.sampleTime || weather.value?.reportTime || station.lastSyncTime || ''
  loading.value = false
  await nextTick()
  renderChart()
}

function applyDashboardOverview(overview: Awaited<ReturnType<typeof getDashboardOverview>>) {
  if (overview.stations?.length) {
    stations.value = overview.stations
  }
  selectedStationId.value = overview.selectedStationId ?? selectedStationId.value
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
  pvOutputStatus.value = null
  weather.value = null
  forecasts.value = []
  weatherError.value = ''
  chartRows.value = []
  renderChart()
}

function mapPvOutputStation(item: PvOutputStation): DashboardStation {
  const details = [
    item.orientation ? `朝向 ${item.orientation}` : '',
    item.panel ? `组件 ${item.panel}` : '',
    item.inverter ? `逆变器 ${item.inverter}` : ''
  ].filter(Boolean)
  return {
    stationId: item.id as number,
    stationName: item.systemName || `PVOutput ${item.externalSystemId}`,
    province: 'PVOutput',
    city: item.postcode || '',
    address: '',
    longitude: item.longitude,
    latitude: item.latitude,
    capacity: (item.systemSizeW ?? 0) / 1000,
    status: !item.enabled ? 'STOPPED' : item.lastSyncStatus === 'FAILED' ? 'SYNC_ERROR' : 'RUNNING',
    description: details.join(' · ') || `公开系统 ID ${item.externalSystemId}`
  }
}

function mapPvOutputRealtime(stationId: number, item: PvOutputStatus): RealtimePvData {
  const voltage = item.voltageV ?? 0
  const powerW = item.powerGenerationW ?? 0
  return {
    stationId,
    collectTime: item.sampleTime,
    power: powerW / 1000,
    voltage,
    current: voltage > 0 ? powerW / voltage : 0,
    irradiance: 0,
    temperature: item.temperatureC ?? 0,
    humidity: 0,
    windSpeed: 0
  }
}

function mapPvOutputHistoryPoint(item: PvOutputStatus): ChartPoint {
  return {
    time: item.sampleTime.slice(5, 16).replace('T', ' '),
    power: typeof item.powerGenerationW === 'number' ? item.powerGenerationW / 1000 : undefined,
    consumptionPower: typeof item.powerConsumptionW === 'number' ? item.powerConsumptionW / 1000 : undefined,
    voltage: item.voltageV,
    temperature: item.temperatureC
  }
}

function mapHistoryPoint(item: PvHistoryItem): ChartPoint {
  return {
    time: item.time.slice(11, 16) || item.time,
    power: item.power,
    voltage: item.voltage,
    current: item.current,
    irradiance: item.irradiance,
    temperature: item.temperature,
    humidity: item.humidity,
    windSpeed: item.windSpeed
  }
}

function mapRealtimePoint(item: RealtimePvData): ChartPoint {
  return {
    time: item.collectTime.slice(11, 16) || item.collectTime,
    power: item.power,
    voltage: item.voltage,
    current: item.current,
    irradiance: item.irradiance,
    temperature: item.temperature,
    humidity: item.humidity,
    windSpeed: item.windSpeed
  }
}

function renderChart() {
  chartPanels.value.forEach((panel) => {
    const element = chartRefs.get(panel.key)
    if (!element) return
    const instance = charts.get(panel.key) ?? echarts.init(element)
    charts.set(panel.key, instance)
    instance.setOption({
      tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => String(value ?? '-') },
      legend: { top: 0, right: 0, itemWidth: 16, itemHeight: 3, textStyle: { color: '#52637a' } },
      grid: { left: 10, right: panel.series.length > 2 ? 42 : 10, top: 44, bottom: 8, containLabel: true },
      xAxis: {
        type: 'category', boundaryGap: false, data: chartRows.value.map((item) => item.time),
        axisLine: { lineStyle: { color: '#d9e4ef' } }, axisTick: { show: false },
        axisLabel: { color: '#748399', hideOverlap: true }
      },
      yAxis: panel.series.map((series, index) => ({
        type: 'value', name: series.unit, position: index === 0 ? 'left' : 'right',
        offset: index === 2 ? 38 : 0,
        nameTextStyle: { color: series.color }, axisLabel: { color: '#748399' },
        axisLine: { show: false }, axisTick: { show: false },
        splitLine: { show: index === 0, lineStyle: { color: '#edf2f7' } }
      })),
      series: panel.series.map((series) => ({
        name: `${series.name} ${series.unit}`, type: 'line', smooth: 0.35, showSymbol: false,
        symbol: 'circle', symbolSize: 6, yAxisIndex: series.axis, connectNulls: false,
        lineStyle: { width: 2.5, color: series.color }, itemStyle: { color: series.color },
        areaStyle: panel.key === 'generation' && series.field === 'power'
          ? { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(22, 119, 255, 0.20)' },
              { offset: 1, color: 'rgba(22, 119, 255, 0.01)' }
            ]) }
          : undefined,
        data: chartRows.value.map((item) => item[series.field] ?? null)
      }))
    }, true)
  })
}

function setChartRef(key: string, element: unknown) {
  if (element instanceof HTMLDivElement) chartRefs.set(key, element)
}

function hasChartData(field: keyof ChartPoint) {
  return chartRows.value.some((item) => typeof item[field] === 'number')
}

function resizeChart() {
  charts.forEach((item) => item.resize())
}

function formatDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatIsoDateTime(date: Date) {
  return formatDateTime(date).replace(' ', 'T')
}

function formatNumber(value: number, digits: number) {
  return Number.isFinite(value) ? value.toFixed(digits) : '-'
}

function formatOptionalNumber(value: number | undefined, digits: number) {
  return typeof value === 'number' ? formatNumber(value, digits) : '-'
}

function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function stationTagType(status?: string) {
  if (status === 'RUNNING') return 'success'
  if (status === 'MAINTENANCE' || status === 'SYNC_ERROR') return 'warning'
  if (status === 'STOPPED') return 'info'
  return 'primary'
}

function stationStatusText(status?: string) {
  const map: Record<string, string> = {
    RUNNING: '运行中',
    MAINTENANCE: '维护中',
    SYNC_ERROR: '同步异常',
    STOPPED: '已停机'
  }
  return map[status ?? ''] ?? '未知'
}

function formatCoordinate(value?: number) {
  return typeof value === 'number' ? value.toFixed(4) : '-'
}

function stationIdentifier() {
  if (isPvOutputMode.value) {
    return selectedPvOutputStation.value?.externalSystemId
      ? `System ID ${selectedPvOutputStation.value.externalSystemId}`
      : '-'
  }
  return selectedStation.value ? `#${selectedStation.value.stationId}` : '-'
}
</script>

<template>
  <section class="page-shell dashboard-page" v-loading="loading">
    <section class="page-section station-header">
      <template v-if="selectedStation">
        <div class="station-title">
          <p class="page-kicker">电站看板</p>
          <div class="station-name-row">
            <h2>{{ selectedStation.stationName }}</h2>
            <el-tag :type="stationTagType(selectedStation.status)" effect="light">
              {{ stationStatusText(selectedStation.status) }}
            </el-tag>
            <el-tag v-if="isPvOutputMode" class="source-tag" effect="plain" type="primary">
              PVOutput 公开电站
            </el-tag>
          </div>
          <p class="station-desc">{{ selectedStation.description || '-' }}</p>
        </div>

        <div class="station-switch">
          <span>切换电站</span>
          <el-select v-model="selectedStationId" class="station-select" size="large" @change="changeStation">
            <el-option
              v-for="station in stations"
              :key="station.stationId"
              :label="station.stationName"
              :value="station.stationId"
            />
          </el-select>
          <el-button :icon="Refresh" size="large" type="primary" @click="changeStation">刷新</el-button>
        </div>

        <div class="station-info-grid">
          <div class="station-info-item wide">
            <el-icon><Location /></el-icon>
            <div>
              <span>位置</span>
              <strong>{{ stationLocation }}</strong>
            </div>
          </div>
          <div class="station-info-item">
            <span>装机容量</span>
            <strong>{{ selectedStation.capacity }} kW</strong>
          </div>
          <div class="station-info-item">
            <span>经纬度</span>
            <strong>{{ formatCoordinate(selectedStation.longitude) }}, {{ formatCoordinate(selectedStation.latitude) }}</strong>
          </div>
          <div class="station-info-item">
            <span>电站编号</span>
            <strong>{{ stationIdentifier() }}</strong>
          </div>
          <div class="station-info-item">
            <span>数据来源</span>
            <strong>{{ dataSource }}</strong>
          </div>
        </div>
      </template>
      <el-empty v-else class="full-empty" description="暂无内部电站或 PVOutput 公开电站" />
    </section>

    <div class="dashboard-main-grid">
      <section class="page-section realtime-panel">
        <div class="panel-head">
          <div>
            <p class="page-kicker">实时图</p>
            <h3>光伏运行曲线</h3>
          </div>
          <div v-if="realtime" class="collect-time">
            <i class="live-dot" />
            <b>每 10 秒更新</b>
            <el-icon><Timer /></el-icon>
            <span>{{ realtime.collectTime }}</span>
          </div>
        </div>

        <template v-if="realtime">
          <div class="realtime-metrics">
            <div v-for="item in realtimeMetrics" :key="item.label" class="metric-chip">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }} <small>{{ item.unit }}</small></strong>
            </div>
          </div>
          <div class="curve-grid">
            <article v-for="panel in chartPanels" :key="panel.key" class="curve-card">
              <div class="curve-card-head">
                <div>
                  <span>{{ panel.eyebrow }}</span>
                  <h4>{{ panel.title }}</h4>
                </div>
                <p>{{ panel.description }}</p>
              </div>
              <div :ref="(element) => setChartRef(panel.key, element)" class="realtime-chart" />
            </article>
          </div>
        </template>
        <el-empty v-else description="暂无实时数据" />
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
            <div class="weather-tile"><span>天气</span><strong>{{ weather.weather }}</strong></div>
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

  </section>
</template>

<style scoped>
.dashboard-page { gap: 18px; }
.station-header { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 18px 24px; }
.station-title { min-width: 0; }
.station-name-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 4px; }
.station-name-row h2, .panel-head h3, .weather-head h3 { margin: 0; color: #10274c; }
.station-name-row h2 { font-size: 24px; }
.source-tag { font-weight: 700; letter-spacing: 0.02em; }
.station-desc { margin: 8px 0 0; color: var(--color-muted); }
.station-switch { display: flex; align-items: center; gap: 10px; }
.station-switch span { color: var(--color-muted); font-weight: 700; white-space: nowrap; }
.station-select { width: 260px; }
.station-info-grid { display: grid; grid-column: 1 / -1; grid-template-columns: minmax(260px, 1.7fr) repeat(4, minmax(150px, 1fr)); gap: 12px; }
.station-info-item { min-height: 74px; padding: 12px 14px; border: 1px solid #e2edf7; border-radius: 8px; background: #f8fbff; }
.station-info-item.wide { display: flex; align-items: center; gap: 10px; }
.station-info-item .el-icon { color: var(--color-primary); font-size: 20px; }
.station-info-item span { display: block; color: var(--color-muted); font-size: 13px; }
.station-info-item strong { display: block; margin-top: 8px; color: #10274c; font-size: 15px; line-height: 1.35; }
.full-empty { grid-column: 1 / -1; }
.dashboard-main-grid { display: grid; grid-template-columns: minmax(0, 1fr) 318px; gap: 18px; align-items: start; }
.realtime-panel { min-height: 560px; }
.panel-head, .weather-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.collect-time, .weather-location { display: inline-flex; align-items: center; gap: 6px; color: var(--color-muted); font-size: 13px; white-space: nowrap; }
.collect-time b { margin-right: 5px; color: #178553; font-size: 12px; }
.live-dot { width: 7px; height: 7px; border-radius: 50%; background: #20a66a; box-shadow: 0 0 0 4px rgba(32, 166, 106, .12); }
.weather-location { color: #4f7891; font-weight: 700; }
.realtime-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin: 18px 0; }
.metric-chip { min-height: 78px; padding: 12px; border: 1px solid #e2edf7; border-radius: 8px; background: #fbfdff; }
.metric-chip span { display: block; color: var(--color-muted); font-size: 13px; }
.metric-chip strong { display: block; margin-top: 10px; color: #10274c; font-size: 20px; line-height: 1.1; }
.metric-chip small { color: var(--color-muted); font-size: 12px; font-weight: 600; }
.curve-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.curve-card { min-width: 0; padding: 16px 16px 8px; border: 1px solid #dfe9f3; border-radius: 10px; background: #fff; }
.curve-card:first-child { grid-column: 1 / -1; border-color: #cfe0f2; background: linear-gradient(180deg, #fbfdff 0%, #fff 72%); }
.curve-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.curve-card-head span { color: #1677ff; font-size: 11px; font-weight: 800; letter-spacing: .12em; }
.curve-card-head h4 { margin: 3px 0 0; color: #10274c; font-size: 17px; }
.curve-card-head p { max-width: 240px; margin: 2px 0 0; color: var(--color-muted); font-size: 12px; line-height: 1.5; text-align: right; }
.realtime-chart { width: 100%; height: 260px; }
.curve-card:first-child .realtime-chart { height: 310px; }
.weather-card { min-height: 430px; padding: 14px; border: 1px solid #b9e5ea; border-radius: 8px; background: #c8eff6; color: #234f6b; box-shadow: var(--shadow-panel); }
.weather-head { align-items: center; }
.weather-list { display: grid; gap: 10px; }
.weather-tile { min-height: 62px; padding: 10px 12px; border-radius: 8px; background: rgba(178, 235, 222, 0.72); }
.weather-tile span { display: block; color: #5d82a0; font-weight: 700; }
.weather-tile strong { display: block; margin-top: 6px; color: #1e4965; font-size: 20px; }
.forecast-mini { display: grid; gap: 8px; margin-top: 12px; }
.forecast-mini div { display: grid; grid-template-columns: 44px 1fr auto; align-items: center; gap: 8px; min-height: 34px; padding: 0 10px; border-radius: 8px; background: rgba(255, 255, 255, 0.4); font-size: 12px; }
.forecast-mini em { font-style: normal; }
.weather-update { margin: 12px 2px 0; color: #6796b4; font-style: italic; font-weight: 700; line-height: 1.45; }
@media (max-width: 1280px) {
  .station-header { grid-template-columns: 1fr; }
  .station-switch { justify-content: flex-start; }
  .station-info-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .realtime-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 980px) {
  .dashboard-main-grid { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .station-switch { align-items: stretch; flex-direction: column; }
  .station-select { width: 100%; }
  .station-info-grid, .realtime-metrics { grid-template-columns: 1fr; }
  .panel-head { align-items: flex-start; flex-direction: column; }
  .curve-grid { grid-template-columns: 1fr; }
  .curve-card:first-child { grid-column: auto; }
  .curve-card-head { display: block; }
  .curve-card-head p { margin-top: 6px; text-align: left; }
  .realtime-chart, .curve-card:first-child .realtime-chart { height: 280px; }
}
</style>
