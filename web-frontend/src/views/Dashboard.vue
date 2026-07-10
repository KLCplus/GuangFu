<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDashboardOverview } from '../api/dashboard'
import type { DashboardResource } from '../api/dashboard'
import { getRealtime, getStations } from '../api/station'
import { getCurrentWeather, getForecast, getLocationCurrentWeather, getLocationForecast } from '../api/weather'
import { mockForecast, mockRealtime, mockStations, mockWeather } from '../data/mock'

interface StationOption {
  stationId: number
  stationName: string
  city: string
  capacity: number
  status: string
  address: string
}

interface WeatherSnapshot {
  weather: string
  temperature: number
  humidity: number
  windDirection: string
  windPower: string
  windSpeed: number
  cloud: number
  reportTime: string
  source: string
  cached: boolean
}

interface ForecastRow {
  date: string
  dayWeather: string
  nightWeather: string
  dayTemp: number
  nightTemp: number
  humidity: number
}

interface RealtimeSnapshot {
  collectTime: string
  power: number
  voltage: number
  current: number
  irradiance: number
  temperature: number
  humidity: number
  windSpeed: number
}

interface ServerResource {
  name: string
  value: number
  detail: string
  level: 'healthy' | 'warning' | 'danger'
}

const router = useRouter()
const loading = ref(false)
const stations = ref<StationOption[]>([])
const selectedStationId = ref<number>()
const dataSource = ref('接口优先')
const lastUpdate = ref('')
const dashboardResources = ref<DashboardResource[]>([])
const weatherError = ref('')
const weatherLocation = ref('')

const weather = ref<WeatherSnapshot>(normalizeWeather(mockWeather, 1))
const forecasts = ref<ForecastRow[]>(mockForecast.map(normalizeForecast))
const realtime = ref<RealtimeSnapshot>(normalizeRealtime(mockRealtime))

const selectedStation = computed(() =>
  stations.value.find((item) => item.stationId === selectedStationId.value) ?? stations.value[0]
)

const utilization = computed(() => {
  const capacity = selectedStation.value?.capacity || 1
  return Math.min(100, Math.round((realtime.value.power / capacity) * 100))
})

const summaryCards = computed(() => [
  {
    label: '实时功率',
    value: `${realtime.value.power.toFixed(1)} kW`,
    note: `装机利用率 ${utilization.value}%`
  },
  {
    label: '当前辐照度',
    value: `${Math.round(realtime.value.irradiance)} W/m2`,
    note: `组件温度 ${realtime.value.temperature.toFixed(1)} C`
  },
  {
    label: '天气状态',
    value: weather.value.weather,
    note: `${weather.value.temperature} C · 云量 ${weather.value.cloud}%`
  },
  {
    label: '数据来源',
    value: dataSource.value,
    note: lastUpdate.value || '等待刷新'
  }
])

const serverResources = computed<ServerResource[]>(() => {
  if (dashboardResources.value.length) {
    return dashboardResources.value.map((item) => ({
      name: item.name,
      value: item.value,
      detail: item.detail,
      level: normalizeResourceLevel(item.level)
    }))
  }
  const seed = selectedStationId.value ?? 1
  const resources = [
    { name: 'CPU', value: 34 + seed * 4, detail: '推理网关 8 Core' },
    { name: 'GPU', value: 42 + seed * 6, detail: 'A10 任务队列' },
    { name: '内存', value: 51 + seed * 3, detail: '32 GB 服务池' },
    { name: '硬盘', value: 58 + seed * 2, detail: '模型与日志卷' }
  ]
  return resources.map((item) => {
    const value = Math.min(item.value, 94)
    return {
      ...item,
      value,
      level: value >= 85 ? 'danger' : value >= 70 ? 'warning' : 'healthy'
    }
  })
})

const weatherTheme = computed(() => {
  const text = weather.value.weather
  if (text.includes('雨')) {
    return {
      className: 'rainy',
      iconClass: 'rainy',
      label: '降雨影响',
      hint: '注意组件表面湿滑和逆变器告警'
    }
  }
  if (text.includes('阴')) {
    return {
      className: 'overcast',
      iconClass: 'cloudy',
      label: '低辐照',
      hint: '功率可能进入平缓区间'
    }
  }
  if (text.includes('云')) {
    return {
      className: 'cloudy',
      iconClass: 'cloudy',
      label: '云量波动',
      hint: '关注短时遮挡带来的功率变化'
    }
  }
  return {
    className: 'sunny',
    iconClass: 'sunny',
    label: '发电友好',
    hint: '辐照条件较好，适合保持高效出力'
  }
})

