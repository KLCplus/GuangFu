<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  applyMarketplaceApi,
  getMarketplaceModelCategories,
  loadMarketplaceModelDetail,
  loadMarketplaceModels,
  requestMarketplaceTrial
} from '../api/userPages'
import type { DataSource, MarketplaceTrialResult } from '../api/userPages'
import type { ApiKey } from '../api/open'
import type { MarketplaceModel, ModelCategory, ModelStatus } from '../data/mock'

type CategoryFilter = ModelCategory | 'ALL'
type StatusFilter = 'ALL' | 'ONLINE' | 'INACTIVE'
type PriceFilter = 'ALL' | 'TRIAL' | 'USAGE' | 'PACKAGE'

interface StatCard {
  label: string
  value: string
  note: string
}

const router = useRouter()

const loading = ref(false)
const detailLoading = ref(false)
const loadError = ref('')
const models = ref<MarketplaceModel[]>([])
const dataSource = ref<DataSource>('remote')
const selectedModel = ref<MarketplaceModel | null>(null)
const detailVisible = ref(false)
const detailSource = ref<DataSource>('remote')
const trialLoadingId = ref<number | null>(null)
const apiLoadingId = ref<number | null>(null)
const apiDialogVisible = ref(false)
const apiResult = ref<ApiKey | null>(null)
const apiResultSource = ref<DataSource>('remote')

const filters = ref({
  keyword: '',
  category: 'ALL' as CategoryFilter,
  status: 'ALL' as StatusFilter,
  price: 'ALL' as PriceFilter
})

const categoryOptions = computed(() => [
  { label: '全部', value: 'ALL' as CategoryFilter },
  ...getMarketplaceModelCategories()
])

const statusOptions: Array<{ label: string; value: StatusFilter }> = [
  { label: '全部', value: 'ALL' },
  { label: '在线', value: 'ONLINE' },
  { label: '维护中/离线', value: 'INACTIVE' }
]

const priceOptions: Array<{ label: string; value: PriceFilter }> = [
  { label: '全部', value: 'ALL' },
  { label: '免费试用', value: 'TRIAL' },
  { label: '按量计费', value: 'USAGE' },
  { label: '套餐', value: 'PACKAGE' }
]

const filteredModels = computed(() => {
  const keyword = filters.value.keyword.trim().toLowerCase()
  return models.value.filter((model) => {
    const matchesKeyword =
      !keyword ||
      model.modelName.toLowerCase().includes(keyword) ||
      model.description.toLowerCase().includes(keyword) ||
      model.tags.some((tag) => tag.toLowerCase().includes(keyword))
    const matchesCategory = filters.value.category === 'ALL' || model.category === filters.value.category
    const matchesStatus =
      filters.value.status === 'ALL' ||
      (filters.value.status === 'ONLINE' && normalizeStatus(model.modelStatus) === 'ONLINE') ||
      (filters.value.status === 'INACTIVE' && normalizeStatus(model.modelStatus) !== 'ONLINE')
    const matchesPrice =
      filters.value.price === 'ALL' ||
      (filters.value.price === 'TRIAL' && model.trialEnabled) ||
      (filters.value.price === 'USAGE' && model.price > 0) ||
      (filters.value.price === 'PACKAGE' && model.quota > 0)

    return matchesKeyword && matchesCategory && matchesStatus && matchesPrice
  })
})

const stats = computed<StatCard[]>(() => {
  const onlineCount = models.value.filter((model) => normalizeStatus(model.modelStatus) === 'ONLINE').length
  const trialCount = models.value.filter((model) => model.trialEnabled).length
  const todayCalls = models.value.reduce((sum, model) => sum + model.callCount, 0)
  const avgLatency = models.value.length
    ? Math.round(models.value.reduce((sum, model) => sum + latencyOf(model), 0) / models.value.length)
    : 0

  return [
    { label: '上线模型数', value: String(onlineCount), note: `共 ${models.value.length} 个模型` },
    { label: '可试用模型数', value: String(trialCount), note: '支持快速开通试用额度' },
    { label: '今日调用量', value: formatNumber(todayCalls), note: '按模型调用热度估算' },
    { label: '平均响应时延', value: `${avgLatency} ms`, note: '基于当前模型能力展示' }
  ]
})

