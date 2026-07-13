<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAdminApiCallLogs,
  getAdminApiKeys,
  updateAdminApiKeyStatus,
  type ApiCallLog,
  type ApiKey
} from '../../api/open'

type ApiStatus = 'ACTIVE' | 'DISABLED' | 'EXPIRED'

const filters = reactive({ keyword: '', status: '' })
const apiKeys = ref<ApiKey[]>([])
const callLogs = ref<ApiCallLog[]>([])
const selectedApi = ref<ApiKey>()
const totalLogCount = ref(0)
const loading = ref(false)
const logsLoading = ref(false)
const logDialogVisible = ref(false)

const filteredKeys = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return apiKeys.value.filter((key) => {
    const matchesKeyword =
      !keyword || [key.keyName, key.apiKeyPrefix || ''].some((value) => value.toLowerCase().includes(keyword))
    return matchesKeyword && (!filters.status || key.status === filters.status)
  })
})

const summaryCards = computed(() => [
  { label: 'API Key 总数', value: apiKeys.value.length },
  { label: '已启用', value: apiKeys.value.filter((key) => key.status === 'ACTIVE').length },
  { label: '已禁用', value: apiKeys.value.filter((key) => key.status === 'DISABLED').length },
  { label: '调用日志总数', value: totalLogCount.value }
])

function normalizeStatus(status: string): ApiStatus {
  if (status === 'ACTIVE' || status === 'EXPIRED') return status
  return 'DISABLED'
}

function statusType(status: string) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DISABLED') return 'warning'
  return 'info'
}

function statusText(status: string) {
  return { ACTIVE: '启用', DISABLED: '禁用', EXPIRED: '过期' }[normalizeStatus(status)]
}

function displayPrefix(key: ApiKey) {
  return key.apiKeyPrefix ? `${key.apiKeyPrefix}…` : '-'
}

function displayTime(value?: string) {
  return value || '-'
}

async function loadAdminApis() {
  loading.value = true
  try {
    const [keys, logPage] = await Promise.all([
      getAdminApiKeys(),
      getAdminApiCallLogs({ pageNum: 1, pageSize: 1 })
    ])
    apiKeys.value = keys
    totalLogCount.value = logPage.total
  } catch (error) {
    apiKeys.value = []
    totalLogCount.value = 0
    ElMessage.error(error instanceof Error ? error.message : 'API 管理数据加载失败')
  } finally {
    loading.value = false
  }
}

async function toggleStatus(key: ApiKey) {
  const nextStatus: ApiStatus = key.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await updateAdminApiKeyStatus(key.apiKeyId, { status: nextStatus })
    key.status = nextStatus
    ElMessage.success(nextStatus === 'ACTIVE' ? 'API Key 已启用' : 'API Key 已禁用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'API Key 状态更新失败')
  }
}

async function showLogs(key: ApiKey) {
  selectedApi.value = key
  logDialogVisible.value = true
  logsLoading.value = true
  callLogs.value = []
  try {
    const result = await getAdminApiCallLogs({ pageNum: 1, pageSize: 50, apiKeyId: key.apiKeyId })
    callLogs.value = result.records
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '调用日志加载失败')
  } finally {
    logsLoading.value = false
  }
}

function logTime(log: ApiCallLog) {
  return log.requestTime || log.createdAt || '-'
}

function logPath(log: ApiCallLog) {
  return log.path || log.requestPath || '-'
}

function logStatusCode(log: ApiCallLog) {
  return log.statusCode ?? log.httpStatus ?? '-'
}

function logCost(log: ApiCallLog) {
  const value = log.costTime ?? log.costTimeMs
  return value === undefined ? '-' : `${value} ms`
}

function logMessage(log: ApiCallLog) {
  return log.errorMessage || log.status || log.bizStatus || '-'
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
}

onMounted(loadAdminApis)
</script>

<template>
  <section class="page-shell admin-api-page">
    <div class="page-title">
      <div>
        <h2>API 管理</h2>
      </div>
      <el-button :loading="loading" @click="loadAdminApis">刷新</el-button>
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
          <el-input v-model="filters.keyword" clearable placeholder="搜索名称或 Key 前缀" />
          <el-select v-model="filters.status" clearable placeholder="状态">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
            <el-option label="过期" value="EXPIRED" />
          </el-select>
        </div>
        <el-button @click="resetFilters">重置筛选</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredKeys"
        empty-text="暂无 API Key"
        max-height="calc(100vh - 276px)"
        stripe
      >
        <el-table-column prop="keyName" label="名称" min-width="190" fixed="left">
          <template #default="{ row }">
            <strong class="table-title">{{ row.keyName }}</strong>
            <span class="table-sub">{{ displayPrefix(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rateLimitPerMinute" label="每分钟上限" width="120">
          <template #default="{ row }">{{ row.rateLimitPerMinute ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="dailyQuota" label="每日上限" width="110">
          <template #default="{ row }">{{ row.dailyQuota ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="expireTime" label="到期时间" min-width="170">
          <template #default="{ row }">{{ displayTime(row.expireTime || row.expireAt) }}</template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" label="最后调用" min-width="170">
          <template #default="{ row }">{{ displayTime(row.lastUsedAt) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170">
          <template #default="{ row }">{{ displayTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" text type="primary" @click="showLogs(row)">日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="logDialogVisible" :title="selectedApi ? `${selectedApi.keyName} 调用日志` : '调用日志'" width="800px">
      <el-table v-loading="logsLoading" :data="callLogs" empty-text="暂无调用日志" max-height="55vh">
        <el-table-column label="请求时间" min-width="170">
          <template #default="{ row }">{{ logTime(row) }}</template>
        </el-table-column>
        <el-table-column label="路径" min-width="170">
          <template #default="{ row }">{{ logPath(row) }}</template>
        </el-table-column>
        <el-table-column label="状态码" width="90">
          <template #default="{ row }">{{ logStatusCode(row) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ logCost(row) }}</template>
        </el-table-column>
        <el-table-column label="结果" min-width="150">
          <template #default="{ row }">{{ logMessage(row) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-api-page {
  gap: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--admin-line);
  border-radius: 8px;
  background: var(--admin-surface);
  box-shadow: var(--admin-shadow);
}

.summary-card span {
  color: var(--admin-muted);
  font-size: 13px;
}

.summary-card strong {
  margin: 0;
  color: var(--admin-ink);
  font-size: 21px;
}

.filter-row {
  display: grid;
  grid-template-columns: 280px 150px;
  gap: 10px;
}

.table-title,
.table-sub {
  display: block;
}

.table-title {
  color: var(--admin-ink);
}

.table-sub {
  margin-top: 4px;
  color: var(--admin-muted);
  font-size: 12px;
}

@media (max-width: 980px) {
  .summary-grid,
  .filter-row {
    grid-template-columns: 1fr;
  }
}
</style>