onMounted(async () => {
  await loadStations()
})

async function loadStations() {
  loading.value = true
  try {
    const overview = await getDashboardOverview()
    const records = Array.isArray(overview.stations) && overview.stations.length ? overview.stations : mockStations
    stations.value = records.map((item) => ({
      stationId: item.stationId,
      stationName: item.stationName,
      city: item.city,
      capacity: item.capacity,
      status: item.status,
      address: item.address
    }))
    selectedStationId.value = overview.selectedStationId ?? stations.value[0]?.stationId
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
    loading.value = false
    return
  } catch {
    // Fall back to the old split-interface flow below.
  }

  const stationId = selectedStationId.value
  const [weatherResult, forecastResult, realtimeResult] = await Promise.allSettled([
    getCurrentWeather(stationId),
    getForecast(stationId),
    getRealtime(stationId)
  ])

  if (weatherResult.status === 'fulfilled') {
    weatherError.value = ''
    weather.value = normalizeWeather(weatherResult.value, stationId)
  } else {
    weatherError.value = weatherResult.reason instanceof Error ? weatherResult.reason.message : '天气接口调用失败'
    weather.value = unavailableWeather(weatherError.value)
  }

  if (forecastResult.status === 'fulfilled' && forecastResult.value.length) {
    forecasts.value = forecastResult.value.slice(0, 3).map(normalizeForecast)
  } else {
    forecasts.value = []
  }

  if (realtimeResult.status === 'fulfilled') {
    realtime.value = normalizeRealtime(realtimeResult.value)
  } else {
    realtime.value = normalizeRealtime(mockRealtime)
  }

  const usingFallback = [weatherResult, forecastResult, realtimeResult].some(
    (item) => item.status === 'rejected'
  )
  dataSource.value = usingFallback ? '部分接口' : '实时接口'
  lastUpdate.value = weather.value.reportTime || realtime.value.collectTime
  loading.value = false
}

async function loadStationsFallback() {
  try {
    const result = await getStations({ pageNum: 1, pageSize: 50 })
    const records = Array.isArray(result.records) && result.records.length ? result.records : mockStations
    stations.value = records.map((item) => ({
      stationId: item.stationId,
      stationName: item.stationName,
      city: item.city,
      capacity: item.capacity,
      status: item.status,
      address: item.address
    }))
    dataSource.value = records === mockStations ? '演示数据' : '实时接口'
  } catch {
    stations.value = mockStations.map((item) => ({
      stationId: item.stationId,
      stationName: item.stationName,
      city: item.city,
      capacity: item.capacity,
      status: item.status,
      address: item.address
    }))
    dataSource.value = '演示数据'
  }
  selectedStationId.value = stations.value[0]?.stationId
}

function applyDashboardOverview(overview: Awaited<ReturnType<typeof getDashboardOverview>>) {
  if (overview.stations?.length) {
    stations.value = overview.stations.map((item) => ({
      stationId: item.stationId,
      stationName: item.stationName,
      city: item.city,
      capacity: item.capacity,
      status: item.status,
      address: item.address
    }))
  }
  selectedStationId.value = overview.selectedStationId ?? selectedStationId.value
  if (overview.weather) {
    weatherError.value = ''
    weather.value = normalizeWeather(overview.weather, overview.selectedStationId ?? 1)
  } else if (overview.dataSource === 'PARTIAL') {
    weatherError.value = '后端天气接口未返回数据'
    weather.value = unavailableWeather(weatherError.value)
  }
  forecasts.value = overview.forecasts?.length ? overview.forecasts.slice(0, 3).map(normalizeForecast) : []
  if (overview.realtime) realtime.value = normalizeRealtime(overview.realtime)
  dashboardResources.value = overview.resources ?? []
  lastUpdate.value = overview.lastUpdate || weather.value.reportTime || realtime.value.collectTime
}

function unavailableWeather(message = '天气接口暂不可用'): WeatherSnapshot {
  return {
    weather: '天气不可用',
    temperature: 0,
    humidity: 0,
    windDirection: '-',
    windPower: '-',
    windSpeed: 0,
    cloud: 0,
    reportTime: '',
    source: message,
    cached: true
  }
}

