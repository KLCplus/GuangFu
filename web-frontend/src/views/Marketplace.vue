<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getModel, getModels } from '../api/model'
import type { ModelDetail, ModelListItem, ModelType } from '../api/model'

interface TypeOption {
  label: string
  value: string
  count: number
}

interface DetailConfigItem {
  label: string
  value: string
}

const loading = ref(false)
const loadError = ref('')
const models = ref<ModelListItem[]>([])
const keyword = ref('')
const selectedTypes = ref<string[]>([])
const filterVisible = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const selectedModel = ref<ModelDetail | null>(null)

const typeOptions = computed<TypeOption[]>(() => {
  const counts = new Map<string, number>()
  models.value.forEach((model) => {
    const type = cleanText(model.modelType)
    if (type) counts.set(type, (counts.get(type) ?? 0) + 1)
  })

  return Array.from(counts.entries()).map(([value, count]) => ({
    value,
    count,
    label: modelTypeLabel(value)
  }))
})

const filteredModels = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLocaleLowerCase()

  return models.value.filter((model) => {
    const matchesType = !selectedTypes.value.length || selectedTypes.value.includes(model.modelType)
    const searchableText = [model.modelName, model.modelCode, model.description]
      .filter((value): value is string => Boolean(cleanText(value)))
      .join(' ')
      .toLocaleLowerCase()
    const matchesKeyword = !normalizedKeyword || searchableText.includes(normalizedKeyword)

    return matchesType && matchesKeyword
  })
})

const hasActiveFilters = computed(() => Boolean(keyword.value.trim()) || selectedTypes.value.length > 0)

const emptyDescription = computed(() => {
  if (!models.value.length) return '接口当前没有返回可展示的模型'
  if (keyword.value.trim() && selectedTypes.value.length) return '没有匹配当前搜索与类型筛选的模型'
  if (keyword.value.trim()) return '没有匹配当前搜索内容的模型'
  if (selectedTypes.value.length) return '没有匹配当前类型筛选的模型'
  return '暂无可展示的模型'
})

const detailConfigs = computed<DetailConfigItem[]>(() => {
  const model = selectedModel.value
  if (!model) return []

  const items: DetailConfigItem[] = []
  if (isNumber(model.inputWindowMinutes)) {
    items.push({ label: '输入窗口', value: `${model.inputWindowMinutes} 分钟` })
  }
  if (isNumber(model.inputFrameIntervalSeconds)) {
    items.push({ label: '输入间隔', value: `${model.inputFrameIntervalSeconds} 秒` })
  }
  if (isNumber(model.outputSteps)) {
    items.push({ label: '输出步数', value: `${model.outputSteps} 步` })
  }
  if (isNumber(model.outputStepMinutes)) {
    items.push({ label: '输出步长', value: `${model.outputStepMinutes} 分钟` })
  }
  return items
})

onMounted(() => {
  void fetchModels()
})

async function fetchModels() {
  loading.value = true
  loadError.value = ''
  try {
    models.value = await getModels()
    const availableTypes = new Set(models.value.map((model) => model.modelType))
    selectedTypes.value = selectedTypes.value.filter((type) => availableTypes.has(type))
  } catch (error) {
    models.value = []
    loadError.value = error instanceof Error ? error.message : '模型列表加载失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(model: ModelListItem) {
  selectedModel.value = { ...model }
  detailError.value = ''
  detailVisible.value = true
  await fetchDetail(model.modelId)
}

async function fetchDetail(modelId: number) {
  detailLoading.value = true
  detailError.value = ''
  try {
    const detail = await getModel(modelId)
    if (selectedModel.value?.modelId === modelId) selectedModel.value = detail
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '模型详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  selectedModel.value = null
  detailError.value = ''
}

function clearTypeFilters() {
  selectedTypes.value = []
}

function clearAllFilters() {
  keyword.value = ''
  selectedTypes.value = []
}

function modelTypeLabel(type?: ModelType) {
  const labels: Record<string, string> = {
    NUMERIC: '时序基线',
    FUSION: '云图 / 视觉融合',
    MULTIMODAL: '视频时空递归',
    IMAGE_TO_NUMERIC: '图像转数值',
    IMAGE: '图像模型'
  }
  const value = cleanText(type)
  return value ? labels[value] ?? value : ''
}

function modelTypeSymbol(type?: ModelType) {
  const symbols: Record<string, string> = {
    NUMERIC: '序',
    FUSION: '融',
    MULTIMODAL: '云',
    IMAGE_TO_NUMERIC: '图',
    IMAGE: '图'
  }
  return symbols[cleanText(type)] ?? '模'
}

function modelTypeClass(type?: ModelType) {
  const value = cleanText(type).toLowerCase().replace(/[^a-z0-9_-]/g, '-')
  return value ? `type-${value}` : 'type-default'
}

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    ONLINE: '已上线',
    TESTING: '测试中',
    OFFLINE: '已下线'
  }
  const value = cleanText(status)
  return value ? labels[value] ?? value : ''
}

