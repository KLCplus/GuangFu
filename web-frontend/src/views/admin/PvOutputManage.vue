<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { loadPvOutputStations, syncPvOutputLiveStations } from '../../api/pvoutput'
import type { PvOutputStation } from '../../api/pvoutput'

const listQuery = reactive({ keyword: '', enabled: undefined as boolean | undefined })
const stationLoading = ref(false)
const syncLoading = ref(false)
const stations = ref<PvOutputStation[]>([])
let filterTimer: ReturnType<typeof setTimeout> | undefined
let latestRequest = 0

async function fetchStations() {
  const requestId = ++latestRequest
  stationLoading.value = true
  try {
    const result = await loadPvOutputStations({
      keyword: listQuery.keyword || undefined,
      enabled: listQuery.enabled
    })
    if (requestId === latestRequest) stations.value = result
  } catch (error) {
    if (requestId === latestRequest) ElMessage.error(message(error, '电站列表加载失败'))
  } finally {
    if (requestId === latestRequest) stationLoading.value = false
  }
}

async function syncPublicData() {
  syncLoading.value = true
  try {
    const results = await syncPvOutputLiveStations()
    ElMessage.success(`公开数据同步完成：${results.length} 个电站`)
    await fetchStations()
  } catch (error) {
    ElMessage.error(message(error, '公开数据同步失败'))
  } finally {
    syncLoading.value = false
  }
}

function syncStatus(row: PvOutputStation) {
  if (row.lastSyncStatus === 'SUCCESS') return '同步成功'
  if (row.lastSyncStatus === 'FAILED' && !row.lastSyncError?.includes('接入未启用')) return '同步失败'
  return '未同步'
}

function statusType(row: PvOutputStation) {
  if (row.lastSyncStatus === 'SUCCESS') return 'success'
  if (row.lastSyncStatus === 'FAILED' && !row.lastSyncError?.includes('接入未启用')) return 'danger'
  return 'info'
}

function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

onMounted(fetchStations)

watch(
  [() => listQuery.keyword, () => listQuery.enabled],
  ([keyword], [previousKeyword]) => {
    if (filterTimer) clearTimeout(filterTimer)
    const delay = keyword === previousKeyword ? 0 : 300
    filterTimer = setTimeout(fetchStations, delay)
  }
)

onBeforeUnmount(() => {
  if (filterTimer) clearTimeout(filterTimer)
})
</script>

<template>
  <section class="page-shell admin-pvoutput-page">
    <div class="page-title">
      <h2>PVOutput 管理</h2>
      <el-button type="primary" :loading="syncLoading" @click="syncPublicData">同步公开数据</el-button>
    </div>

    <section class="page-section">
      <el-form class="toolbar pvoutput-toolbar" inline :model="listQuery">
        <el-form-item label="关键词">
          <el-input v-model="listQuery.keyword" clearable placeholder="名称/邮编/设备" />
        </el-form-item>
        <el-form-item label="使用状态">
          <el-select v-model="listQuery.enabled" clearable class="short-input">
            <el-option label="使用中" :value="true" />
            <el-option label="未使用" :value="false" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="stationLoading"
        :data="stations"
        empty-text="暂无公开电站"
        max-height="calc(100vh - 178px)"
        stripe
      >
        <el-table-column prop="systemName" label="名称" min-width="170" />
        <el-table-column prop="externalSystemId" label="System ID" width="120" />
        <el-table-column prop="systemSizeW" label="容量（W）" width="110" />
        <el-table-column prop="postcode" label="邮编" width="100" />
        <el-table-column prop="orientation" label="朝向" width="90" />
        <el-table-column label="使用状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '使用中' : '未使用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastSyncTime" label="最近同步" min-width="170">
          <template #default="{ row }">{{ row.lastSyncTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="同步结果" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row)">{{ syncStatus(row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<style scoped>
.pvoutput-toolbar {
  justify-content: flex-start;
}

.short-input {
  width: 120px;
}
</style>
