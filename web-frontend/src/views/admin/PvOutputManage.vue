<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  loadPvOutputHistory,
  loadPvOutputLatestStatus,
  loadPvOutputStations,
  savePvOutputStation,
  searchPvOutputStations,
  setPvOutputStationEnabled,
  syncAllPvOutputStations,
  syncPvOutputLiveStations,
  syncPvOutputStation
} from '../../api/pvoutput'
import type { PvOutputStation, PvOutputStatus } from '../../api/pvoutput'

const searchForm = reactive({ keyword: 'Enphase', countryCode: 'au', seenDays: 7 })
const listQuery = reactive({ keyword: '', enabled: undefined as boolean | undefined })
const searchLoading = ref(false)
const stationLoading = ref(false)
const syncAllLoading = ref(false)
const syncLiveLoading = ref(false)
const actionLoadingId = ref<number | null>(null)
const searchResults = ref<PvOutputStation[]>([])
const stations = ref<PvOutputStation[]>([])
const latestStatus = ref<PvOutputStatus | null>(null)
const historyRows = ref<PvOutputStatus[]>([])
const selectedStation = ref<PvOutputStation | null>(null)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

onMounted(() => {
  void fetchStations()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

async function fetchSearch() {
  searchLoading.value = true
  try {
    searchResults.value = await searchPvOutputStations(searchForm)
    if (!searchResults.value.length) ElMessage.info('没有搜索到公开电站')
  } catch (error) {
    ElMessage.error(message(error, '搜索失败'))
  } finally {
    searchLoading.value = false
  }
}

async function addStation(row: PvOutputStation) {
  actionLoadingId.value = row.externalSystemId
  try {
    await savePvOutputStation(row)
    ElMessage.success('已添加公开电站')
    await fetchStations()
  } catch (error) {
    ElMessage.error(message(error, '添加失败'))
  } finally {
    actionLoadingId.value = null
  }
}

async function fetchStations() {
  stationLoading.value = true
  try {
    stations.value = await loadPvOutputStations({
      keyword: listQuery.keyword || undefined,
      enabled: listQuery.enabled
    })
  } catch (error) {
    ElMessage.error(message(error, '电站列表加载失败'))
  } finally {
    stationLoading.value = false
  }
}

async function toggleEnabled(row: PvOutputStation, enabled: boolean) {
  if (!row.id) return
  actionLoadingId.value = row.id
  try {
    await setPvOutputStationEnabled(row.id, enabled)
    ElMessage.success(enabled ? '已启用' : '已禁用')
    await fetchStations()
  } catch (error) {
    ElMessage.error(message(error, '状态更新失败'))
  } finally {
    actionLoadingId.value = null
  }
}

async function syncOne(row: PvOutputStation) {
  if (!row.id) return
  actionLoadingId.value = row.id
  try {
    const result = await syncPvOutputStation(row.id)
    if (result.status === 'SUCCESS') ElMessage.success('同步成功')
    else ElMessage.error(result.message || '同步失败')
    await fetchStations()
    await viewStatus(row)
  } catch (error) {
    ElMessage.error(message(error, '同步失败'))
  } finally {
    actionLoadingId.value = null
  }
}

async function syncAll() {
  syncAllLoading.value = true
  try {
    const results = await syncAllPvOutputStations()
    const failed = results.filter((item) => item.status !== 'SUCCESS')
    ElMessage[failed.length ? 'warning' : 'success'](`同步完成：${results.length - failed.length} 成功，${failed.length} 失败`)
    await fetchStations()
  } catch (error) {
    ElMessage.error(message(error, '批量同步失败'))
  } finally {
    syncAllLoading.value = false
  }
}

async function syncLive() {
  syncLiveLoading.value = true
  try {
    const results = await syncPvOutputLiveStations()
    ElMessage.success(`公开实时页同步完成：${results.length} 个电站`)
    await fetchStations()
  } catch (error) {
    ElMessage.error(message(error, '公开实时页同步失败'))
  } finally {
    syncLiveLoading.value = false
  }
}

async function viewStatus(row: PvOutputStation) {
  if (!row.id) return
  selectedStation.value = row
  try {
    const end = new Date()
    const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000)
    latestStatus.value = await loadPvOutputLatestStatus(row.id)
    historyRows.value = await loadPvOutputHistory(row.id, {
      startTime: start.toISOString().slice(0, 19),
      endTime: end.toISOString().slice(0, 19)
    })
    await nextTick()
    renderChart()
  } catch (error) {
    ElMessage.error(message(error, '状态加载失败'))
  }
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart ?? echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 28, bottom: 36 },
    xAxis: { type: 'category', data: historyRows.value.map((item) => item.sampleTime) },
    yAxis: { type: 'value', name: 'W' },
    series: [
      {
        name: '发电功率',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: historyRows.value.map((item) => item.powerGenerationW ?? null)
      }
    ]
  })
}