function normalizeWeather(value: Partial<WeatherSnapshot>, stationId: number): WeatherSnapshot {
  return {
    weather: value.weather ?? '晴',
    temperature: Number(value.temperature ?? 32),
    humidity: Number(value.humidity ?? 60),
    windDirection: value.windDirection ?? '东南风',
    windPower: value.windPower ?? '3级',
    windSpeed: Number(value.windSpeed ?? mockRealtime.windSpeed),
    cloud: Number(value.cloud ?? 18 + stationId * 6),
    reportTime: value.reportTime ?? mockWeather.reportTime,
    source: value.source ?? '本地演示',
    cached: Boolean(value.cached ?? true)
  }
}

function normalizeForecast(value: Partial<ForecastRow>): ForecastRow {
  return {
    date: value.date ?? '',
    dayWeather: value.dayWeather ?? '晴',
    nightWeather: value.nightWeather ?? '多云',
    dayTemp: Number(value.dayTemp ?? 32),
    nightTemp: Number(value.nightTemp ?? 25),
    humidity: Number(value.humidity ?? 60)
  }
}

function normalizeRealtime(value: Partial<RealtimeSnapshot>): RealtimeSnapshot {
  return {
    collectTime: value.collectTime ?? mockRealtime.collectTime,
    power: Number(value.power ?? mockRealtime.power),
    voltage: Number(value.voltage ?? mockRealtime.voltage),
    current: Number(value.current ?? mockRealtime.current),
    irradiance: Number(value.irradiance ?? mockRealtime.irradiance),
    temperature: Number(value.temperature ?? mockRealtime.temperature),
    humidity: Number(value.humidity ?? mockRealtime.humidity),
    windSpeed: Number(value.windSpeed ?? mockRealtime.windSpeed)
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
    weather.value = normalizeWeather(currentResult, selectedStationId.value ?? 1)
    forecasts.value = forecastResult.slice(0, 3).map(normalizeForecast)
    dataSource.value = '地点天气'
    lastUpdate.value = weather.value.reportTime
  } catch (error) {
    weatherError.value = error instanceof Error ? error.message : '地点天气查询失败'
    weather.value = unavailableWeather(weatherError.value)
    forecasts.value = []
    ElMessage.error(weatherError.value)
  } finally {
    loading.value = false
  }
}

function stationTagType(status?: string) {
  if (status === 'RUNNING') return 'success'
  if (status === 'MAINTENANCE') return 'warning'
  if (status === 'STOPPED') return 'info'
  return 'primary'
}

function stationStatusText(status?: string) {
  const map: Record<string, string> = {
    RUNNING: '运行中',
    MAINTENANCE: '维护中',
    STOPPED: '停机'
  }
  return map[status ?? ''] ?? '未知'
}

function resourceColor(item: ServerResource) {
  if (item.level === 'danger') return '#e35d5d'
  if (item.level === 'warning') return '#d99a2b'
  return '#2c9b6f'
}

function normalizeResourceLevel(level?: string): ServerResource['level'] {
  if (level === 'danger' || level === 'warning' || level === 'healthy') return level
  return 'healthy'
}

function openReport() {
  if (!selectedStationId.value) {
    ElMessage.warning('请先选择电站')
    return
  }
  router.push({ path: '/reports', query: { stationId: selectedStationId.value } })
}
</script>

