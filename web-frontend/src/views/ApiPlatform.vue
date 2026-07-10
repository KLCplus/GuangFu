<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createApiKey,
  loadApiCallLogs,
  loadApiKeys,
  loadApiUsageStats,
  removeApiKey,
  resetApiKey,
  setApiKeyEnabled
} from '../api/userPages'
import { getOpenOverview } from "../api/open"
import type { ApiKey, ApiKeyApplyPayload, OpenAccountOverview } from "../api/open"
import type { ApiUsageStats, DataSource, NormalizedApiCallLog } from '../api/userPages'

interface ApiKeyForm {
  keyName: string
  expireDays: number
}

interface PlaceholderCapability {
  title: string
  value: string
  note: string
}

const keysLoading = ref(false)
const logsLoading = ref(false)
const actionLoadingId = ref<number | null>(null)
const createLoading = ref(false)
const loadError = ref('')
const apiKeys = ref<ApiKey[]>([])
const callLogs = ref<NormalizedApiCallLog[]>([])
const usageStats = ref<ApiUsageStats | null>(null)
const keySource = ref<DataSource>('remote')
const logSource = ref<DataSource>('remote')
const statsSource = ref<DataSource>('remote')
const createDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const createdKey = ref<ApiKey | null>(null)
const createdKeySource = ref<DataSource>('remote')
const openOverview = ref<OpenAccountOverview | null>(null)

const createForm = reactive<ApiKeyForm>({
  keyName: '',
  expireDays: 90
})

const logQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  apiKeyId: undefined as number | undefined,
  status: ''
})

const totalLogs = ref(0)

const activeKeys = computed(() => apiKeys.value.filter((item) => normalizeKeyStatus(item.status) === 'ACTIVE'))
const disabledKeys = computed(() => apiKeys.value.filter((item) => normalizeKeyStatus(item.status) !== 'ACTIVE'))
const totalQuota = computed(() => apiKeys.value.reduce((sum, item) => sum + (item.dailyQuota ?? 0), 0))
const remainingQuota = computed(() => usageStats.value?.summary.remainingQuota ?? Math.max(totalQuota.value, 0))

const overviewCards = computed(() => [
  {
    label: "API Key",
    value: String(apiKeys.value.length),
    note: `${activeKeys.value.length} 个启用，${disabledKeys.value.length} 个停用`
  },
  {
    label: "今日调用",
    value: formatNumber(usageStats.value?.summary.todayCalls ?? 0),
    note: "来自调用日志统计"
  },
  {
    label: "错误率",
    value: `${usageStats.value?.summary.errorRate ?? 0}%`,
    note: `${usageStats.value?.summary.failedCalls ?? 0} 次失败`
  },
  {
    label: "平均时延",
    value: `${usageStats.value?.summary.avgLatency ?? 0} ms`,
    note: "开放预测接口响应耗时"
  }
])

const placeholderCapabilities = computed<PlaceholderCapability[]>(() => [
  { title: "余额", value: openOverview.value ? `￥${openOverview.value.wallet.balance.toFixed(2)}` : "暂无数据", note: "GET /api/open/wallet" },
  { title: "套餐", value: openOverview.value ? `${openOverview.value.plans.length} 个` : "暂无数据", note: "GET /api/open/plans" },
  { title: "Key 重置", value: "已接入", note: "POST /api/open/keys/{apiKeyId}/reset" }
])

const requestExample = computed(() => ({
  stationId: 1,
  modelName: 'iTransformer',
  input: Array.from({ length: 30 }, (_, index) => ({
    time: `2026-07-09 10:${String(index).padStart(2, '0')}:00`,
    power: Number((52.8 + index * 0.42).toFixed(2)),
    temperature: Number((31.2 + index * 0.03).toFixed(2)),
    irradiance: Number((820 + index * 3.5).toFixed(1))
  }))
}))

const responseExample = {
  taskId: 1001,
  taskNo: 'OPEN-20260709-001',
  status: 'SUCCESS',
  predictions: [
    { timeOffset: 5, predictPower: 75.85 },
    { timeOffset: 10, predictPower: 78.12 },
    { timeOffset: 15, predictPower: 80.06 }
  ],
  costTime: 126
}

const openApiPredictUrl = computed(() => `${__PV_BACKEND_URL__.replace(/\/$/, '')}/openapi/v1/predict`)