function cleanText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

function formatSchema(value?: string) {
  const content = cleanText(value)
  if (!content) return ''
  try {
    return JSON.stringify(JSON.parse(content), null, 2)
  } catch {
    return content
  }
}
</script>

<template>
  <section class="page-shell marketplace-page">
    <section class="marketplace-toolbar" aria-label="模型搜索与筛选">
      <el-button class="filter-toggle" plain @click="filterVisible = !filterVisible">
        <span class="filter-icon" aria-hidden="true">☷</span>
        {{ filterVisible ? '隐藏筛选器' : '展开筛选器' }}
        <span v-if="selectedTypes.length" class="filter-count">{{ selectedTypes.length }}</span>
      </el-button>

      <el-input
        v-model="keyword"
        class="model-search"
        clearable
        size="large"
        placeholder="搜索模型名称"
        aria-label="搜索模型名称"
      >
        <template #prefix>
          <span class="search-icon" aria-hidden="true">⌕</span>
        </template>
      </el-input>
    </section>

    <section v-if="loading" class="model-grid" aria-label="正在加载模型">
      <article v-for="index in 6" :key="index" class="model-card skeleton-card">
        <el-skeleton animated :rows="4" />
      </article>
    </section>

    <section v-else-if="loadError" class="state-panel">
      <el-empty description="模型列表加载失败" :image-size="88">
        <p class="state-message">{{ loadError }}</p>
        <el-button type="primary" @click="fetchModels">重新加载</el-button>
      </el-empty>
    </section>

    <section v-else-if="!filteredModels.length" class="state-panel">
      <el-empty :description="emptyDescription" :image-size="88">
        <el-button v-if="hasActiveFilters" plain type="primary" @click="clearAllFilters">清空条件</el-button>
      </el-empty>
    </section>

    <section v-else class="model-grid" aria-live="polite">
      <button
        v-for="model in filteredModels"
        :key="model.modelId"
        type="button"
        class="model-card"
        :aria-label="`查看 ${model.modelName} 详情`"
        @click="openDetail(model)"
      >
        <span class="card-accent" :class="modelTypeClass(model.modelType)"></span>
        <span class="card-heading">
          <span class="model-symbol" :class="modelTypeClass(model.modelType)" aria-hidden="true">
            {{ modelTypeSymbol(model.modelType) }}
          </span>
          <span class="model-heading-copy">
            <strong>{{ model.modelName }}</strong>
            <small v-if="cleanText(model.modelCode)">{{ model.modelCode }}</small>
          </span>
          <span class="card-arrow" aria-hidden="true">→</span>
        </span>

        <span v-if="cleanText(model.description)" class="model-description">{{ model.description }}</span>

        <span class="card-footer">
          <span v-if="modelTypeLabel(model.modelType)" class="model-type-tag">
            {{ modelTypeLabel(model.modelType) }}
          </span>
          <span class="detail-hint">查看详情</span>
        </span>
      </button>
    </section>

    <el-drawer
      v-model="filterVisible"
      title="筛选模型"
      direction="ltr"
      size="320px"
      class="marketplace-filter-drawer"
    >
      <div class="filter-drawer-content">
        <div class="filter-section-heading">
          <div>
            <strong>模型类型</strong>
            <p>可多选，并与模型名称搜索同时生效</p>
          </div>
          <el-button v-if="selectedTypes.length" text type="primary" @click="clearTypeFilters">清空</el-button>
        </div>

        <el-checkbox-group v-if="typeOptions.length" v-model="selectedTypes" class="type-filter-list">
          <el-checkbox v-for="option in typeOptions" :key="option.value" :value="option.value" border>
            <span>{{ option.label }}</span>
            <small>{{ option.count }}</small>
          </el-checkbox>
        </el-checkbox-group>
        <el-empty v-else description="暂无可用类型" :image-size="64" />

      </div>
    </el-drawer>

    <el-drawer
      v-model="detailVisible"
      title="模型详情"
      direction="rtl"
      size="min(680px, 92vw)"
      class="marketplace-detail-drawer"
      @closed="closeDetail"
    >
      <div v-if="selectedModel" v-loading="detailLoading" class="detail-panel">
        <el-alert
          v-if="detailError"
          type="error"
          :closable="false"
          show-icon
          title="详情接口加载失败，以下仅展示列表接口已返回的信息。"
        >
          <template #default>
            <span>{{ detailError }}</span>
            <el-button text type="primary" @click="fetchDetail(selectedModel.modelId)">重试</el-button>
          </template>
        </el-alert>

        <section class="detail-hero">
          <span class="detail-symbol" :class="modelTypeClass(selectedModel.modelType)" aria-hidden="true">
            {{ modelTypeSymbol(selectedModel.modelType) }}
          </span>
          <div class="detail-heading-copy">
            <p v-if="cleanText(selectedModel.modelCode)" class="detail-code">{{ selectedModel.modelCode }}</p>
            <h2>{{ selectedModel.modelName }}</h2>
            <div class="detail-badges">
              <el-tag v-if="modelTypeLabel(selectedModel.modelType)" effect="plain">
                {{ modelTypeLabel(selectedModel.modelType) }}
              </el-tag>
              <el-tag v-if="statusLabel(selectedModel.status)" type="success" effect="light">
                {{ statusLabel(selectedModel.status) }}
              </el-tag>
            </div>
          </div>
        </section>

        <section v-if="cleanText(selectedModel.description)" class="detail-section">
          <p class="section-kicker">介绍</p>
          <h3>模型说明</h3>
          <p class="detail-description">{{ selectedModel.description }}</p>
        </section>

        <section v-if="detailConfigs.length" class="detail-section">
          <p class="section-kicker">配置</p>
          <h3>预测输入与输出</h3>
          <div class="config-grid">
            <div v-for="item in detailConfigs" :key="item.label" class="config-card">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section v-if="cleanText(selectedModel.inputSchema)" class="detail-section">
          <p class="section-kicker">Input</p>
          <h3>输入要求</h3>
          <pre class="schema-block">{{ formatSchema(selectedModel.inputSchema) }}</pre>
        </section>

        <section v-if="cleanText(selectedModel.outputSchema)" class="detail-section">
          <p class="section-kicker">Output</p>
          <h3>输出说明</h3>
          <pre class="schema-block">{{ formatSchema(selectedModel.outputSchema) }}</pre>
        </section>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.marketplace-page {
  gap: 18px;
}

