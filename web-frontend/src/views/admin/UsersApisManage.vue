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

interface UserApiRow {
  apiId: number
  apiName: string
  apiKey: string
  ownerName: string
  ownerEmail: string
  modelName: string
  status: ApiStatus
  todayCalls: number
  totalCalls: number
  errorRate: number
  avgLatency: number
  dailyQuota: number
  remainingQuota: number
  balance: number
  createdAt: string
}

interface ApiLogRow {
  id: number
  requestTime: string
  path: string
  statusCode: number
  costTime: number
  message: string
}

const filters = reactive({
  keyword: '',
  status: '',
  modelName: ''
})

const selectedApi = ref<UserApiRow>()
const logDialogVisible = ref(false)
const loading = ref(false)
const logsLoading = ref(false)
const remoteReady = ref(false)

const fallbackRows: UserApiRow[] = [
  {
    apiId: 501,
    apiName: '成都示范站短时预测 API',
    apiKey: 'sk-pv-cd-demo-7a9f24d8',
    ownerName: 'lin_admin',
    ownerEmail: 'lin@example.com',
    modelName: 'PatchTST 短期功率预测',
    status: 'ACTIVE',
    todayCalls: 328,
    totalCalls: 15420,
    errorRate: 0.8,
    avgLatency: 126,
    dailyQuota: 2000,
    remainingQuota: 1672,
    balance: 286.5,
    createdAt: '2026-07-06 09:12:00'
  },
  {
    apiId: 502,
    apiName: '云图递归预测试用 API',
    apiKey: 'sk-pv-cloud-trial-31ce88b1',
    ownerName: 'demo_user',
    ownerEmail: 'demo@example.com',
    modelName: 'SimVP+GSTA 云图递归预测',
    status: 'ACTIVE',
    todayCalls: 64,
    totalCalls: 1810,
    errorRate: 2.4,
    avgLatency: 238,
    dailyQuota: 300,
    remainingQuota: 236,
    balance: 48,
    createdAt: '2026-07-05 16:30:00'
  },
  {
    apiId: 503,
    apiName: '德阳屋顶站多模态 API',
    apiKey: 'sk-pv-dy-multi-c83421aa',
    ownerName: 'ops_chen',
    ownerEmail: 'chen@example.com',
    modelName: 'CNN+LSTM 多模态预测',
    status: 'DISABLED',
    todayCalls: 0,
    totalCalls: 7092,
    errorRate: 1.1,
    avgLatency: 184,
    dailyQuota: 1000,
    remainingQuota: 1000,
    balance: 112.8,
    createdAt: '2026-07-03 11:08:00'
  },
  {
    apiId: 504,
    apiName: '绵阳储能协同预测 API',
    apiKey: 'sk-pv-my-storage-0984ddee',
    ownerName: 'station_owner',
    ownerEmail: 'owner@example.com',
    modelName: 'iTransformer 融合序列模型',
    status: 'EXPIRED',
    todayCalls: 0,
    totalCalls: 982,
    errorRate: 3.2,
    avgLatency: 156,
    dailyQuota: 500,
    remainingQuota: 0,
    balance: 0,
    createdAt: '2026-06-28 14:45:00'
  }
]

const fallbackLogs: ApiLogRow[] = [
  {
    id: 1,
    requestTime: '2026-07-10 08:48:12',
    path: '/openapi/v1/predict',
    statusCode: 200,
    costTime: 132,
    message: '预测成功'
  },
  {
    id: 2,
    requestTime: '2026-07-10 08:31:40',
    path: '/openapi/v1/predict',
    statusCode: 400,
    costTime: 28,
    message: '输入帧数量不足'
  },
  {
    id: 3,
    requestTime: '2026-07-10 08:06:22',
    path: '/openapi/v1/report',
    statusCode: 200,
    costTime: 310,
    message: '报告生成完成'
  }
]

const apiRows = ref<UserApiRow[]>([...fallbackRows])
const apiLogs = ref<ApiLogRow[]>([...fallbackLogs])

const modelOptions = computed(() => Array.from(new Set(apiRows.value.map((row) => row.modelName))))

const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return apiRows.value.filter((row) => {
    const matchesKeyword =
      !keyword ||
      [row.apiName, row.ownerName, row.ownerEmail, row.modelName, row.apiKey].some((value) =>
        value.toLowerCase().includes(keyword)
      )
    const matchesStatus = !filters.status || row.status === filters.status
    const matchesModel = !filters.modelName || row.modelName === filters.modelName
    return matchesKeyword && matchesStatus && matchesModel
  })
})

