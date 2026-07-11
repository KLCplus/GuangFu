<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Location, Refresh, Timer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardOverview } from '../api/dashboard'
import { getCurrentWeather, getForecast, getHistory, getRealtime, getStation, getStations } from '../api/station'
import type { Station } from '../api/station'
import type { PvHistoryItem, RealtimePvData } from '../api/pvData'
import type { CurrentWeather, WeatherForecastItem } from '../api/weather'

interface ChartPoint {
  time: string
  power?: number
  voltage?: number
  current?: number
  irradiance?: number
  temperature?: number
  humidity?: number
  windSpeed?: number
}

const loading = ref(false)
const stations = ref<Station[]>([])
const selectedStationId = ref<number>()
const stationDetail = ref<Station | null>(null)
const realtime = ref<RealtimePvData | null>(null)
const weather = ref<CurrentWeather | null>(null)
const forecasts = ref<WeatherForecastItem[]>([])
const chartRows = ref<ChartPoint[]>([])
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const selectedStation = computed(() =>
  stationDetail.value ?? stations.value.find((item) => item.stationId === selectedStationId.value)
)

const stationLocation = computed(() => {
  if (!selectedStation.value) return '-'
  return [selectedStation.value.province, selectedStation.value.city, selectedStation.value.address]
    .filter(Boolean)
    .join(' / ')
})

const realtimeMetrics = computed(() => {
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
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

async function loadStations() {
  loading.value = true
  try {
    const overview = await getDashboardOverview()
    stations.value = overview.stations ?? []
    selectedStationId.value = overview.selectedStationId ?? stations.value[0]?.stationId
    if (selectedStationId.value) {
      applyDashboardOverview(overview)
      await nextTick()
      renderChart()
    } else {
      clearDashboard()
      ElMessage.warning('暂无可展示的电站数据')
    }
  } catch {
    await loadStationsBySection()
  } finally {
    loading.value = false
  }
}

async function loadStationsBySection() {
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
  } catch (error) {
    clearDashboard()
    ElMessage.error(message(error, '电站列表加载失败'))
  }
}

async function loadDashboard() {
  if (!selectedStationId.value) return
  loading.value = true
  const stationId = selectedStationId.value

  try {
    const overview = await getDashboardOverview({ stationId })
    applyDashboardOverview(overview)
    loading.value = false
    await nextTick()
    renderChart()
    return
  } catch {
    // 兼容旧后端：聚合看板接口不可用时，继续使用分段接口加载。
  }

  const end = new Date()
  const start = new Date(end.getTime() - 60 * 60 * 1000)

  const [stationResult, weatherResult, forecastResult, realtimeResult, historyResult] = await Promise.allSettled([
    getStation(stationId),
    getCurrentWeather(stationId),
    getForecast(stationId),
    getRealtime(stationId),
    getHistory(stationId, {
      startTime: formatDateTime(start),
      endTime: formatDateTime(end),
      interval: '5min'
    })
  ])

  stationDetail.value = stationResult.status === 'fulfilled' ? stationResult.value : null
  weather.value = weatherResult.status === 'fulfilled' ? weatherResult.value : null
  forecasts.value = forecastResult.status === 'fulfilled' ? forecastResult.value.slice(0, 3) : []
  realtime.value = realtimeResult.status === 'fulfilled' ? realtimeResult.value : null

  if (historyResult.status === 'fulfilled' && historyResult.value.length) {
    chartRows.value = historyResult.value.map(mapHistoryPoint)
  } else if (realtime.value) {
    chartRows.value = [mapRealtimePoint(realtime.value)]
  } else {
    chartRows.value = []
  }

  reportSectionErrors([
    ['电站详情', stationResult],
    ['天气', weatherResult],
    ['天气预报', forecastResult],
    ['实时数据', realtimeResult],
    ['历史曲线', historyResult]
  ])

  loading.value = false
  await nextTick()
  renderChart()
}

function applyDashboardOverview(overview: Awaited<ReturnType<typeof getDashboardOverview>>) {
  if (overview.stations?.length) {
    stations.value = overview.stations
  }
  selectedStationId.value = overview.selectedStationId ?? selectedStationId.value
  stationDetail.value = stations.value.find((item) => item.stationId === selectedStationId.value) ?? null
  weather.value = overview.weather ?? null
  forecasts.value = overview.forecasts?.slice(0, 3) ?? []
  realtime.value = overview.realtime ?? null
  chartRows.value = overview.realtime ? [mapRealtimePoint(overview.realtime)] : []
}

function clearDashboard() {
  stationDetail.value = null
  realtime.value = null
  weather.value = null
  forecasts.value = []
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
  if (!chartRef.value) return
  chart = chart ?? echarts.init(chartRef.value)
  chart.setOption({
    color: ['#1d6fdc', '#22a06b', '#f59e0b', '#ef6c63', '#7c3aed', '#06a6b8', '#64748b'],
    tooltip: { trigger: 'axis' },
    legend: {
      top: 4,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#40516b' }
    },
    grid: { left: 42, right: 26, top: 58, bottom: 42, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartRows.value.map((item) => item.time),
      axisLine: { lineStyle: { color: '#d8e3f0' } },
      axisLabel: { color: '#66758c' }
    },
    yAxis: [
      {
        type: 'value',
        name: '功率/辐照',
        axisLabel: { color: '#66758c' },
        splitLine: { lineStyle: { color: '#edf3f9' } }
      },
      {
        type: 'value',
        name: '环境/电气',
        axisLabel: { color: '#66758c' },
        splitLine: { show: false }
      }
    ],
    series: [
      lineSeries('功率 kW', 'power'),
      lineSeries('辐照度 W/m²', 'irradiance'),
      lineSeries('电压 V', 'voltage', 1),
      lineSeries('电流 A', 'current', 1),
      lineSeries('温度 °C', 'temperature', 1),
      lineSeries('湿度 %', 'humidity', 1),
      lineSeries('风速 m/s', 'windSpeed', 1)
    ]
  }, true)
}

function lineSeries(name: string, key: keyof ChartPoint, yAxisIndex = 0) {
  return {
    name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 5,
    yAxisIndex,
    data: chartRows.value.map((item) => item[key] ?? null)
  }
}

function reportSectionErrors(results: Array<[string, PromiseSettledResult<unknown>]>) {
  const failed = results.filter(([, result]) => result.status === 'rejected').map(([name]) => name)
  if (failed.length) ElMessage.warning(`${failed.join('、')}加载失败`)
}

function resizeChart() {
  chart?.resize()
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
    STOPPED: '已停机'
  }
  return map[status ?? ''] ?? '未知'
}