<template>
  <section class="page-shell dashboard-page" v-loading="loading">
    <div class="page-section dashboard-toolbar">
      <div class="station-block">
        <p class="page-kicker">综合信息分析平台</p>
        <h2>{{ selectedStation?.stationName || '请选择电站' }}</h2>
        <div class="station-meta">
          <el-tag :type="stationTagType(selectedStation?.status)">
            {{ stationStatusText(selectedStation?.status) }}
          </el-tag>
          <span>{{ selectedStation?.city }}</span>
          <span>{{ selectedStation?.capacity }} kW</span>
          <span>{{ selectedStation?.address }}</span>
        </div>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="selectedStationId" size="large" class="station-select" @change="loadDashboard">
          <el-option
            v-for="station in stations"
            :key="station.stationId"
            :label="station.stationName"
            :value="station.stationId"
          />
        </el-select>
        <el-button type="primary" size="large" @click="loadDashboard">刷新</el-button>
      </div>
    </div>

    <div class="metric-grid">
      <div v-for="item in summaryCards" :key="item.label" class="metric-tile">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.note }}</p>
      </div>
    </div>

    <div class="dashboard-grid">
      <section class="page-section weather-panel featured-weather" :class="weatherTheme.className">
        <div class="weather-hero">
          <div>
            <p class="page-kicker">天气情况</p>
            <h3>{{ weather.weather }}</h3>
            <p>{{ weatherTheme.hint }}</p>
          </div>
          <div class="weather-symbol" :class="weatherTheme.iconClass" aria-hidden="true">
            <span></span>
          </div>
        </div>

        <div class="weather-query">
          <el-input
            v-model="weatherLocation"
            placeholder="输入城市或地点，如 成都、上海"
            clearable
            @keyup.enter="loadLocationWeather"
          />
          <el-button @click="loadLocationWeather">查询地点天气</el-button>
          <el-button text @click="loadDashboard">返回电站天气</el-button>
        </div>

        <el-alert
          v-if="weatherError"
          class="weather-alert"
          :title="weatherError"
          type="warning"
          show-icon
          :closable="false"
        />

        <div class="weather-main">
          <strong>{{ weather.temperature }} C</strong>
          <div>
            <el-tag effect="dark">{{ weatherTheme.label }}</el-tag>
            <p>{{ weather.source }} · {{ weather.cached ? '缓存可用' : '实时数据' }}</p>
          </div>
        </div>

        <div class="weather-metrics">
          <span>湿度 <b>{{ weather.humidity }}%</b></span>
          <span>风速 <b>{{ weather.windSpeed }} m/s</b></span>
          <span>风向 <b>{{ weather.windDirection }}</b></span>
          <span>云量 <b>{{ weather.cloud }}%</b></span>
        </div>

        <div class="forecast-list">
          <div v-if="!forecasts.length" class="forecast-row empty-forecast">
            <span>-</span>
            <strong>暂无预报数据</strong>
            <em>-</em>
          </div>
          <div v-for="item in forecasts" :key="item.date" class="forecast-row">
            <span>{{ item.date.slice(5) }}</span>
            <strong>{{ item.dayWeather }} / {{ item.nightWeather }}</strong>
            <em>{{ item.nightTemp }}-{{ item.dayTemp }} C</em>
          </div>
        </div>
      </section>

      <section class="page-section server-panel">
        <div class="panel-head compact">
          <div>
            <p class="page-kicker">服务器状态</p>
            <h3>推理资源</h3>
          </div>
          <el-tag type="success">健康</el-tag>
        </div>
        <div class="resource-list">
          <div v-for="item in serverResources" :key="item.name" class="resource-row">
            <div class="resource-label">
              <strong>{{ item.name }}</strong>
              <span>{{ item.detail }}</span>
              <b>{{ item.value }}%</b>
            </div>
            <el-progress :percentage="item.value" :stroke-width="10" :color="resourceColor(item)" />
          </div>
        </div>
      </section>
    </div>

    <div class="page-section action-band">
      <div>
        <p class="page-kicker">电站切换联动</p>
        <h3>切换电站会刷新天气和服务器状态</h3>
      </div>
      <div class="action-buttons">
        <el-button @click="router.push('/stations')">查看电站列表</el-button>
        <el-button type="primary" @click="openReport">生成分析报告</el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.dashboard-page {
  gap: 18px;
}

.dashboard-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.station-block h2,
.panel-head h3,
.action-band h3 {
  margin: 4px 0 0;
}

