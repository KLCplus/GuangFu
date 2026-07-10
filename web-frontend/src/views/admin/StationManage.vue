<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createStation,
  deleteStation,
  getStations,
  updateStation,
  type Station,
  type StationPayload
} from '../../api/station'
import { mockStations } from '../../data/mock'

type StationStatus = 'RUNNING' | 'STOPPED' | 'MAINTENANCE'

interface StationRow {
  stationId: number
  stationName: string
  province: string
  city: string
  address: string
  longitude: number
  latitude: number
  capacity: number
  status: StationStatus
  dataSource: string
  weatherSource: string
  description: string
  updatedAt: string
}

const filters = reactive({
  keyword: '',
  status: ''
})

const dialogVisible = ref(false)
const editingId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const remoteReady = ref(false)

const stationForm = reactive<StationRow>({
  stationId: 0,
  stationName: '',
  province: '四川省',
  city: '',
  address: '',
  longitude: 104.0668,
  latitude: 30.5728,
  capacity: 1000,
  status: 'RUNNING',
  dataSource: '手动接入',
  weatherSource: '本地天气服务',
  description: '',
  updatedAt: ''
})

const fallbackRows: StationRow[] = mockStations.map((station, index) => ({
    ...station,
    dataSource: index % 2 === 0 ? 'PVOutput 接入' : '手动接入',
    weatherSource: index % 2 === 0 ? '和风天气' : '本地天气服务',
    updatedAt: `2026-07-0${Math.min(index + 6, 9)} 10:30:00`
  }))

const stationRows = ref<StationRow[]>([...fallbackRows])

const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return stationRows.value.filter((row) => {
    const matchesKeyword =
      !keyword ||
      [row.stationName, row.province, row.city, row.address, row.dataSource, row.weatherSource].some((value) =>
        value.toLowerCase().includes(keyword)
      )
    const matchesStatus = !filters.status || row.status === filters.status
    return matchesKeyword && matchesStatus
  })
})

const summaryCards = computed(() => {
  const running = stationRows.value.filter((row) => row.status === 'RUNNING').length
  const capacity = stationRows.value.reduce((sum, row) => sum + row.capacity, 0)
  const dataSources = new Set(stationRows.value.map((row) => row.dataSource)).size
  return [
    { label: '电站总数', value: `${stationRows.value.length}` },
    { label: '运行中', value: `${running}` },
    { label: '装机容量', value: `${capacity} kW` },
    { label: '数据源', value: `${dataSources}` }
  ]
})

function statusType(status: StationStatus) {
  if (status === 'RUNNING') return 'success'
  if (status === 'MAINTENANCE') return 'warning'
  return 'info'
}

function statusText(status: StationStatus) {
  const map: Record<StationStatus, string> = {
    RUNNING: '运行中',
    STOPPED: '停机',
    MAINTENANCE: '维护中'
  }
  return map[status]
}

function resetForm() {
  Object.assign(stationForm, {
    stationId: 0,
    stationName: '',
    province: '四川省',
    city: '',
    address: '',
    longitude: 104.0668,
    latitude: 30.5728,
    capacity: 1000,
    status: 'RUNNING',
    dataSource: '手动接入',
    weatherSource: '本地天气服务',
    description: '',
    updatedAt: ''
  })
}

function openCreate() {
  editingId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: StationRow) {
  editingId.value = row.stationId
  Object.assign(stationForm, row)
  dialogVisible.value = true
}

