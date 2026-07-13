<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Refresh, Sunny } from '@element-plus/icons-vue'
import { getHistory, getStations, type Station } from '../api/station'
import type { PvHistoryItem } from '../api/pvData'

const stations = ref<Station[]>([])
const selectedStationId = ref<number>()
const rows = ref<PvHistoryItem[]>([])
const loading = ref(false)
const error = ref('')
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

const selectedStation = computed(() => stations.value.find((item) => item.stationId === selectedStationId.value))
const peakPower = computed(() => rows.value.length ? Math.max(...rows.value.map((item) => item.power || 0)) : 0)
const totalPower = computed(() => rows.value.reduce((sum, item) => sum + (item.power || 0), 0))
const averageIrradiance = computed(() => rows.value.length ? rows.value.reduce((sum, item) => sum + (item.irradiance || 0), 0) / rows.value.length : 0)

onMounted(async () => { await loadStations(); window.addEventListener('resize', resizeChart) })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeChart); chart?.dispose() })
watch(selectedStationId, () => { if (selectedStationId.value) void loadHistory() })

async function loadStations() {
  loading.value = true
  try {
    const page = await getStations({ pageNum: 1, pageSize: 100 })
    stations.value = page.records
    selectedStationId.value = stations.value[0]?.stationId
    if (!selectedStationId.value) error.value = '暂无可查看的电站，请先创建电站或导入数据。'
  } catch (e) { error.value = e instanceof Error ? e.message : '电站列表加载失败' }
  finally { loading.value = false }
}

async function loadHistory() {
  if (!selectedStationId.value) return
  loading.value = true; error.value = ''
  const end = new Date(); const start = new Date(end.getTime() - 24 * 60 * 60 * 1000)
  try {
    rows.value = await getHistory(selectedStationId.value, { startTime: formatTime(start), endTime: formatTime(end), interval: '15min' })
    await nextTick(); renderChart()
  } catch (e) { rows.value = []; error.value = e instanceof Error ? e.message : '历史数据加载失败' }
  finally { loading.value = false }
}