const curlExample = computed(() =>
  [
    `curl -X POST "${openApiPredictUrl.value}" \\`,
    '  -H "Content-Type: application/json" \\',
    '  -H "X-API-KEY: <your-api-key>" \\',
    `  -d '${JSON.stringify(requestExample.value, null, 2)}'`
  ].join('\n')
)

onMounted(() => {
  void fetchPageData()
})

async function fetchPageData() {
  await Promise.all([fetchKeys(), fetchLogs(), fetchUsageStats(), fetchOpenOverview()])
}

async function fetchKeys() {
  keysLoading.value = true
  loadError.value = ""
  try {
    const result = await loadApiKeys()
    apiKeys.value = result.data
    keySource.value = result.source
    showSourceTip(result.source, "API Key")
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "API Key 加载失败"
  } finally {
    keysLoading.value = false
  }
}

async function fetchLogs() {
  logsLoading.value = true
  try {
    const result = await loadApiCallLogs({
      pageNum: logQuery.pageNum,
      pageSize: logQuery.pageSize,
      apiKeyId: logQuery.apiKeyId,
      status: logQuery.status || undefined
    })
    callLogs.value = result.data.records
    totalLogs.value = result.data.total
    logSource.value = result.source
    showSourceTip(result.source, "调用日志")
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "调用日志加载失败")
  } finally {
    logsLoading.value = false
  }
}

async function fetchUsageStats() {
  try {
    const result = await loadApiUsageStats()
    usageStats.value = result.data
    statsSource.value = result.source
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "使用统计加载失败")
  }
}

async function fetchOpenOverview() {
  try {
    openOverview.value = await getOpenOverview()
  } catch {
    openOverview.value = null
  }
}