function resizeChart() {
  chart?.resize()
}

function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
</script>

<template>
  <div class="pvoutput-page">
    <section class="page-section">
      <div class="section-header">
        <div>
          <p class="page-kicker">PVOutput</p>
          <h2>公开电站搜索</h2>
        </div>
      </div>
      <el-form class="toolbar" inline :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" clearable placeholder="Enphase" />
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="searchForm.countryCode" class="short-input" />
        </el-form-item>
        <el-form-item label="最近天数">
          <el-input-number v-model="searchForm.seenDays" :min="1" :max="30" />
        </el-form-item>
        <el-button type="primary" :loading="searchLoading" @click="fetchSearch">搜索</el-button>
      </el-form>
      <el-table v-loading="searchLoading" :data="searchResults" empty-text="暂无搜索结果" border>
        <el-table-column prop="systemName" label="名称" min-width="160" />
        <el-table-column prop="systemSizeW" label="容量W" width="100" />
        <el-table-column prop="postcode" label="邮编" width="100" />
        <el-table-column prop="orientation" label="朝向" width="90" />
        <el-table-column prop="outputs" label="输出" width="90" />
        <el-table-column prop="lastOutputText" label="最近输出" width="130" />
        <el-table-column prop="externalSystemId" label="System ID" width="120" />
        <el-table-column prop="panel" label="组件" min-width="160" />
        <el-table-column prop="inverter" label="逆变器" min-width="160" />
        <el-table-column prop="distanceKm" label="距离km" width="100" />
        <el-table-column prop="latitude" label="纬度" width="110" />
        <el-table-column prop="longitude" label="经度" width="110" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :loading="actionLoadingId === row.externalSystemId" @click="addStation(row)">添加</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="page-section">
      <div class="section-header">
        <div>
          <p class="page-kicker">已添加</p>
          <h2>公开电站列表</h2>
        </div>
        <div class="header-actions">
          <el-button :loading="syncLiveLoading" @click="syncLive">同步公开实时页</el-button>
          <el-button type="primary" :loading="syncAllLoading" @click="syncAll">同步全部</el-button>
        </div>
      </div>
      <el-form class="toolbar" inline :model="listQuery">
        <el-form-item label="关键词">
          <el-input v-model="listQuery.keyword" clearable placeholder="名称/邮编/设备" @keyup.enter="fetchStations" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="listQuery.enabled" clearable class="short-input">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-button :loading="stationLoading" @click="fetchStations">查询</el-button>
      </el-form>
      <el-table v-loading="stationLoading" :data="stations" empty-text="暂无已添加公开电站" border>
        <el-table-column prop="systemName" label="名称" min-width="150" />
        <el-table-column prop="externalSystemId" label="System ID" width="120" />
        <el-table-column prop="systemSizeW" label="容量W" width="100" />
        <el-table-column prop="postcode" label="邮编" width="100" />
        <el-table-column prop="orientation" label="朝向" width="90" />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastSyncTime" label="最近同步" width="170" />
        <el-table-column prop="lastSyncStatus" label="同步状态" width="110" />
        <el-table-column prop="lastSyncError" label="错误" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :loading="actionLoadingId === row.id" @click="syncOne(row)">同步</el-button>
            <el-button size="small" type="primary" @click="viewStatus(row)">状态</el-button>
            <el-button size="small" :type="row.enabled ? 'warning' : 'success'" @click="toggleEnabled(row, !row.enabled)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="page-section">
      <div class="section-header">
        <div>
          <p class="page-kicker">{{ selectedStation?.systemName || '未选择电站' }}</p>
          <h2>最新状态与历史功率</h2>
        </div>
      </div>
      <el-descriptions v-if="latestStatus" :column="4" border>
        <el-descriptions-item label="采样时间">{{ latestStatus.sampleTime }}</el-descriptions-item>
        <el-descriptions-item label="发电量Wh">{{ latestStatus.energyGenerationWh ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="发电功率W">{{ latestStatus.powerGenerationW ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="用电量Wh">{{ latestStatus.energyConsumptionWh ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="用电功率W">{{ latestStatus.powerConsumptionW ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="归一化输出">{{ latestStatus.normalisedOutput ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="温度C">{{ latestStatus.temperatureC ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="电压V">{{ latestStatus.voltageV ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="选择电站后查看最新状态" />
      <div ref="chartRef" class="history-chart" />
    </section>
  </div>
</template>

<style scoped>
.pvoutput-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-header h2 {
  margin: 0;
  font-size: 20px;
}

.toolbar {
  margin-bottom: 14px;
}

.short-input {
  width: 120px;
}

.history-chart {
  width: 100%;
  height: 320px;
  margin-top: 18px;
}
</style>