.marketplace-toolbar {
  position: sticky;
  z-index: 5;
  top: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 0 2px;
  background: #f4f8fc;
}

.filter-toggle {
  min-width: 150px;
  height: 44px;
  border-color: #cbd9e9;
  color: #31435f;
  background: #ffffff;
  box-shadow: 0 6px 18px rgba(20, 65, 120, 0.06);
}

.filter-icon,
.search-icon {
  font-size: 20px;
  line-height: 1;
}

.filter-count {
  display: inline-grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  color: #ffffff;
  background: var(--color-primary);
  font-size: 12px;
}

.model-search {
  max-width: 680px;
}

.model-search :deep(.el-input__wrapper) {
  min-height: 44px;
  border: 1px solid #cbd9e9;
  box-shadow: none;
}

.model-search :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(29, 111, 220, 0.1);
}

.model-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.model-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 252px;
  padding: 22px;
  overflow: hidden;
  flex-direction: column;
  gap: 18px;
  border: 1px solid #dce6f1;
  border-radius: 12px;
  color: #172033;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(20, 65, 120, 0.06);
  text-align: left;
  font: inherit;
  cursor: pointer;
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.model-card:hover,
.model-card:focus-visible {
  border-color: rgba(29, 111, 220, 0.45);
  outline: none;
  box-shadow: 0 16px 34px rgba(20, 65, 120, 0.12);
  transform: translateY(-3px);
}

.card-accent {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  background: #78aef4;
}

.card-accent.type-fusion,
.model-symbol.type-fusion,
.detail-symbol.type-fusion {
  background: #e8f7f2;
  color: #178563;
}

.card-accent.type-multimodal,
.model-symbol.type-multimodal,
.detail-symbol.type-multimodal {
  background: #f0ecff;
  color: #6750c7;
}

.card-accent.type-image_to_numeric,
.card-accent.type-image,
.model-symbol.type-image_to_numeric,
.model-symbol.type-image,
.detail-symbol.type-image_to_numeric,
.detail-symbol.type-image {
  background: #fff3df;
  color: #b36b05;
}