async function submitCreateKey() {
  const keyName = createForm.keyName.trim()

  if (!keyName) {
    ElMessage.warning('请输入 API Key 名称')
    return
  }

  createLoading.value = true
  try {
    const payload: ApiKeyApplyPayload = {
      keyName,
      expireDays: createForm.expireDays
    }
    const result = await createApiKey(payload)
    createdKey.value = result.data
    createdKeySource.value = result.source
    resultDialogVisible.value = true
    createDialogVisible.value = false
    resetCreateForm()
    ElMessage.success(result.source === 'mock' ? '当前为模拟 API Key' : 'API Key 创建成功')
    await Promise.all([fetchKeys(), fetchUsageStats()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'API Key 创建失败')
  } finally {
    createLoading.value = false
  }
}

async function toggleKeyStatus(row: ApiKey) {
  const apiKeyId = row.apiKeyId
  const willEnable = normalizeKeyStatus(row.status) !== 'ACTIVE'
  actionLoadingId.value = apiKeyId
  try {
    const result = await setApiKeyEnabled(apiKeyId, willEnable)
    if (result.source === 'mock') {
      ElMessage.info('状态操作当前使用模拟兜底')
    } else {
      ElMessage.success(willEnable ? 'API Key 已启用' : 'API Key 已停用')
    }
    await fetchKeys()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  } finally {
    actionLoadingId.value = null
  }
}

async function deleteKey(row: ApiKey) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.keyName}」吗？删除后不可恢复。`, '删除 API Key', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  actionLoadingId.value = row.apiKeyId
  try {
    const result = await removeApiKey(row.apiKeyId)
    ElMessage.success(result.source === 'mock' ? '当前为模拟删除' : 'API Key 已删除')
    await fetchKeys()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'API Key 删除失败')
  } finally {
    actionLoadingId.value = null
  }
}

async function resetKey(row: ApiKey) {
  actionLoadingId.value = row.apiKeyId
  try {
    const result = await resetApiKey(row.apiKeyId)
    createdKey.value = result.data
    createdKeySource.value = result.source
    resultDialogVisible.value = true
    ElMessage.success(result.source === 'mock' ? '重置接口暂不可用，当前展示模拟结果' : 'API Key 已重置')
  } finally {
    actionLoadingId.value = null
  }
}

function resetCreateForm() {
  createForm.keyName = ''
  createForm.expireDays = 90
}

function handleLogPageChange(pageNum: number) {
  logQuery.pageNum = pageNum
  void fetchLogs()
}

function handleLogSizeChange(pageSize: number) {
  logQuery.pageSize = pageSize
  logQuery.pageNum = 1
  void fetchLogs()
}

function clearLogFilters() {
  logQuery.apiKeyId = undefined
  logQuery.status = ''
  logQuery.pageNum = 1
  void fetchLogs()
}

function normalizeKeyStatus(status?: string) {
  return status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED'
}

function keyStatusLabel(status?: string) {
  return normalizeKeyStatus(status) === 'ACTIVE' ? '启用' : status === 'EXPIRED' ? '过期' : '停用'
}

function keyStatusType(status?: string) {
  if (normalizeKeyStatus(status) === 'ACTIVE') return 'success'
  if (status === 'EXPIRED') return 'warning'
  return 'info'
}

function logStatusLabel(row: NormalizedApiCallLog) {
  return row.status === 'FAILED' || (row.statusCode ?? 200) >= 400 ? '失败' : '成功'
}

function logStatusType(row: NormalizedApiCallLog) {
  return row.status === 'FAILED' || (row.statusCode ?? 200) >= 400 ? 'danger' : 'success'
}

function keyText(key: ApiKey | null) {
  if (!key) return ''
  return key.apiKey || key.apiKeyPrefix || ''
}

function sourceLabel(source: DataSource) {
  if (source === 'remote') return '真实接口'
  if (source === 'mixed') return '混合数据'
  return '模拟数据'
}

function sourceType(source: DataSource) {
  if (source === 'remote') return 'success'
  if (source === 'mixed') return 'warning'
  return 'info'
}

function showSourceTip(source: DataSource, label: string) {
  if (source === 'mock') {
    ElMessage.info(`${label} 真实接口暂不可用，当前使用模拟数据`)
  }
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatDate(value?: string) {
  return value || '-'
}

function quotaText(row: ApiKey) {
  return row.dailyQuota ? `${formatNumber(row.dailyQuota)} 次/日` : '未返回'
}

async function copyText(value: string, successMessage = '已复制') {
  if (!value) {
    ElMessage.warning('没有可复制的内容')
    return
  }

  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(successMessage)
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}
</script>

<template>
  <section class="api-page">
    <div class="page-heading">
      <div>
        <h1>API 管理</h1>
        <p>查看 API Key、开放预测调用日志和接口使用状态</p>
      </div>
      <div class="heading-actions">
        <el-tag :type="sourceType(keySource)" effect="light">{{ sourceLabel(keySource) }}</el-tag>
        <el-button type="primary" @click="createDialogVisible = true">申请 API Key</el-button>
      </div>
    </div>

    <el-alert
      v-if="keySource !== 'remote' || logSource !== 'remote' || statsSource !== 'remote'"
      class="source-alert"
      title="部分数据当前使用 mock 兜底；真实接口恢复后会自动展示后端数据。"
      type="info"
      show-icon
      :closable="false"
    />

    <el-alert
      v-if="loadError"
      class="source-alert"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button size="small" type="primary" @click="fetchPageData">重试</el-button>
      </template>
    </el-alert>

    <div class="overview-grid">
      <div v-for="item in overviewCards" :key="item.label" class="overview-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </div>
    </div>

    <div class="api-layout">
      <section class="panel key-panel">
        <div class="panel-head">
          <div>
            <h2>API Key 列表</h2>
            <p>Key 明文仅创建时返回；列表通常只展示前缀和状态。</p>
          </div>
          <el-button :loading="keysLoading" @click="fetchKeys">刷新</el-button>
        </div>

        <el-table v-loading="keysLoading" :data="apiKeys" stripe>
          <el-table-column prop="keyName" label="API 名称" min-width="180" />
          <el-table-column label="Key 前缀" min-width="150">
            <template #default="{ row }: { row: ApiKey }">
              <code>{{ row.apiKeyPrefix || keyText(row) || '-' }}</code>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }: { row: ApiKey }">
              <el-tag :type="keyStatusType(row.status)" effect="light">{{ keyStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="限流" width="120">
            <template #default="{ row }: { row: ApiKey }">
              {{ row.rateLimitPerMinute ? `${row.rateLimitPerMinute}/分钟` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="日额度" width="140">
            <template #default="{ row }: { row: ApiKey }">
              {{ quotaText(row) }}
            </template>
          </el-table-column>
          <el-table-column label="到期时间" min-width="160">
            <template #default="{ row }: { row: ApiKey }">
              {{ formatDate(row.expireAt || row.expireTime) }}
            </template>
          </el-table-column>
          <el-table-column label="最后调用" min-width="160">
            <template #default="{ row }: { row: ApiKey }">
              {{ formatDate(row.lastUsedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }: { row: ApiKey }">
              <el-button
                size="small"
                text
                type="primary"
                :loading="actionLoadingId === row.apiKeyId"
                @click="toggleKeyStatus(row)"
              >
                {{ normalizeKeyStatus(row.status) === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
              <el-button size="small" text @click="resetKey(row)">重置</el-button>
              <el-button size="small" text type="danger" @click="deleteKey(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <aside class="side-stack">
        <section class="panel quota-panel">
          <div class="panel-head compact">
            <div>
              <h2>使用状态</h2>
              <p>基于调用日志计算，额度字段以后端返回为准。</p>
            </div>
          </div>
          <div class="quota-list">
            <div>
              <span>剩余额度</span>
              <strong>{{ formatNumber(remainingQuota) }}</strong>
            </div>
            <div>
              <span>累计调用</span>
              <strong>{{ formatNumber(usageStats?.summary.totalCalls ?? 0) }}</strong>
            </div>
            <div>
              <span>成功调用</span>
              <strong>{{ formatNumber(usageStats?.summary.successCalls ?? 0) }}</strong>
            </div>
          </div>
        </section>

        <section class="panel capability-panel">
          <div class="panel-head compact">
            <div>
              <h2>开放账户</h2>
              <p>展示开放平台账户接口状态。</p>
            </div>
          </div>
          <div class="capability-list">
            <div v-for="item in placeholderCapabilities" :key="item.title">
              <div>
                <span>{{ item.title }}</span>
                <strong>{{ item.value }}</strong>
              </div>
              <small>{{ item.note }}</small>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <section class="panel log-panel">
      <div class="panel-head">
        <div>
          <h2>调用日志</h2>
          <p>来源：GET /api/open/call-logs；后端不可用时展示模拟日志。</p>
        </div>
        <el-tag :type="sourceType(logSource)" effect="light">{{ sourceLabel(logSource) }}</el-tag>
      </div>

      <div class="log-filters">
        <el-select v-model="logQuery.apiKeyId" clearable placeholder="按 Key 筛选" @change="fetchLogs">
          <el-option v-for="item in apiKeys" :key="item.apiKeyId" :label="item.keyName" :value="item.apiKeyId" />
        </el-select>
        <el-select v-model="logQuery.status" clearable placeholder="按状态筛选" @change="fetchLogs">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-button @click="clearLogFilters">清空</el-button>
        <el-button :loading="logsLoading" @click="fetchLogs">刷新</el-button>
      </div>

      <el-table v-loading="logsLoading" :data="callLogs" stripe>
        <el-table-column prop="createdAt" label="时间" min-width="170" />
        <el-table-column prop="path" label="路径" min-width="190" />
        <el-table-column prop="method" label="方法" width="88" />
        <el-table-column label="状态" width="96">
          <template #default="{ row }: { row: NormalizedApiCallLog }">
            <el-tag :type="logStatusType(row)" effect="light">{{ logStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" label="HTTP" width="88" />
        <el-table-column label="耗时" width="100">
          <template #default="{ row }: { row: NormalizedApiCallLog }">
            {{ row.costTime }} ms
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型" min-width="140" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
      </el-table>

      <div class="pagination-row">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="totalLogs"
          :current-page="logQuery.pageNum"
          :page-size="logQuery.pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="handleLogPageChange"
          @size-change="handleLogSizeChange"
        />
      </div>
    </section>

    <section class="panel example-panel">
      <div class="panel-head">
        <div>
          <h2>开放 API 调用示例</h2>
          <p>文档要求请求头使用 X-API-KEY，开放预测路径为 /openapi/v1/predict。</p>
        </div>
        <el-button type="primary" plain @click="copyText(curlExample, '调用示例已复制')">复制 curl</el-button>
      </div>

      <div class="code-grid">
        <div class="code-block">
          <div class="code-title">
            <span>curl</span>
            <el-button size="small" text type="primary" @click="copyText(curlExample, 'curl 已复制')">复制</el-button>
          </div>
          <pre>{{ curlExample }}</pre>
        </div>
        <div class="code-block">
          <div class="code-title">
            <span>请求 JSON</span>
            <el-button
              size="small"
              text
              type="primary"
              @click="copyText(JSON.stringify(requestExample, null, 2), '请求 JSON 已复制')"
            >
              复制
            </el-button>
          </div>
          <pre>{{ JSON.stringify(requestExample, null, 2) }}</pre>
        </div>
        <div class="code-block">
          <div class="code-title">
            <span>响应 JSON</span>
            <el-button
              size="small"
              text
              type="primary"
              @click="copyText(JSON.stringify(responseExample, null, 2), '响应 JSON 已复制')"
            >
              复制
            </el-button>
          </div>
          <pre>{{ JSON.stringify(responseExample, null, 2) }}</pre>
        </div>
      </div>
    </section>

    <el-dialog v-model="createDialogVisible" title="申请 API Key" width="460px" @closed="resetCreateForm">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="Key 名称" required>
          <el-input v-model="createForm.keyName" maxlength="64" show-word-limit placeholder="例如：iTransformer 生产调用" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-input-number v-model="createForm.expireDays" :min="1" :max="365" :step="30" controls-position="right" />
          <span class="form-tip">单位：天。后端以 expireDays 字段接收。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreateKey">申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultDialogVisible" title="API Key 结果" width="520px">
      <div class="key-result">
        <el-alert
          v-if="createdKeySource === 'mock'"
          title="当前为模拟 API Key，真实接口恢复后请重新申请真实 Key。"
          type="info"
          show-icon
          :closable="false"
        />
        <p>完整 Key 通常只在创建时返回；如果后端仅返回前缀，页面只展示前缀。</p>
        <div class="key-box">
          <code>{{ keyText(createdKey) || '未返回 API Key' }}</code>
          <el-button size="small" @click="copyText(keyText(createdKey), 'API Key 已复制')">复制</el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.api-page {
  display: grid;
  gap: 18px;
}

.page-heading,
.panel-head,
.heading-actions,
.code-title,
.key-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.page-heading h1,
.panel-head h2 {
  margin: 0;
  color: #10274c;
}

.page-heading h1 {
  font-size: 28px;
}

.panel-head h2 {
  font-size: 18px;
}

.page-heading p,
.panel-head p,
.capability-list small,
.form-tip,
.key-result p {
  margin: 6px 0 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.source-alert {
  margin: 0;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.overview-card,
.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.overview-card {
  min-height: 112px;
  padding: 18px;
}

.overview-card span,
.quota-list span,
.capability-list span {
  color: var(--color-muted);
  font-size: 13px;
}

.overview-card strong {
  display: block;
  margin: 10px 0 6px;
  color: #10274c;
  font-size: 28px;
  line-height: 1;
}

.overview-card small {
  color: var(--color-muted);
}

.api-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.panel {
  min-width: 0;
  padding: 18px;
}

.key-panel,
.log-panel,
.example-panel {
  display: grid;
  gap: 16px;
}

.side-stack {
  display: grid;
  gap: 16px;
}

.compact {
  align-items: flex-start;
}

.quota-list,
.capability-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.quota-list div,
.capability-list > div {
  padding: 12px;
  border-radius: 8px;
  background: #f8fbff;
}

.quota-list strong,
.capability-list strong {
  display: block;
  margin-top: 6px;
  color: #10274c;
  font-size: 18px;
}

.capability-list > div {
  display: grid;
  gap: 4px;
}

.log-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.log-filters .el-select {
  width: 220px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
}

.code-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 12px;
}

.code-block {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  background: #fbfdff;
}

.code-block:first-child {
  grid-column: 1 / -1;
}

.code-title {
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border);
  color: #10274c;
  font-weight: 700;
}

pre {
  max-height: 280px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  color: #172033;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

code {
  color: #10274c;
  overflow-wrap: anywhere;
}

.form-tip {
  display: block;
  margin-left: 12px;
}

.key-result {
  display: grid;
  gap: 14px;
}

.key-box {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f8fbff;
}

.key-box code {
  min-width: 0;
}

@media (max-width: 1180px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .api-layout,
  .code-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .page-heading,
  .panel-head,
  .heading-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .log-filters .el-select {
    width: 100%;
  }
}
</style>