const detailRequestExample = computed(() => {
  const model = selectedModel.value
  return {
    stationId: 1,
    modelName: model?.serviceModelName ?? model?.modelCode ?? 'iTransformer',
    input: [
      {
        time: '2026-07-09 10:00:00',
        power: 52.8,
        temperature: 31.2,
        irradiance: 820
      }
    ]
  }
})

const detailResponseExample = computed(() => ({
  taskId: 1001,
  taskNo: 'PRED-20260709-001',
  status: 'SUCCESS',
  modelName: selectedModel.value?.serviceModelName ?? selectedModel.value?.modelCode ?? 'iTransformer',
  predictions: [
    { timeOffset: 5, predictPower: 75.85 },
    { timeOffset: 10, predictPower: 78.12 }
  ],
  costTime: latencyOf(selectedModel.value)
}))

const curlExample = computed(() => {
  const path = selectedModel.value?.apiPath || '/openapi/v1/predict'
  return [
    `curl -X POST "${path}" \\`,
    '  -H "Content-Type: application/json" \\',
    '  -H "X-API-KEY: <your-api-key>" \\',
    `  -d '${JSON.stringify(detailRequestExample.value, null, 2)}'`
  ].join('\n')
})

onMounted(() => {
  void fetchModels()
})

async function fetchModels() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await loadMarketplaceModels()
    models.value = result.data
    dataSource.value = result.source
    if (result.source === 'mock') {
      ElMessage.info('真实接口暂不可用，当前展示模拟模型数据')
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '模型广场数据加载失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(model: MarketplaceModel) {
  selectedModel.value = model
  detailVisible.value = true
  detailLoading.value = true
  try {
    const result = await loadMarketplaceModelDetail(model.modelId)
    selectedModel.value = result.data
    detailSource.value = result.source
    if (result.source === 'mock') {
      ElMessage.info('当前展示模拟数据')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function startTrial(model: MarketplaceModel) {
  if (!model.trialEnabled) return
  trialLoadingId.value = model.modelId
  try {
    const result = await requestMarketplaceTrial(model.modelId)
    ElMessage.success(trialMessage(result.data, result.source))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试用开通失败')
  } finally {
    trialLoadingId.value = null
  }
}

async function applyApi(model: MarketplaceModel) {
  apiLoadingId.value = model.modelId
  try {
    const result = await applyMarketplaceApi(model.modelId, {
      keyName: `${model.modelName} API Key`,
      expireDays: 90
    })
    apiResult.value = result.data
    apiResultSource.value = result.source
    apiDialogVisible.value = true
    ElMessage.success(result.source === 'mock' ? '当前为模拟 API Key' : 'API Key 申请成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'API Key 申请失败')
  } finally {
    apiLoadingId.value = null
  }
}

function normalizeStatus(status: ModelStatus): ModelStatus {
  if (status === 'OFFLINE' || status === 'TESTING') return status
  return 'ONLINE'
}

function statusLabel(status: ModelStatus) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ONLINE') return '在线'
  if (normalized === 'TESTING') return '维护中'
  return '离线'
}

function statusType(status: ModelStatus) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ONLINE') return 'success'
  if (normalized === 'TESTING') return 'warning'
  return 'danger'
}

function latencyOf(model?: MarketplaceModel | null) {
  if (!model) return 126
  if (model.category === 'TIME_SERIES') return 96 + (model.modelId % 5) * 8
  if (model.category === 'VISION_FUSION') return 168 + (model.modelId % 4) * 18
  return 238 + (model.modelId % 4) * 24
}

function scenarioOf(model: MarketplaceModel) {
  if (model.applicableScenarios?.length) return model.applicableScenarios.join('、')
  if (model.category === 'TIME_SERIES') return '分钟级功率预测、历史数据补全、模型效果基线对比。'
  if (model.category === 'VISION_FUSION') return '云图特征融合、天气突变识别、短时功率波动预测。'
  return '连续云图外推、云层运动建模、视觉预测能力演示。'
}

function inputText(model: MarketplaceModel) {
  if (model.supportedInputModes?.length) return model.supportedInputModes.join('、')
  return `输入窗口 ${model.inputWindowMinutes} 分钟，采样间隔 ${model.inputFrameIntervalSeconds} 秒。请求体可包含功率、温度、辐照度等序列字段。`
}

function outputText(model: MarketplaceModel) {
  return `输出未来 ${model.outputSteps} 个预测步长，每步 ${model.outputStepMinutes} 分钟，返回预测功率和时间偏移。`
}

function canApply(model: MarketplaceModel) {
  return normalizeStatus(model.modelStatus) === 'ONLINE'
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function priceText(model: MarketplaceModel) {
  return model.billingRule || `¥${model.price}/${model.quota} ${model.unit}`
}

function trialMessage(result: MarketplaceTrialResult, source: DataSource) {
  const suffix = source === 'mock' ? '，当前为模拟试用数据' : ''
  return `已开通试用额度：${result.quota} 次${suffix}`
}

function apiKeyText(key: ApiKey | null) {
  if (!key) return ''
  return key.apiKey || key.apiKeyPrefix || ''
}

async function copyText(text: string, successText = '已复制') {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(successText)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function resetFilters() {
  filters.value = {
    keyword: '',
    category: 'ALL',
    status: 'ALL',
    price: 'ALL'
  }
}
</script>

<template>
  <section class="page-shell marketplace-page">
    <div class="page-title">
      <div>
        <h2>模型广场</h2>
        <p>浏览、试用和申请光伏预测模型 API</p>
      </div>
      <el-button :loading="loading" plain type="primary" @click="fetchModels">刷新</el-button>
    </div>

    <el-alert
      v-if="dataSource === 'mock'"
      title="真实接口暂不可用，当前页面使用模拟数据兜底。"
      type="info"
      show-icon
      :closable="false"
    />

    <div class="overview-grid">
      <div v-for="item in stats" :key="item.label" class="overview-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </div>
    </div>

    <section class="page-section filter-panel">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          clearable
          class="search-input"
          placeholder="搜索模型名称、描述或标签"
        />
        <el-button text type="primary" @click="resetFilters">重置筛选</el-button>
      </div>
      <div class="filter-row">
        <div class="filter-item">
          <span>分类</span>
          <el-segmented v-model="filters.category" :options="categoryOptions" />
        </div>
        <div class="filter-item">
          <span>状态</span>
          <el-segmented v-model="filters.status" :options="statusOptions" />
        </div>
        <div class="filter-item">
          <span>权益</span>
          <el-segmented v-model="filters.price" :options="priceOptions" />
        </div>
      </div>
    </section>

    <section v-loading="loading" class="models-area">
      <el-empty v-if="loadError" description="模型广场数据加载失败">
        <p class="empty-text">{{ loadError }}</p>
        <el-button type="primary" @click="fetchModels">重试</el-button>
      </el-empty>

      <el-empty v-else-if="!filteredModels.length && !loading" description="暂无符合条件的模型" />

      <div v-else class="model-grid">
        <article v-for="model in filteredModels" :key="model.modelId" class="model-card">
          <div class="card-head">
            <div>
              <h3>{{ model.modelName }}</h3>
              <p>{{ model.categoryName }}</p>
            </div>
            <el-tag :type="statusType(model.modelStatus)" effect="light">{{ statusLabel(model.modelStatus) }}</el-tag>
          </div>

          <p class="model-desc">{{ model.shortDescription || model.description }}</p>

          <div class="tag-row">
            <el-tag v-for="tag in model.tags" :key="tag" size="small">{{ tag }}</el-tag>
          </div>

          <div class="meta-grid">
            <div>
              <span>版本</span>
              <strong>{{ model.modelVersion || 'v1.0' }}</strong>
            </div>
            <div>
              <span>调用量</span>
              <strong>{{ formatNumber(model.callCount) }}</strong>
            </div>
            <div>
              <span>平均时延</span>
              <strong>{{ latencyOf(model) }} ms</strong>
            </div>
            <div>
              <span>来源</span>
              <strong>{{ model.provider || '-' }}</strong>
            </div>
          </div>

          <div class="billing-line">
            <span>{{ priceText(model) }}</span>
            <small>{{ formatNumber(model.quota) }} {{ model.unit }}</small>
          </div>

          <div class="card-actions">
            <el-button plain @click="openDetail(model)">查看详情</el-button>
            <el-button
              :disabled="!model.trialEnabled || !canApply(model)"
              :loading="trialLoadingId === model.modelId"
              @click="startTrial(model)"
            >
              免费试用
            </el-button>
            <el-button type="primary" :disabled="!canApply(model)" :loading="apiLoadingId === model.modelId" @click="applyApi(model)">
              申请 API
            </el-button>
          </div>
        </article>
      </div>
    </section>

    <el-drawer v-model="detailVisible" size="560px" title="模型详情">
      <div v-if="selectedModel" v-loading="detailLoading" class="detail-panel">
        <el-alert
          v-if="detailSource === 'mock'"
          title="当前展示模拟数据"
          type="info"
          show-icon
          :closable="false"
        />

        <div class="detail-title">
          <div>
            <h3>{{ selectedModel.modelName }}</h3>
            <p>{{ selectedModel.categoryName }} · {{ selectedModel.modelVersion }}</p>
          </div>
          <el-tag :type="statusType(selectedModel.modelStatus)">{{ statusLabel(selectedModel.modelStatus) }}</el-tag>
        </div>

        <p class="detail-desc">{{ selectedModel.description }}</p>

        <div class="tag-row detail-tags">
          <el-tag v-for="tag in selectedModel.tags" :key="tag" size="small">{{ tag }}</el-tag>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="模型家族">{{ selectedModel.modelFamily || selectedModel.categoryName }}</el-descriptions-item>
          <el-descriptions-item label="来源机构">{{ selectedModel.provider || '-' }}</el-descriptions-item>
          <el-descriptions-item label="论文">
            <a v-if="selectedModel.paperUrl" :href="selectedModel.paperUrl" target="_blank" rel="noreferrer">{{ selectedModel.paperTitle || selectedModel.paperUrl }}</a>
            <span v-else>{{ selectedModel.paperTitle || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="输入说明">{{ inputText(selectedModel) }}</el-descriptions-item>
          <el-descriptions-item label="输出说明">{{ outputText(selectedModel) }}</el-descriptions-item>
          <el-descriptions-item label="适用场景">{{ scenarioOf(selectedModel) }}</el-descriptions-item>
          <el-descriptions-item label="计费方式">{{ priceText(selectedModel) }}</el-descriptions-item>
          <el-descriptions-item label="开放路径">{{ selectedModel.apiPath }}</el-descriptions-item>
          <el-descriptions-item label="核心能力">{{ selectedModel.capabilities?.join('、') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="优势">{{ selectedModel.advantages?.join('、') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="局限">{{ selectedModel.limitations?.join('、') || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="selectedModel.metrics?.length" class="metric-panel">
          <h4>参考指标</h4>
          <el-table :data="selectedModel.metrics" size="small" border>
            <el-table-column prop="datasetName" label="数据集" min-width="120" />
            <el-table-column prop="mae" label="MAE" width="90" />
            <el-table-column prop="rmse" label="RMSE" width="90" />
            <el-table-column prop="mape" label="MAPE" width="90" />
            <el-table-column prop="r2Score" label="R2" width="90" />
          </el-table>
          <p class="metric-note">{{ String(selectedModel.referenceInfo?.metricSource || '指标为参考资料或离线评估，不代表当前平台实时实测。') }}</p>
        </div>

        <div class="code-block">
          <div class="code-title">
            <span>API 调用示例</span>
            <el-button size="small" text type="primary" @click="copyText(curlExample, '调用示例已复制')">
              复制
            </el-button>
          </div>
          <pre>{{ curlExample }}</pre>
        </div>

        <div class="code-grid">
          <div class="code-block">
            <div class="code-title">
              <span>请求参数示例</span>
              <el-button
                size="small"
                text
                type="primary"
                @click="copyText(JSON.stringify(detailRequestExample, null, 2), '请求示例已复制')"
              >
                复制
              </el-button>
            </div>
            <pre>{{ JSON.stringify(detailRequestExample, null, 2) }}</pre>
          </div>
          <div class="code-block">
            <div class="code-title">
              <span>响应示例</span>
              <el-button
                size="small"
                text
                type="primary"
                @click="copyText(JSON.stringify(detailResponseExample, null, 2), '响应示例已复制')"
              >
                复制
              </el-button>
            </div>
            <pre>{{ JSON.stringify(detailResponseExample, null, 2) }}</pre>
          </div>
        </div>

        <div class="drawer-actions">
          <el-button
            :disabled="!selectedModel.trialEnabled || !canApply(selectedModel)"
            :loading="trialLoadingId === selectedModel.modelId"
            @click="startTrial(selectedModel)"
          >
            免费试用
          </el-button>
          <el-button
            type="primary"
            :disabled="!canApply(selectedModel)"
            :loading="apiLoadingId === selectedModel.modelId"
            @click="applyApi(selectedModel)"
          >
            申请 API
          </el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="apiDialogVisible" width="460px" title="API Key 申请结果">
      <div class="api-result">
        <el-alert
          v-if="apiResultSource === 'mock'"
          title="当前为模拟 API Key"
          type="info"
          show-icon
          :closable="false"
        />
        <p>请妥善保存 API Key；如果后端仅返回前缀，页面只展示前缀。</p>
        <div class="key-box">
          <code>{{ apiKeyText(apiResult) || '未返回 API Key' }}</code>
          <el-button size="small" @click="copyText(apiKeyText(apiResult), 'API Key 已复制')">复制</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="apiDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="router.push('/api')">去 API 管理查看</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.marketplace-page {
  gap: 18px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.overview-card {
  min-height: 112px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.overview-card span,
.meta-grid span,
.filter-item span {
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

.overview-card small,
.billing-line small,
.empty-text {
  color: var(--color-muted);
}

.filter-panel {
  display: grid;
  gap: 14px;
}

.search-input {
  max-width: 420px;
}

.filter-row {
  display: grid;
  gap: 12px;
}

.filter-item {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.models-area {
  min-height: 240px;
}

.model-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.model-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 360px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.card-head,
.detail-title,
.code-title,
.billing-line,
.drawer-actions,
.key-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-head h3,
.detail-title h3 {
  margin: 0;
  color: #10274c;
  font-size: 18px;
}

.card-head p,
.detail-title p,
.detail-desc,
.model-desc,
.api-result p {
  margin: 6px 0 0;
  color: var(--color-muted);
  line-height: 1.7;
}

.model-desc {
  min-height: 72px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 28px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.meta-grid div {
  min-height: 58px;
  padding: 10px;
  border-radius: 8px;
  background: #f8fbff;
}

.meta-grid strong {
  display: block;
  margin-top: 6px;
  color: #172033;
}

.billing-line {
  margin-top: auto;
  padding-top: 4px;
}

.billing-line span {
  color: var(--color-primary);
  font-weight: 700;
}

.card-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.detail-panel {
  display: grid;
  gap: 18px;
}

.detail-desc {
  margin: 0;
}

.code-grid {
  display: grid;
  gap: 12px;
}

.code-block {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  background: #fbfdff;
}

.code-title {
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border);
  color: #10274c;
  font-weight: 700;
}

pre {
  max-height: 260px;
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

.drawer-actions {
  justify-content: flex-end;
  padding-top: 6px;
}

.api-result {
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
  overflow-wrap: anywhere;
  color: #10274c;
}

@media (max-width: 1200px) {
  .overview-grid,
  .model-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .overview-grid,
  .model-grid,
  .card-actions {
    grid-template-columns: 1fr;
  }

  .filter-item {
    grid-template-columns: 1fr;
  }
}
</style>