.card-accent.type-fusion,
.card-accent.type-multimodal,
.card-accent.type-image_to_numeric,
.card-accent.type-image {
  color: transparent;
}

.card-accent.type-fusion {
  background: #38b98d;
}

.card-accent.type-multimodal {
  background: #8069dd;
}

.card-accent.type-image_to_numeric,
.card-accent.type-image {
  background: #e6a23c;
}

.card-heading {
  display: flex;
  align-items: center;
  gap: 13px;
}

.model-symbol,
.detail-symbol {
  display: inline-grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  border-radius: 12px;
  color: #1d6fdc;
  background: #e8f2ff;
  font-size: 20px;
  font-weight: 800;
}

.model-heading-copy,
.detail-heading-copy {
  min-width: 0;
}

.model-heading-copy strong {
  display: block;
  overflow: hidden;
  color: #10274c;
  font-size: 17px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-heading-copy small {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: #7a89a0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-arrow {
  margin-left: auto;
  color: #9aabc0;
  font-size: 20px;
  transition: color 180ms ease, transform 180ms ease;
}

.model-card:hover .card-arrow {
  color: var(--color-primary);
  transform: translateX(3px);
}

.model-description {
  display: -webkit-box;
  overflow: hidden;
  color: #5f7088;
  line-height: 1.75;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
}

.model-type-tag {
  display: inline-flex;
  max-width: 78%;
  min-height: 28px;
  align-items: center;
  padding: 4px 10px;
  overflow: hidden;
  border: 1px solid #cfe0f4;
  border-radius: 7px;
  color: #27649f;
  background: #f2f7fd;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-hint {
  color: #8796aa;
  font-size: 13px;
}

.skeleton-card {
  cursor: default;
}

.skeleton-card:hover {
  border-color: #dce6f1;
  box-shadow: 0 8px 24px rgba(20, 65, 120, 0.06);
  transform: none;
}

.state-panel {
  display: grid;
  min-height: 360px;
  place-items: center;
  border: 1px dashed #cfdae8;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
}

.state-message {
  max-width: 520px;
  margin: 0 0 14px;
  color: var(--color-muted);
  line-height: 1.7;
  text-align: center;
}

.filter-drawer-content {
  display: grid;
  gap: 20px;
}

.filter-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.filter-section-heading strong {
  color: #10274c;
  font-size: 16px;
}

.filter-section-heading p {
  margin: 6px 0 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
}

.type-filter-list {
  display: grid;
  gap: 10px;
}

.type-filter-list :deep(.el-checkbox) {
  width: 100%;
  height: 42px;
  margin: 0;
  padding: 0 12px;
  border-radius: 8px;
}

.type-filter-list :deep(.el-checkbox__label) {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.type-filter-list small {
  color: #94a2b5;
}

.detail-panel {
  display: grid;
  gap: 24px;
  padding-bottom: 20px;
}

.detail-hero {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 18px;
  border: 1px solid #dce6f1;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 70%);
}

.detail-symbol {
  width: 58px;
  height: 58px;
  flex-basis: 58px;
  font-size: 23px;
}

.detail-code,
.section-kicker {
  margin: 0 0 5px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.detail-heading-copy h2 {
  margin: 0;
  color: #10274c;
  font-size: 22px;
  line-height: 1.4;
}

.detail-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.detail-section {
  padding-top: 2px;
}

.detail-section h3 {
  margin: 0 0 12px;
  color: #10274c;
  font-size: 17px;
}

.detail-description {
  margin: 0;
  color: #566980;
  line-height: 1.9;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.config-card {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid #dce6f1;
  border-radius: 10px;
  background: #fbfdff;
}

.config-card span {
  color: #7a89a0;
  font-size: 12px;
}

.config-card strong {
  color: #172033;
  font-size: 18px;
}

.schema-block {
  max-height: 300px;
  margin: 0;
  padding: 16px;
  overflow: auto;
  border: 1px solid #d8e3f0;
  border-radius: 10px;
  color: #33445d;
  background: #f7faff;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1280px) {
  .model-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .marketplace-toolbar {
    position: static;
    align-items: stretch;
    flex-direction: column;
  }

  .filter-toggle,
  .model-search {
    width: 100%;
    max-width: none;
  }

  .model-grid,
  .config-grid {
    grid-template-columns: 1fr;
  }

  .model-card {
    min-height: 230px;
  }
}
</style>