function formatCoordinate(value?: number) {
  return typeof value === 'number' ? value.toFixed(4) : '-'
}

function formatDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
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
          </div>
          <p class="station-desc">{{ selectedStation.description || '-' }}</p>
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
            <strong>#{{ selectedStation.stationId }}</strong>
          </div>
          <div class="station-info-item">
            <span>状态</span>
            <strong>{{ stationStatusText(selectedStation.status) }}</strong>
          </div>
        </div>
      </template>
      <el-empty v-else class="full-empty" description="暂无电站数据" />
    </section>

    <div class="dashboard-main-grid">
      <section class="page-section realtime-panel">
        <div class="panel-head">
          <div>
            <p class="page-kicker">实时图</p>
            <h3>光伏运行曲线</h3>
          </div>
          <div v-if="realtime" class="collect-time">
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
          <div ref="chartRef" class="realtime-chart" />
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
            <div class="weather-tile">
              <span>天气</span>
              <strong>{{ weather.weather }}</strong>
            </div>
            <div class="weather-tile">
              <span>温度</span>
              <strong>{{ weather.temperature }}°C</strong>
            </div>
            <div class="weather-tile">
              <span>湿度</span>
              <strong>{{ weather.humidity }}%</strong>
            </div>
            <div class="weather-tile">
              <span>风速</span>
              <strong>{{ weather.windPower || `${weather.windSpeed} m/s` }}</strong>
            </div>
            <div class="weather-tile">
              <span>风向</span>
              <strong>{{ weather.windDirection }}</strong>
            </div>
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
        <el-empty v-else description="暂无天气数据" />
      </aside>
    </div>

    <section class="page-section server-placeholder">
      <p class="page-kicker">服务器信息</p>
    </section>
  </section>