function formatTime(value: Date) { return value.toISOString().slice(0, 19).replace('T', ' ') }
function timeLabel(value: string) { return value.length >= 16 ? value.slice(5, 16) : value }
function formatNumber(value: number, digits = 1) { return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: digits }).format(value) }
function resizeChart() { chart?.resize() }
function renderChart() {
  if (!chartRef.value) return
  chart?.dispose(); chart = echarts.init(chartRef.value)
  chart.setOption({
    animationDuration: 450,
    grid: { left: 54, right: 54, top: 34, bottom: 46 },
    tooltip: { trigger: 'axis', backgroundColor: '#12352f', borderWidth: 0, textStyle: { color: '#fff' } },
    legend: { top: 0, right: 4, textStyle: { color: '#60766f' } },
    xAxis: { type: 'category', data: rows.value.map((item) => timeLabel(item.time)), axisLine: { lineStyle: { color: '#d9e7e1' } }, axisLabel: { color: '#668078' } },
    yAxis: [
      { type: 'value', name: '功率 (kW)', nameTextStyle: { color: '#668078' }, axisLabel: { color: '#668078' }, splitLine: { lineStyle: { color: '#edf4f0' } } },
      { type: 'value', name: '辐照 (W/m²)', nameTextStyle: { color: '#668078' }, axisLabel: { color: '#668078' }, splitLine: { show: false } }
    ],
    series: [
      { name: '发电功率', type: 'line', smooth: true, data: rows.value.map((item) => item.power), symbol: 'none', lineStyle: { width: 3, color: '#0c7562' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(12,117,98,.28)' }, { offset: 1, color: 'rgba(12,117,98,0)' }]) } },
      { name: '太阳辐照', type: 'line', yAxisIndex: 1, smooth: true, data: rows.value.map((item) => item.irradiance), symbol: 'none', lineStyle: { width: 2, color: '#e3a92e' } }
    ]
  })
}
</script>

<template>
  <section class="visualization-page">
    <header class="visual-header">
      <div><p class="eyebrow">SOLAR SIGNALS</p><h1>数据可视化</h1><p>以 15 分钟粒度观察电站过去 24 小时的发电节奏。</p></div>
      <div class="header-actions"><el-select v-model="selectedStationId" placeholder="选择电站" :loading="loading" class="station-picker"><el-option v-for="station in stations" :key="station.stationId" :label="station.stationName" :value="station.stationId" /></el-select><el-button :icon="Refresh" :loading="loading" @click="loadHistory">刷新数据</el-button></div>
    </header>

    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
    <template v-else-if="selectedStation">
      <section class="station-strip"><span class="sun-mark"><el-icon><Sunny /></el-icon></span><div><strong>{{ selectedStation.stationName }}</strong><small>{{ selectedStation.city || selectedStation.province || '位置待补充' }} · {{ selectedStation.capacity || 0 }} kW 装机容量</small></div><span class="sample-count">{{ rows.length }} 个数据点</span></section>
      <section class="metric-grid"><article><span>峰值功率</span><strong>{{ formatNumber(peakPower) }}<em> kW</em></strong></article><article><span>平均辐照</span><strong>{{ formatNumber(averageIrradiance) }}<em> W/m²</em></strong></article><article><span>功率累计</span><strong>{{ formatNumber(totalPower) }}<em> kW</em></strong></article></section>
      <section class="chart-card" v-loading="loading"><header><div><p class="eyebrow">24 HOUR TRACE</p><h2>功率与日照轨迹</h2></div><el-icon class="chart-icon"><DataAnalysis /></el-icon></header><div v-if="rows.length" ref="chartRef" class="chart" /><el-empty v-else-if="!loading" description="该时间范围内暂无历史数据" /></section>
    </template>
  </section>
</template>

<style scoped>
.visualization-page { display: grid; gap: 18px; }
.visual-header, .header-actions, .station-strip, .chart-card header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.visual-header { padding: 26px 28px; border: 1px solid #d9e7e1; border-radius: 14px; background: linear-gradient(120deg, #f4fbf7, #fffdf7); }
.eyebrow { margin: 0 0 6px; color: #0c7562; font-size: 11px; font-weight: 800; letter-spacing: .13em; }
h1, h2, p { margin: 0; } h1 { color: #12352f; font-size: 30px; } .visual-header > div > p:last-child { margin-top: 7px; color: #6d817b; }
.station-picker { width: 220px; }
.station-strip { padding: 14px 18px; border-left: 4px solid #e3a92e; background: #12352f; color: #fff; } .station-strip div { display: grid; gap: 3px; margin-right: auto; } .station-strip small { color: #b9cec7; } .sun-mark { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; background: #e3a92e; color: #12352f; } .sample-count { color: #d7e8e1; font-size: 13px; }
.metric-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; } .metric-grid article, .chart-card { border: 1px solid #d9e7e1; border-radius: 12px; background: #fff; } .metric-grid article { display: grid; gap: 8px; padding: 20px; } .metric-grid span { color: #71857e; font-size: 13px; } .metric-grid strong { color: #12352f; font-size: 28px; } em { color: #748780; font-size: 13px; font-style: normal; font-weight: 500; }
.chart-card { min-height: 430px; padding: 22px; } .chart-card h2 { color: #12352f; font-size: 20px; } .chart-icon { color: #e3a92e; font-size: 26px; } .chart { height: 340px; margin-top: 14px; }
@media (max-width: 760px) { .visual-header, .header-actions { align-items: stretch; flex-direction: column; } .station-picker { width: 100%; } .metric-grid { grid-template-columns: 1fr; } .station-strip { align-items: flex-start; flex-wrap: wrap; } .sample-count { width: 100%; } }
</style>