.station-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  color: var(--color-muted);
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.station-select {
  width: 260px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-tile {
  min-height: 116px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.metric-tile span {
  color: var(--color-muted);
  font-size: 13px;
}

.metric-tile strong {
  display: block;
  margin-top: 12px;
  color: #10274c;
  font-size: 26px;
  line-height: 1.1;
}

.metric-tile p {
  margin: 10px 0 0;
  color: var(--color-muted);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(360px, 0.95fr);
  gap: 16px;
  align-items: start;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-head.compact {
  align-items: center;
}

.featured-weather {
  position: relative;
  overflow: hidden;
  min-height: 430px;
  border-color: transparent;
  color: #ffffff;
}

.featured-weather::after {
  position: absolute;
  right: -70px;
  bottom: -90px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  content: "";
}

.featured-weather.sunny {
  background: linear-gradient(135deg, #1675e5 0%, #48a9f8 52%, #f2b84b 100%);
}

.featured-weather.cloudy {
  background: linear-gradient(135deg, #2667b8 0%, #73a5d8 56%, #b8d4ea 100%);
}

.featured-weather.rainy {
  background: linear-gradient(135deg, #245f9f 0%, #487fa8 52%, #7fb6c7 100%);
}

.featured-weather.overcast {
  background: linear-gradient(135deg, #405f86 0%, #6e87a6 58%, #a9b8c6 100%);
}

.weather-hero,
.weather-main,
.weather-metrics,
.forecast-list {
  position: relative;
  z-index: 1;
}

.weather-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.weather-hero .page-kicker {
  color: rgba(255, 255, 255, 0.82);
}

.weather-hero h3 {
  margin: 8px 0 0;
  font-size: 38px;
  line-height: 1.1;
}

.weather-hero p:not(.page-kicker) {
  max-width: 420px;
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.86);
  line-height: 1.7;
}

.weather-symbol {
  position: relative;
  width: 116px;
  height: 116px;
  flex: 0 0 116px;
}

.weather-symbol span,
.weather-symbol::before,
.weather-symbol::after {
  position: absolute;
  display: block;
  content: "";
}

.weather-symbol.sunny span {
  inset: 27px;
  border-radius: 50%;
  background: #ffd86b;
  box-shadow: 0 0 0 12px rgba(255, 216, 107, 0.24), 0 0 34px rgba(255, 247, 184, 0.78);
}

.weather-symbol.sunny::before {
  inset: 10px;
  border-radius: 50%;
  border: 5px dashed rgba(255, 255, 255, 0.62);
}

.weather-symbol.cloudy span,
.weather-symbol.rainy span {
  right: 8px;
  bottom: 31px;
  width: 74px;
  height: 36px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: -28px 2px 0 2px rgba(255, 255, 255, 0.76);
}

.weather-symbol.cloudy::before,
.weather-symbol.rainy::before {
  right: 22px;
  bottom: 50px;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
}

.weather-symbol.cloudy::after {
  left: 4px;
  top: 8px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 214, 100, 0.76);
}

.weather-symbol.rainy::after {
  left: 28px;
  bottom: 2px;
  width: 7px;
  height: 30px;
  border-radius: 999px;
  background: rgba(188, 232, 255, 0.94);
  box-shadow: 25px -2px 0 rgba(188, 232, 255, 0.86), 50px 0 0 rgba(188, 232, 255, 0.76);
  transform: rotate(15deg);
}

.weather-query {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(180px, 1fr) auto auto;
  gap: 10px;
  margin-top: 22px;
}

.weather-alert {
  position: relative;
  z-index: 1;
  margin-top: 14px;
}

.weather-main {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-top: 34px;
}

.weather-main strong {
  font-size: 72px;
  line-height: 0.95;
  letter-spacing: 0;
}

.weather-main p {
  margin: 10px 0 0;
  color: rgba(255, 255, 255, 0.78);
}

.weather-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 28px;
}

.weather-metrics span {
  min-height: 76px;
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.78);
}

.weather-metrics b {
  display: block;
  margin-top: 8px;
  color: #ffffff;
  font-size: 20px;
}

.forecast-list {
  display: grid;
  gap: 8px;
  margin-top: 20px;
}

.forecast-row {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 42px;
  padding: 0 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.78);
}

.forecast-row strong {
  color: #ffffff;
  font-weight: 600;
}

.forecast-row em {
  color: #ffffff;
  font-style: normal;
}

.empty-forecast {
  opacity: 0.82;
}

.resource-list {
  display: grid;
  gap: 24px;
  padding-top: 10px;
}

.resource-label {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 6px;
}

.resource-label span {
  color: var(--color-muted);
  font-size: 13px;
}

.resource-label b {
  color: #10274c;
}

.action-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.action-buttons {
  display: flex;
  gap: 10px;
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .dashboard-toolbar,
  .action-band {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-actions,
  .action-buttons {
    flex-direction: column;
    align-items: stretch;
  }

  .station-select {
    width: 100%;
  }

  .metric-grid,
  .weather-metrics {
    grid-template-columns: 1fr;
  }

  .weather-hero,
  .weather-main {
    align-items: flex-start;
    flex-direction: column;
  }

  .weather-query {
    grid-template-columns: 1fr;
  }

  .weather-main strong {
    font-size: 54px;
  }
}
</style>
