<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCurrentWeather, getForecast } from '../api/weather'
import { getStations, type Station } from '../api/station'
import type { CurrentWeather, WeatherForecastItem } from '../api/weather'

const loading = ref(false)
const weatherLoading = ref(false)
const stations = ref<Station[]>([])
const selectedStationId = ref<number>()
const current = ref<CurrentWeather>()
const forecasts = ref<WeatherForecastItem[]>([])
const loadError = ref('')

const selectedStation = computed(() =>
  stations.value.find((item) => item.stationId === selectedStationId.value)
)
const selectedStationHasCoordinates = computed(() =>
  selectedStation.value?.longitude != null && selectedStation.value?.latitude != null
)

const weatherTone = computed(() => {
  const text = current.value?.weather ?? ''
  if (text.includes('雨')) return 'rainy'
  if (text.includes('阴')) return 'overcast'
  if (text.includes('云')) return 'cloudy'
  return 'sunny'
})

const dataBadgeType = computed(() => current.value?.cached ? 'warning' : 'success')

onMounted(async () => {
  await loadStationOptions()
})

async function loadStationOptions() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await getStations({ pageNum: 1, pageSize: 50 })
    stations.value = result.records ?? []
    selectedStationId.value = stations.value[0]?.stationId
    if (selectedStationId.value) {
      await refreshWeather()
    } else {
      loadError.value = '暂无可用电站，请先创建或分配电站。'
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '电站列表加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshWeather() {
  if (!selectedStationId.value) return
  if (!selectedStationHasCoordinates.value) {
    current.value = undefined
    forecasts.value = []
    loadError.value = '该电站未配置经纬度，无法获取真实天气。请先在电站管理中补充经纬度。'
    return
  }
  weatherLoading.value = true
  loadError.value = ''
  try {
    const [currentResult, forecastResult] = await Promise.all([
      getCurrentWeather(selectedStationId.value),
      getForecast(selectedStationId.value)
    ])
    current.value = currentResult
    forecasts.value = forecastResult
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '天气数据加载失败'
    ElMessage.error(loadError.value)
  } finally {
    weatherLoading.value = false
  }
}

function onStationChange() {
  void refreshWeather()
}

function formatNumber(value?: number, suffix = '') {
  if (value === undefined || value === null || Number.isNaN(value)) return '-'
  return `${value}${suffix}`
}
</script>

<template>
  <section class="weather-page">
    <div class="page-heading">
      <div>
        <h1>天气</h1>
        <p>按电站经纬度读取和风天气实时天气与未来预报</p>
      </div>
      <div class="heading-actions">
        <el-select
          v-model="selectedStationId"
          placeholder="选择电站"
          :loading="loading"
          filterable
          @change="onStationChange"
        >
          <el-option
            v-for="station in stations"
            :key="station.stationId"
            :label="station.longitude != null && station.latitude != null ? station.stationName : `${station.stationName}（缺经纬度）`"
            :value="station.stationId"
          />
        </el-select>
        <el-button type="primary" :loading="weatherLoading" @click="refreshWeather">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />

    <div v-loading="loading || weatherLoading" class="weather-content">
      <section v-if="current" class="weather-hero" :class="weatherTone">
        <div>
          <p class="page-kicker">{{ selectedStation?.city || selectedStation?.address || '电站天气' }}</p>
          <h2>{{ current.weather }}</h2>
          <p>{{ selectedStation?.stationName }} · {{ current.reportTime }}</p>
        </div>
        <div class="temperature-block">
          <strong>{{ formatNumber(current.temperature, '°C') }}</strong>
          <el-tag :type="dataBadgeType" effect="dark">
            {{ current.source }} · {{ current.cached ? '缓存' : '实时' }}
          </el-tag>
        </div>
      </section>

      <section v-if="current" class="metric-grid">
        <div>
          <span>湿度</span>
          <strong>{{ formatNumber(current.humidity, '%') }}</strong>
        </div>
        <div>
          <span>风向</span>
          <strong>{{ current.windDirection || '-' }}</strong>
        </div>
        <div>
          <span>风力</span>
          <strong>{{ current.windPower || '-' }}</strong>
        </div>
        <div>
          <span>风速</span>
          <strong>{{ formatNumber(current.windSpeed, ' m/s') }}</strong>
        </div>
      </section>

      <section class="forecast-section">
        <div class="section-head">
          <h2>未来预报</h2>
          <span>{{ forecasts.length }} 天</span>
        </div>
        <el-empty v-if="!forecasts.length" description="暂无预报数据" />
        <div v-else class="forecast-grid">
          <article v-for="item in forecasts" :key="item.date" class="forecast-card">
            <span>{{ item.date }}</span>
            <strong>{{ item.dayWeather }} / {{ item.nightWeather }}</strong>
            <p>{{ item.nightTemp }}°C - {{ item.dayTemp }}°C</p>
            <small>湿度 {{ item.humidity }}% · {{ item.windDirection }} {{ item.windPower }}</small>
            <el-tag size="small" :type="item.cached ? 'warning' : 'success'" effect="light">
              {{ item.source }} {{ item.cached ? '缓存' : '实时' }}
            </el-tag>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.weather-page,
.weather-content {
  display: grid;
  gap: 18px;
}

.page-heading,
.heading-actions,
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.page-heading h1,
.section-head h2,
.weather-hero h2 {
  margin: 0;
  color: #10274c;
}

.page-heading h1 {
  font-size: 28px;
}

.page-heading p,
.weather-hero p,
.forecast-card p,
.forecast-card small,
.section-head span {
  margin: 6px 0 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.heading-actions .el-select {
  width: 260px;
}

.weather-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 220px;
  padding: 26px;
  border-radius: 8px;
  color: #10274c;
  background: linear-gradient(135deg, #e9f8ef, #f7fbff);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-panel);
}

.weather-hero.sunny {
  background: linear-gradient(135deg, #fff4c7, #e9f8ef);
}

.weather-hero.cloudy {
  background: linear-gradient(135deg, #eef4f8, #e8f7f2);
}

.weather-hero.rainy,
.weather-hero.overcast {
  background: linear-gradient(135deg, #e9eef6, #eef8fb);
}

.page-kicker {
  color: var(--color-primary);
  font-weight: 700;
}

.weather-hero h2 {
  margin-top: 8px;
  font-size: 42px;
}

.temperature-block {
  display: grid;
  justify-items: end;
  gap: 12px;
}

.temperature-block strong {
  color: #10274c;
  font-size: 56px;
  line-height: 1;
}

.metric-grid,
.forecast-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-grid div,
.forecast-card,
.forecast-section {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.metric-grid div {
  padding: 18px;
}

.metric-grid span,
.forecast-card span {
  color: var(--color-muted);
  font-size: 13px;
}

.metric-grid strong,
.forecast-card strong {
  display: block;
  margin-top: 8px;
  color: #10274c;
  font-size: 22px;
}

.forecast-section {
  padding: 18px;
}

.forecast-grid {
  margin-top: 14px;
}

.forecast-card {
  display: grid;
  gap: 8px;
  padding: 16px;
}

@media (max-width: 980px) {
  .metric-grid,
  .forecast-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-heading,
  .heading-actions,
  .section-head,
  .weather-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .heading-actions,
  .heading-actions .el-select,
  .metric-grid,
  .forecast-grid {
    width: 100%;
    grid-template-columns: 1fr;
  }

  .temperature-block {
    justify-items: start;
  }
}
</style>