</template>

<style scoped>
.dashboard-page {
  gap: 18px;
}

.station-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px 24px;
}

.station-title {
  min-width: 0;
}

.station-name-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.station-name-row h2 {
  margin: 0;
  color: #10274c;
  font-size: 24px;
}

.station-desc {
  margin: 8px 0 0;
  color: var(--color-muted);
}

.station-switch {
  display: flex;
  align-items: center;
  gap: 10px;
}

.station-switch span {
  color: var(--color-muted);
  font-weight: 700;
  white-space: nowrap;
}

.station-select {
  width: 260px;
}

.station-info-grid {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: minmax(260px, 1.7fr) repeat(4, minmax(150px, 1fr));
  gap: 12px;
}

.station-info-item {
  min-height: 74px;
  padding: 12px 14px;
  border: 1px solid #e2edf7;
  border-radius: 8px;
  background: #f8fbff;
}

.station-info-item.wide {
  display: flex;
  align-items: center;
  gap: 10px;
}

.station-info-item .el-icon {
  color: var(--color-primary);
  font-size: 20px;
}

.station-info-item span {
  display: block;
  color: var(--color-muted);
  font-size: 13px;
}

.station-info-item strong {
  display: block;
  margin-top: 8px;
  color: #10274c;
  font-size: 15px;
  line-height: 1.35;
}

.full-empty {
  grid-column: 1 / -1;
}

.dashboard-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 318px;
  gap: 18px;
  align-items: start;
}

.realtime-panel {
  min-height: 560px;
}

.panel-head,
.weather-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h3,
.weather-head h3 {
  margin: 4px 0 0;
  color: #10274c;
}

.collect-time,
.weather-location {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-muted);
  font-size: 13px;
  white-space: nowrap;
}

.realtime-metrics {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  margin: 18px 0;
}

.metric-chip {
  min-height: 78px;
  padding: 12px;
  border: 1px solid #e2edf7;
  border-radius: 8px;
  background: #fbfdff;
}

.metric-chip span {
  display: block;
  color: var(--color-muted);
  font-size: 13px;
}

.metric-chip strong {
  display: block;
  margin-top: 10px;
  color: #10274c;
  font-size: 20px;
  line-height: 1.1;
}

.metric-chip small {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 600;
}

.realtime-chart {
  width: 100%;
  height: 390px;
}

.weather-card {
  min-height: 430px;
  padding: 14px;
  border: 1px solid #b9e5ea;
  border-radius: 8px;
  background: #c8eff6;
  color: #234f6b;
  box-shadow: var(--shadow-panel);
}

.weather-head {
  align-items: center;
  margin-bottom: 12px;
}

.weather-head h3 {
  margin: 0;
  font-size: 18px;
}

.weather-location {
  color: #4f7891;
  font-weight: 700;
}

.weather-list {
  display: grid;
  gap: 10px;
}

.weather-tile {
  min-height: 62px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(178, 235, 222, 0.72);
}

.weather-tile span {
  display: block;
  color: #5d82a0;
  font-weight: 700;
}

.weather-tile strong {
  display: block;
  margin-top: 6px;
  color: #1e4965;
  font-size: 20px;
}

.forecast-mini {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.forecast-mini div {
  display: grid;
  grid-template-columns: 44px 1fr auto;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}

.forecast-mini em {
  font-style: normal;
}

.weather-update {
  margin: 12px 2px 0;
  color: #6796b4;
  font-style: italic;
  font-weight: 700;
  line-height: 1.45;
}

.server-placeholder {
  min-height: 150px;
}

@media (max-width: 1280px) {
  .station-header {
    grid-template-columns: 1fr;
  }

  .station-switch {
    justify-content: flex-start;
  }

  .station-info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .realtime-metrics {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .dashboard-main-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .station-switch {
    align-items: stretch;
    flex-direction: column;
  }

  .station-select {
    width: 100%;
  }

  .station-info-grid,
  .realtime-metrics {
    grid-template-columns: 1fr;
  }

  .panel-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .realtime-chart {
    height: 340px;
  }
}
</style>