const summaryCards = computed(() => {
  const activeCount = apiRows.value.filter((row) => row.status === 'ACTIVE').length
  const todayCalls = apiRows.value.reduce((sum, row) => sum + row.todayCalls, 0)
  const totalQuota = apiRows.value.reduce((sum, row) => sum + row.remainingQuota, 0)
  const avgError =
    apiRows.value.reduce((sum, row) => sum + row.errorRate, 0) / Math.max(apiRows.value.length, 1)
  return [
    { label: '启用 API', value: `${activeCount}` },
    { label: '今日调用', value: `${todayCalls}` },
    { label: '剩余额度', value: `${totalQuota}` },
    { label: '平均错误率', value: `${avgError.toFixed(1)}%` }
  ]
})

function statusType(status: ApiStatus) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DISABLED') return 'warning'
  return 'info'
}

function statusText(status: ApiStatus) {
  const map: Record<ApiStatus, string> = {
    ACTIVE: '启用',
    DISABLED: '禁用',
    EXPIRED: '过期'
  }
  return map[status]
}

function maskKey(key: string) {
  if (key.length <= 14) return key
  return `${key.slice(0, 8)}...${key.slice(-6)}`
}

async function toggleStatus(row: UserApiRow) {
  const nextStatus: ApiStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    if (remoteReady.value) {
      await updateAdminApiKeyStatus(row.apiId, { status: nextStatus })
    }
    row.status = nextStatus
    if (nextStatus === 'ACTIVE' && row.remainingQuota <= 0) {
      row.remainingQuota = row.dailyQuota
    }
    ElMessage.success(nextStatus === 'ACTIVE' ? 'API 已恢复' : 'API 已禁用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'API 状态更新失败')
  }
}

function resetQuota(row: UserApiRow) {
  row.remainingQuota = row.dailyQuota
  ElMessage.success(remoteReady.value ? '已按日额度重算当前显示额度' : '额度已重置')
}

function copyKey(row: UserApiRow) {
  const writeTask = navigator.clipboard?.writeText(row.apiKey)
  if (!writeTask) {
    ElMessage.warning('复制失败')
    return
  }
  writeTask.then(() => ElMessage.success('API Key 已复制')).catch(() => ElMessage.warning('复制失败'))
}

async function showLogs(row: UserApiRow) {
  selectedApi.value = row
  logDialogVisible.value = true
  if (!remoteReady.value) {
    apiLogs.value = [...fallbackLogs]
    return
  }

  logsLoading.value = true
  try {
    const result = await getAdminApiCallLogs({ pageNum: 1, pageSize: 20, apiKeyId: row.apiId })
    apiLogs.value = mapLogs(result.records)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '调用日志加载失败')
  } finally {
    logsLoading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.modelName = ''
}

async function loadAdminApis() {
  loading.value = true
  try {
    const [keys, logPage] = await Promise.all([
      getAdminApiKeys(),
      getAdminApiCallLogs({ pageNum: 1, pageSize: 100 })
    ])
    const logs = logPage.records ?? []
    apiRows.value = keys.map((key, index) => mapApiKey(key, logs, index))
    apiLogs.value = mapLogs(logs)
    remoteReady.value = true
  } catch (error) {
    remoteReady.value = false
    apiRows.value = [...fallbackRows]
    apiLogs.value = [...fallbackLogs]
    ElMessage.warning(error instanceof Error ? `使用演示数据：${error.message}` : '使用演示数据')
  } finally {
    loading.value = false
  }
}

function mapApiKey(key: ApiKey, logs: ApiCallLog[], index: number): UserApiRow {
  const keyLogs = logs.filter((log) => log.apiKeyId === key.apiKeyId)
  const today = formatDate(new Date())
  const todayLogs = keyLogs.filter((log) => (log.requestTime || log.createdAt || '').startsWith(today))
  const failedLogs = keyLogs.filter((log) => isFailedLog(log))
  const totalCost = keyLogs.reduce((sum, log) => sum + normalizeCost(log), 0)
  const dailyQuota = key.dailyQuota ?? 0

  return {
    apiId: key.apiKeyId,
    apiName: key.keyName,
    apiKey: key.apiKey || key.apiKeyPrefix || `api-key-${key.apiKeyId}`,
    ownerName: `API 账号 ${index + 1}`,
    ownerEmail: '后端暂未返回所属用户',
    modelName: firstLogModelName(keyLogs) || '开放预测 API',
    status: normalizeApiStatus(key.status),
    todayCalls: todayLogs.length,
    totalCalls: keyLogs.length,
    errorRate: keyLogs.length ? (failedLogs.length / keyLogs.length) * 100 : 0,
    avgLatency: keyLogs.length ? Math.round(totalCost / keyLogs.length) : 0,
    dailyQuota,
    remainingQuota: dailyQuota ? Math.max(dailyQuota - todayLogs.length, 0) : 0,
    balance: 0,
    createdAt: key.createdAt || '-'
  }
}