async function saveStation() {
  if (!stationForm.stationName.trim() || !stationForm.city.trim()) {
    ElMessage.warning('请填写电站名称和城市')
    return
  }

  saving.value = true
  try {
    const payload = toStationPayload(stationForm)
    const nextRow: StationRow = remoteReady.value
      ? mapStation(
          editingId.value ? await updateStation(editingId.value, payload) : await createStation(payload),
          stationForm
        )
      : {
          ...stationForm,
          stationId: editingId.value ?? Date.now(),
          updatedAt: formatDateTime()
        }

    if (editingId.value) {
      const index = stationRows.value.findIndex((row) => row.stationId === editingId.value)
      if (index >= 0) stationRows.value[index] = nextRow
      ElMessage.success('电站配置已更新')
    } else {
      stationRows.value.unshift(nextRow)
      ElMessage.success('电站已新增')
    }
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '电站保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStation(row: StationRow) {
  const nextStatus: StationStatus = row.status === 'RUNNING' ? 'STOPPED' : 'RUNNING'
  await updateStationStatus(row, nextStatus, nextStatus === 'RUNNING' ? '电站已启用' : '电站已停用')
}

async function setMaintenance(row: StationRow) {
  await updateStationStatus(row, 'MAINTENANCE', '电站已切换为维护状态')
}

async function removeStation(row: StationRow) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.stationName}」吗？`, '删除电站', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    if (remoteReady.value) {
      await deleteStation(row.stationId)
    }
    stationRows.value = stationRows.value.filter((item) => item.stationId !== row.stationId)
    ElMessage.success('电站已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '电站删除失败')
    }
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
}

function formatCoordinate(value: number) {
  return Number(value).toFixed(4)
}

function formatDateTime() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes()
  )}:${pad(date.getSeconds())}`
}

async function loadStations() {
  loading.value = true
  try {
    const result = await getStations({ pageNum: 1, pageSize: 100 })
    stationRows.value = result.records.map((station, index) => mapStation(station, fallbackRows[index]))
    remoteReady.value = true
  } catch (error) {
    stationRows.value = [...fallbackRows]
    remoteReady.value = false
    ElMessage.warning(error instanceof Error ? `使用演示数据：${error.message}` : '使用演示数据')
  } finally {
    loading.value = false
  }
}

async function updateStationStatus(row: StationRow, status: StationStatus, successMessage: string) {
  try {
    if (remoteReady.value) {
      const updated = await updateStation(row.stationId, toStationPayload({ ...row, status }))
      Object.assign(row, mapStation(updated, { ...row, status }))
    } else {
      row.status = status
      row.updatedAt = formatDateTime()
    }
    ElMessage.success(successMessage)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '电站状态更新失败')
  }
}

function mapStation(station: Station, extra?: Partial<StationRow>): StationRow {
  return {
    stationId: station.stationId,
    stationName: station.stationName,
    province: station.province || extra?.province || '',
    city: station.city || extra?.city || '',
    address: station.address || extra?.address || '',
    longitude: station.longitude ?? extra?.longitude ?? 0,
    latitude: station.latitude ?? extra?.latitude ?? 0,
    capacity: station.capacity ?? extra?.capacity ?? 0,
    status: normalizeStatus(station.status || extra?.status),
    dataSource: extra?.dataSource || '手动接入',
    weatherSource: extra?.weatherSource || '本地天气服务',
    description: station.description || extra?.description || '',
    updatedAt: extra?.updatedAt || formatDateTime()
  }
}

function toStationPayload(row: StationRow): StationPayload {
  return {
    stationName: row.stationName.trim(),
    province: row.province,
    city: row.city,
    address: row.address,
    longitude: row.longitude,
    latitude: row.latitude,
    capacity: row.capacity,
    status: row.status,
    description: row.description
  }
}

function normalizeStatus(status?: string): StationStatus {
  if (status === 'RUNNING' || status === 'STOPPED' || status === 'MAINTENANCE') return status
  return 'STOPPED'
}

onMounted(loadStations)
</script>

