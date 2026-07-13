<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { loadPvOutputHistory, loadPvOutputLatestStatus, loadPvOutputStations, getPvOutputCurrentWeather, getPvOutputForecast } from '../api/pvoutput'
import type { PvOutputStation, PvOutputStatus } from '../api/pvoutput'
import type { CurrentWeather, WeatherForecastItem } from '../api/weather'

const loading = ref(false)
const statusLoading = ref(false)
const stations = ref<PvOutputStation[]>([])
const selectedStationId = ref<number>()
const latestStatus = ref<PvOutputStatus | null>(null)
const historyRows = ref<PvOutputStatus[]>([])
const chartRef = ref<HTMLDivElement | null>(null)
const weather = ref<CurrentWeather | null>(null)
const forecasts = ref<WeatherForecastItem[]>([])
const weatherError = ref('')
let chart: echarts.ECharts | null = null

const selectedStation = computed(() => stations.value.find((item) => item.id === selectedStationId.value) ?? stations.value[0])
const selectedStationHasCoordinates = computed(() =>
  selectedStation.value?.longitude != null && selectedStation.value?.latitude != null
)

const totalCapacityKw = computed(() => stations.value.reduce((sum, item) => sum + (item.systemSizeW ?? 0), 0) / 1000)
const onlineCount = computed(() => stations.value.filter((item) => item.enabled).length)
const latestPowerKw = computed(() => ((latestStatus.value?.powerGenerationW ?? 0) / 1000).toFixed(1))
const latestEnergyKwh = computed(() => ((latestStatus.value?.energyGenerationWh ?? 0) / 1000).toFixed(1))

onMounted(async () => {
  await fetchStations()
  window.addEventListener('resize', resizeChart)
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
    if (selectedStationId.value) await fetchStatus()
  } catch (error) {
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
    weather.value = null
    forecasts.value = []
    weatherError.value = ''
    const [latest, history] = await Promise.allSettled([
      loadPvOutputLatestStatus(station.id),
      loadPvOutputHistory(station.id, {
        startTime: formatLocalDateTime(start),
        endTime: formatLocalDateTime(end)
      })
    ])
    latestStatus.value = latest.status === 'fulfilled' ? latest.value : null
    historyRows.value = history.status === 'fulfilled' ? history.value : []
    if (selectedStationHasCoordinates.value) {
      const [weatherResult, forecastResult] = await Promise.allSettled([
        getPvOutputCurrentWeather(station.id),
        getPvOutputForecast(station.id)
      ])
      weather.value = weatherResult.status === 'fulfilled' ? weatherResult.value : null
      forecasts.value = forecastResult.status === 'fulfilled' ? forecastResult.value : []
      if (weatherResult.status === 'rejected') {
        weatherError.value = message(weatherResult.reason, '天气数据加载失败')
      }
    } else {
      weatherError.value = '该公开电站未配置经纬度，无法获取真实天气。'
    }
    await nextTick()
    renderChart()
  } catch (error) {
    ElMessage.error(message(error, '状态数据加载失败'))
  } finally {
    statusLoading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart ?? echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 12 },
    grid: { left: 54, right: 24, top: 38, bottom: 42 },
    xAxis: {
      type: 'category',
      data: historyRows.value.map((item) => item.sampleTime),
      axisLabel: { hideOverlap: true }
    },
    yAxis: { type: 'value', name: 'W' },
    series: [
      {
        name: '发电功率',
        type: 'line',
        smooth: true,
        symbol: 'none',
        areaStyle: { opacity: 0.12 },
        data: historyRows.value.map((item) => item.powerGenerationW ?? null)
      }
    ]
  })
}

function resizeChart() {
  chart?.resize()
}

function formatLocalDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatKw(value?: number) {
  if (!value) return '-'
  return `${(value / 1000).toFixed(2)} kW`
}

function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
</script>