function mapLogs(logs: ApiCallLog[]): ApiLogRow[] {
  return logs.map((log) => ({
    id: log.logId,
    requestTime: log.requestTime || log.createdAt || '-',
    path: log.path || log.requestPath || '-',
    statusCode: log.statusCode || log.httpStatus || (isFailedLog(log) ? 500 : 200),
    costTime: normalizeCost(log),
    message: log.errorMessage || log.status || log.bizStatus || '调用完成'
  }))
}

function normalizeApiStatus(status: string): ApiStatus {
  if (status === 'ACTIVE' || status === 'DISABLED' || status === 'EXPIRED') return status
  return 'DISABLED'
}

function normalizeCost(log: ApiCallLog) {
  return log.costTime ?? log.costTimeMs ?? 0
}

function isFailedLog(log: ApiCallLog) {
  const statusCode = log.statusCode || log.httpStatus || 0
  const status = log.status || log.bizStatus || ''
  return statusCode >= 400 || status === 'FAILED'
}

function firstLogModelName(logs: ApiCallLog[]) {
  return logs.find((log) => log.modelName)?.modelName
}

function formatDate(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

onMounted(loadAdminApis)
</script>

<template>
  <section class="page-shell admin-api-page">
    <div class="page-title">
      <div>
        <h2>用户/API</h2>
        <p>以 API 为中心展示所属用户、模型、调用量、余额、状态和日志。</p>
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
          <el-input v-model="filters.keyword" clearable placeholder="搜索 API、用户、Key" />
          <el-select v-model="filters.status" clearable placeholder="状态">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
            <el-option label="过期" value="EXPIRED" />
          </el-select>
          <el-select v-model="filters.modelName" clearable placeholder="所属模型">
            <el-option v-for="model in modelOptions" :key="model" :label="model" :value="model" />
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-button @click="resetFilters">重置筛选</el-button>
          <el-button :loading="loading" @click="loadAdminApis">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="filteredRows" size="large" stripe>
        <el-table-column prop="apiName" label="API 名称" min-width="210" fixed="left">
          <template #default="{ row }">
            <strong class="table-title">{{ row.apiName }}</strong>
            <span class="table-sub">{{ maskKey(row.apiKey) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所属用户" min-width="150">
          <template #default="{ row }">
            <span>{{ row.ownerName }}</span>
            <span class="table-sub">{{ row.ownerEmail }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="所属模型" min-width="190" />
        <el-table-column prop="status" label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="todayCalls" label="今日调用" width="100" sortable />
        <el-table-column prop="totalCalls" label="总调用量" width="110" sortable />
        <el-table-column label="余额/额度" width="132">
          <template #default="{ row }">
            <span>{{ row.balance ? `${row.balance.toFixed(1)} 元` : '按额度计费' }}</span>
            <span class="table-sub">{{ row.remainingQuota }}/{{ row.dailyQuota }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorRate" label="错误率" width="90">
          <template #default="{ row }">{{ row.errorRate.toFixed(1) }}%</template>
        </el-table-column>
        <el-table-column prop="avgLatency" label="平均时延" width="100">
          <template #default="{ row }">{{ row.avgLatency }} ms</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="copyKey(row)">复制 Key</el-button>
            <el-button size="small" text @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '禁用' : '恢复' }}
            </el-button>
            <el-button size="small" text @click="resetQuota(row)">重置额度</el-button>
            <el-button size="small" text @click="showLogs(row)">日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="logDialogVisible" :title="selectedApi ? `${selectedApi.apiName} 调用日志` : '调用日志'" width="760px">
      <el-table v-loading="logsLoading" :data="apiLogs" size="large">
        <el-table-column prop="requestTime" label="请求时间" min-width="170" />
        <el-table-column prop="path" label="路径" min-width="160" />
        <el-table-column prop="statusCode" label="状态码" width="90">
          <template #default="{ row }">
            <el-tag :type="row.statusCode === 200 ? 'success' : 'danger'">{{ row.statusCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costTime" label="耗时" width="90">
          <template #default="{ row }">{{ row.costTime }} ms</template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="160" />
      </el-table>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-api-page {
  gap: 18px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 18px;
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
  display: block;
  margin-top: 12px;
  color: var(--admin-ink);
  font-size: 26px;
}

.filter-row {
  display: grid;
  grid-template-columns: 260px 150px 240px;
  gap: 10px;
}

.toolbar-actions {
  display: flex;
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
