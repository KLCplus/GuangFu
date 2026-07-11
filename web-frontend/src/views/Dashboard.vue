<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Location, Refresh, Timer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  loadPvOutputHistory,
  loadPvOutputLatestStatus,
  loadPvOutputStations,
  syncPvOutputStation
} from '../api/pvoutput'
import type { PvOutputStation, PvOutputStatus } from '../api/pvoutput'
import { getLocationCurrentWeather, getLocationForecast } from '../api/weather'
import type { CurrentWeather, WeatherForecastItem, WeatherLocationQuery } from '../api/weather'

const loading = ref(false)
const statusLoading = ref(false)
const syncLoading = ref(false)
const stations = ref<PvOutputStation[]>([])
const selectedStationId = ref<number>()
const latestStatus = ref<PvOutputStatus | null>(null)
const historyRows = ref<PvOutputStatus[]>([])
const weather = ref<CurrentWeather | null>(null)
const forecasts = ref<WeatherForecastItem[]>([])
const weatherError = ref('')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const selectedStation = computed(() =>
  stations.value.find((item) => item.id === selectedStationId.value) ?? stations.value[0]
)

const totalCapacityKw = computed(() => stations.value.reduce((sum, item) => sum + (item.systemSizeW ?? 0), 0) / 1000)
const enabledCount = computed(() => stations.value.filter((item) => item.enabled !== false).length)
const latestPowerKw = computed(() => formatNumber((latestStatus.value?.powerGenerationW ?? 0) / 1000, 1))
const latestEnergyKwh = computed(() => formatNumber((latestStatus.value?.energyGenerationWh ?? 0) / 1000, 1))