<template>
  <div class="page-shell public-pvoutput">
    <div class="page-title">
      <div>
        <p class="page-kicker">PVOutput</p>
        <h2>公开电站展示</h2>
        <p>后端定时拉取 PVOutput API，入库后由本页面读取数据库展示。</p>
      </div>
      <el-button :loading="loading" @click="fetchStations">刷新列表</el-button>
    </div>

    <section class="summary-grid">
      <div class="summary-card">
        <span>展示电站</span>
        <strong>{{ stations.length }}</strong>
        <small>{{ onlineCount }} 个启用同步</small>
      </div>
      <div class="summary-card">
        <span>合计装机</span>
        <strong>{{ totalCapacityKw.toFixed(1) }} kW</strong>
        <small>公共电站样本</small>
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
    </section>

    <section class="page-section station-browser">
      <div class="station-list" v-loading="loading">
        <button
          v-for="station in stations"
          :key="station.id"
          class="station-row"
          :class="{ active: station.id === selectedStation?.id }"
          type="button"
          @click="selectedStationId = station.id; fetchStatus()"
        >
          <span>{{ station.systemName }}</span>
          <strong>{{ formatKw(station.systemSizeW) }}</strong>
          <small>{{ station.postcode || '-' }} · System ID {{ station.externalSystemId }}</small>
        </button>
        <el-empty v-if="!loading && !stations.length" description="暂无公开电站，请先在管理端添加或等待默认种子初始化" />
      </div>

      <div class="status-panel" v-loading="statusLoading">
        <div class="section-header">
          <div>
            <p class="page-kicker">{{ selectedStation?.postcode || '-' }}</p>
            <h3>{{ selectedStation?.systemName || '选择电站' }}</h3>
          </div>
          <el-tag :type="selectedStation?.lastSyncStatus === 'SUCCESS' ? 'success' : 'info'">
            {{ selectedStation?.lastSyncStatus || '未同步' }}
          </el-tag>
        </div>

        <el-descriptions v-if="latestStatus" :column="2" border>
          <el-descriptions-item label="采样时间">{{ latestStatus.sampleTime }}</el-descriptions-item>
          <el-descriptions-item label="发电功率">{{ latestStatus.powerGenerationW ?? '-' }} W</el-descriptions-item>
          <el-descriptions-item label="发电量">{{ latestStatus.energyGenerationWh ?? '-' }} Wh</el-descriptions-item>
          <el-descriptions-item label="归一化输出">{{ latestStatus.normalisedOutput ?? '-' }} kWh/kW</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="暂无入库状态数据，配置 API Key 后可由定时任务或管理端手动同步" />

        <el-alert v-if="weatherError" class="weather-alert" :title="weatherError" type="warning" show-icon :closable="false" />
        <div v-if="weather" class="weather-mini">
          <div class="weather-mini-header">
            <span>🌤 当地天气</span>
            <small>{{ weather.source }} · {{ weather.cached ? '缓存' : '实时' }}</small>
          </div>
          <div class="weather-mini-main">
            <strong>{{ weather.weather }}</strong>
            <em>{{ weather.temperature }}°C</em>
          </div>
          <div class="weather-mini-meta">
            <span>湿度 {{ weather.humidity }}%</span>
            <span>{{ weather.windDirection }} {{ weather.windPower }}</span>
            <span>风速 {{ weather.windSpeed }} m/s</span>
          </div>
          <div v-if="forecasts.length" class="weather-mini-forecast">
            <div v-for="item in forecasts.slice(0, 3)" :key="item.date" class="forecast-chip">
              <small>{{ item.date.slice(5) }}</small>
              <strong>{{ item.dayWeather }}</strong>
              <em>{{ item.nightTemp }}-{{ item.dayTemp }}°C</em>
            </div>
          </div>
        </div>

        <div ref="chartRef" class="power-chart" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.public-pvoutput {
  gap: 18px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  min-height: 112px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
  display: grid;
  gap: 8px;
}

.summary-card span,
.summary-card small {
  color: var(--color-muted);
}

.summary-card strong {
  font-size: 24px;
  color: #14233f;
}

.station-browser {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
}

.station-list {
  display: grid;
  align-content: start;
  gap: 10px;
  max-height: 640px;
  overflow: auto;
}

.station-row {
  width: 100%;
  min-height: 86px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f8fbff;
  color: #172033;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 5px;
}

.station-row.active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.station-row span {
  font-weight: 700;
}

.station-row strong {
  color: var(--color-primary);
}

.station-row small {
  color: var(--color-muted);
}

.status-panel {
  min-width: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  font-size: 20px;
}

.power-chart {
  width: 100%;
  height: 360px;
  margin-top: 18px;
}

.weather-alert {
  margin-top: 16px;
}

.weather-mini {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f7ff 100%);
}

.weather-mini-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.weather-mini-header span {
  font-weight: 600;
  color: #10274c;
}

.weather-mini-header small {
  color: var(--color-muted);
}

.weather-mini-main {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 12px;
}

.weather-mini-main strong {
  font-size: 24px;
  color: #10274c;
}

.weather-mini-main em {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
  font-style: normal;
}

.weather-mini-meta {
  display: flex;
  gap: 18px;
  color: var(--color-muted);
  font-size: 13px;
  margin-bottom: 12px;
}

.weather-mini-forecast {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.forecast-chip {
  display: grid;
  gap: 2px;
  padding: 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.72);
  text-align: center;
}

.forecast-chip small {
  color: var(--color-muted);
  font-size: 12px;
}

.forecast-chip strong {
  font-size: 14px;
  color: #10274c;
}

.forecast-chip em {
  font-size: 12px;
  color: var(--color-muted);
  font-style: normal;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .station-browser {
    grid-template-columns: 1fr;
  }
}
</style>