<template>
  <section class="page-shell admin-station-page">
    <div class="page-title">
      <div>
        <h2>电站管理</h2>
        <p>维护用户端首页可切换的电站，配置位置、数据源、天气源和启停状态。</p>
      </div>
      <div class="title-actions">
        <el-button :loading="loading" @click="loadStations">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增电站</el-button>
      </div>
    </div>

    <div class="summary-grid">
      <div v-for="item in summaryCards" :key="item.label" class="summary-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <section class="page-section">
      <div class="toolbar">
        <div class="filter-row">
          <el-input v-model="filters.keyword" clearable placeholder="搜索电站、城市、数据源" />
          <el-select v-model="filters.status" clearable placeholder="电站状态">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="停机" value="STOPPED" />
          </el-select>
        </div>
        <el-button @click="resetFilters">重置筛选</el-button>
      </div>

      <el-table v-loading="loading" :data="filteredRows" size="large" stripe>
        <el-table-column prop="stationName" label="电站名称" min-width="190" fixed="left">
          <template #default="{ row }">
            <strong class="table-title">{{ row.stationName }}</strong>
            <span class="table-sub">{{ row.description }}</span>
          </template>
        </el-table-column>
        <el-table-column label="位置" min-width="220">
          <template #default="{ row }">
            <span>{{ row.province }} {{ row.city }}</span>
            <span class="table-sub">{{ row.address }}</span>
          </template>
        </el-table-column>
        <el-table-column label="经纬度" min-width="150">
          <template #default="{ row }">
            {{ formatCoordinate(row.longitude) }}, {{ formatCoordinate(row.latitude) }}
          </template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量" width="110" sortable>
          <template #default="{ row }">{{ row.capacity }} kW</template>
        </el-table-column>
        <el-table-column prop="dataSource" label="数据源" min-width="130" />
        <el-table-column prop="weatherSource" label="天气源" min-width="130" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text @click="toggleStation(row)">
              {{ row.status === 'RUNNING' ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" text @click="setMaintenance(row)">维护</el-button>
            <el-button size="small" text type="danger" @click="removeStation(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑电站' : '新增电站'" width="720px">
      <el-form label-position="top" class="station-form">
        <el-form-item label="电站名称">
          <el-input v-model="stationForm.stationName" placeholder="请输入电站名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="stationForm.status">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="停机" value="STOPPED" />
          </el-select>
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="stationForm.province" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="stationForm.city" />
        </el-form-item>
        <el-form-item label="详细地址" class="wide">
          <el-input v-model="stationForm.address" />
        </el-form-item>
        <el-form-item label="经度">
          <el-input-number v-model="stationForm.longitude" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="纬度">
          <el-input-number v-model="stationForm.latitude" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="装机容量(kW)">
          <el-input-number v-model="stationForm.capacity" :min="1" :step="50" />
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="stationForm.dataSource">
            <el-option label="PVOutput 接入" value="PVOutput 接入" />
            <el-option label="手动接入" value="手动接入" />
            <el-option label="CSV 批量导入" value="CSV 批量导入" />
          </el-select>
        </el-form-item>
        <el-form-item label="天气源">
          <el-select v-model="stationForm.weatherSource">
            <el-option label="和风天气" value="和风天气" />
            <el-option label="本地天气服务" value="本地天气服务" />
            <el-option label="气象局接口" value="气象局接口" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" class="wide">
          <el-input v-model="stationForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveStation">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-station-page {
  gap: 18px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  min-height: 96px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.summary-card span {
  color: var(--color-muted);
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 12px;
  color: #10274c;
  font-size: 24px;
}

.filter-row {
  display: grid;
  grid-template-columns: 320px 160px;
  gap: 10px;
}

.title-actions {
  display: flex;
  gap: 10px;
}

.table-title,
.table-sub {
  display: block;
}

.table-title {
  color: #10274c;
}

.table-sub {
  margin-top: 4px;
  color: var(--color-muted);
  font-size: 12px;
}

.station-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.station-form .wide {
  grid-column: 1 / -1;
}

.station-form :deep(.el-input-number),
.station-form :deep(.el-select) {
  width: 100%;
}

@media (max-width: 980px) {
  .summary-grid,
  .filter-row,
  .station-form {
    grid-template-columns: 1fr;
  }

  .station-form .wide {
    grid-column: auto;
  }
}
</style>