onMounted(async () => {
  window.addEventListener('resize', resizeChart)
  await fetchStations()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

async function fetchStations() {
  loading.value = true
  try {
    stations.value = await loadPvOutputStations({ enabled: true })
    selectedStationId.value = stations.value[0]?.id
    if (selectedStationId.value) {
      await fetchStatus()
    } else {
      clearStatus()
      ElMessage.warning('暂无已接入的公开电站，请先搜索并添加')
    }
  } catch (error) {
    clearStatus()
    ElMessage.error(message(error, '公开电站加载失败'))
  } finally {
    loading.value = false
  }
}

async function fetchStatus() {
  const station = selectedStation.value
  if (!station?.id) return
  statusLoading.value = true
  try {
    const end = new Date()
    const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000)
    const [latest, history] = await Promise.all([
      loadPvOutputLatestStatus(station.id),
      loadPvOutputHistory(station.id, {
        startTime: formatLocalDateTime(start),
        endTime: formatLocalDateTime(end)
      })
    ])
    latestStatus.value = latest
    historyRows.value = history
    await fetchWeather()
    await nextTick()
    renderChart()
  } catch (error) {
    clearStatus()
    ElMessage.error(message(error, '公开电站状态加载失败'))
  } finally {
    statusLoading.value = false
  }
}

async function syncSelectedStation() {
  const station = selectedStation.value
  if (!station?.id) {
    ElMessage.warning('请先选择公开电站')
    return
  }
  syncLoading.value = true
  try {
    const result = await syncPvOutputStation(station.id)
    if (result.status === 'SUCCESS') {
      ElMessage.success('公开电站同步成功')
    } else {
      ElMessage.warning(result.message || '同步完成，但未返回成功状态')
    }
    await fetchStations()
    selectedStationId.value = station.id
    await fetchStatus()
  } catch (error) {
    ElMessage.error(message(error, '公开电站同步失败'))
  } finally {
    syncLoading.value = false
  }
}

function clearStatus() {
  latestStatus.value = null
  historyRows.value = []
  weather.value = null
  forecasts.value = []
  weatherError.value = ''
  renderChart()
}

async function fetchWeather() {
  const station = selectedStation.value
  const query = weatherQuery(station)
  weatherError.value = ''

  if (!query) {
    weather.value = null
    forecasts.value = []
    weatherError.value = '当前公开电站没有经纬度或可解析位置，无法获取天气'
    return
  }

  const [currentResult, forecastResult] = await Promise.allSettled([
    getLocationCurrentWeather(query),
    getLocationForecast(query)
  ])

  weather.value = currentResult.status === 'fulfilled' ? currentResult.value : null
  forecasts.value = forecastResult.status === 'fulfilled' ? forecastResult.value.slice(0, 3) : []

  if (currentResult.status === 'rejected') {
    weatherError.value = message(currentResult.reason, '天气实况加载失败')
  } else if (forecastResult.status === 'rejected') {
    weatherError.value = message(forecastResult.reason, '天气预报加载失败')
  }
}

function weatherQuery(station?: PvOutputStation): WeatherLocationQuery | null {
  if (!station) return null
  if (station.longitude != null && station.latitude != null) {
    return { longitude: station.longitude, latitude: station.latitude }
  }
  const location = station.postcode || station.systemName
  return location ? { location } : null
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart ?? echarts.init(chartRef.value)
  chart.setOption(
    {
      color: ['#1d6fdc', '#22a06b', '#f59e0b', '#7c3aed'],
      tooltip: { trigger: 'axis' },
      legend: {
        top: 4,
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { color: '#40516b' }
      },
      grid: { left: 46, right: 24, top: 58, bottom: 42, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: historyRows.value.map((item) => item.sampleTime),
        axisLabel: { color: '#66758c', hideOverlap: true },
        axisLine: { lineStyle: { color: '#d8e3f0' } }
      },
      yAxis: [
        {
          type: 'value',
          name: '功率 W',
          axisLabel: { color: '#66758c' },
          splitLine: { lineStyle: { color: '#edf3f9' } }
        },
        {
          type: 'value',
          name: '温度/电压',
          axisLabel: { color: '#66758c' },
          splitLine: { show: false }
        }
      ],
      series: [
        {
          name: '发电功率',
          type: 'line',
          smooth: true,
          symbol: 'none',
          areaStyle: { opacity: 0.12 },
          data: historyRows.value.map((item) => item.powerGenerationW ?? null)
        },
        {
          name: '用电功率',
          type: 'line',
          smooth: true,
          symbol: 'none',
          data: historyRows.value.map((item) => item.powerConsumptionW ?? null)
        },
        {
          name: '温度',
          type: 'line',
          smooth: true,
          symbol: 'none',
          yAxisIndex: 1,
          data: historyRows.value.map((item) => item.temperatureC ?? null)
        },
        {
          name: '电压',
          type: 'line',
          smooth: true,
          symbol: 'none',
          yAxisIndex: 1,
          data: historyRows.value.map((item) => item.voltageV ?? null)
        }
      ]
    },
    true
  )
}

function resizeChart() {
  chart?.resize()
}

function formatLocalDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(
    date.getMinutes()
  )}:${pad(date.getSeconds())}`
}

function formatNumber(value: number, digits: number) {
  return Number.isFinite(value) ? value.toFixed(digits) : '-'
}

function formatWatts(value?: number) {
  if (value == null) return '-'
  if (Math.abs(value) >= 1000) return `${(value / 1000).toFixed(2)} kW`
  return `${value.toFixed(0)} W`
}

function formatWh(value?: number) {
  if (value == null) return '-'
  if (Math.abs(value) >= 1000) return `${(value / 1000).toFixed(2)} kWh`
  return `${value.toFixed(0)} Wh`
}

function formatCapacity(value?: number) {
  if (value == null) return '-'
  return `${(value / 1000).toFixed(2)} kW`
}

function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
</script>

<template>
  <section class="page-shell dashboard-page" v-loading="loading">
    <section class="page-section station-header">
      <div class="station-title">
        <p class="page-kicker">公开电站看板</p>
        <div class="station-name-row">
          <h2>{{ selectedStation?.systemName || '请选择公开电站' }}</h2>
          <el-tag :type="selectedStation?.enabled === false ? 'info' : 'success'" effect="light">
            {{ selectedStation?.enabled === false ? '未启用' : '已接入' }}
          </el-tag>
        </div>
        <p class="station-desc">
          System ID {{ selectedStation?.externalSystemId || '-' }} · {{ selectedStation?.postcode || '未知区域' }}
        </p>
      </div>

      <div class="station-switch">
        <span>切换电站</span>
        <el-select v-model="selectedStationId" class="station-select" size="large" filterable @change="fetchStatus">
          <el-option
            v-for="station in stations"
            :key="station.id"
            :label="station.systemName || `System ${station.externalSystemId}`"
            :value="station.id"
          />
        </el-select>
        <el-button :icon="Refresh" size="large" @click="fetchStations">刷新</el-button>
        <el-button :icon="Timer" size="large" type="primary" :loading="syncLoading" @click="syncSelectedStation">
          同步
        </el-button>
      </div>

      <div class="station-info-grid">
        <div class="station-info-item wide">
          <el-icon><Location /></el-icon>
          <div>
            <span>位置</span>
            <strong>{{ selectedStation?.postcode || '-' }}</strong>
          </div>
        </div>
        <div class="station-info-item">
          <span>装机容量</span>
          <strong>{{ formatCapacity(selectedStation?.systemSizeW) }}</strong>
        </div>
        <div class="station-info-item">
          <span>经纬度</span>
          <strong>{{ selectedStation?.longitude ?? '-' }}, {{ selectedStation?.latitude ?? '-' }}</strong>
        </div>
        <div class="station-info-item">
          <span>组件 / 逆变器</span>
          <strong>{{ selectedStation?.panel || '-' }} / {{ selectedStation?.inverter || '-' }}</strong>
        </div>
        <div class="station-info-item">
          <span>最近同步</span>
          <strong>{{ selectedStation?.lastSyncTime || selectedStation?.lastOutputText || '-' }}</strong>
        </div>
      </div>
    </section>

    <div class="dashboard-main-grid">
      <section class="page-section realtime-panel" v-loading="statusLoading">
        <div class="panel-head">
          <div>
            <p class="page-kicker">实时图</p>
            <h3>公开电站功率曲线</h3>
          </div>
          <div v-if="latestStatus" class="collect-time">
            <el-icon><Timer /></el-icon>
            <span>{{ latestStatus.sampleTime }}</span>
          </div>
        </div>

        <div class="summary-grid">
          <div class="summary-card">
            <span>已接入电站</span>
            <strong>{{ stations.length }}</strong>
            <small>{{ enabledCount }} 个启用</small>
          </div>
          <div class="summary-card">
            <span>合计装机</span>
            <strong>{{ totalCapacityKw.toFixed(1) }} kW</strong>
            <small>当前公开样本</small>
          </div>
          <div class="summary-card">
            <span>当前发电功率</span>
            <strong>{{ latestPowerKw }} kW</strong>
            <small>{{ selectedStation?.systemName || '未选择电站' }}</small>
          </div>
          <div class="summary-card">
            <span>当日发电量</span>
            <strong>{{ latestEnergyKwh }} kWh</strong>
            <small>{{ latestStatus?.sampleTime || '暂无同步数据' }}</small>
          </div>
        </div>

        <div ref="chartRef" class="realtime-chart" />
        <el-empty v-if="!statusLoading && selectedStation && !historyRows.length" description="暂无历史状态数据，请先同步电站" />
      </section>

      <aside class="weather-card">
        <div class="weather-head">
          <h3>天气实况</h3>
          <div v-if="selectedStation" class="weather-location">
            <el-icon><Location /></el-icon>
            <span>{{ selectedStation.postcode || '-' }}</span>
          </div>
        </div>

        <el-alert
          v-if="weatherError"
          class="weather-error"
          :title="weatherError"
          type="warning"
          show-icon
          :closable="false"
        />

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
        <el-empty v-else :description="weatherError || '暂无天气数据'" />
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

.station-name-row h2,
.panel-head h3,
.weather-head h3 {
  margin: 0;
  color: #10274c;
}

.station-name-row h2 {
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
  width: 280px;
}

.station-info-grid {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: minmax(260px, 1.5fr) repeat(4, minmax(150px, 1fr));
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

.station-info-item span,
.summary-card span,
.summary-card small,
.weather-tile span {
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

.panel-head,
.weather-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
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

.collect-time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-muted);
  font-size: 13px;
  white-space: nowrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.summary-card {
  min-height: 96px;
  padding: 14px;
  border: 1px solid #e2edf7;
  border-radius: 8px;
  background: #fbfdff;
  display: grid;
  gap: 8px;
}

.summary-card strong {
  color: #10274c;
  font-size: 22px;
  line-height: 1.1;
}

.realtime-chart {
  width: 100%;
  height: 380px;
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
}

.weather-error {
  margin-bottom: 12px;
}

.weather-location {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #4f7891;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
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

  .station-info-grid,
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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
  .summary-grid {
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
