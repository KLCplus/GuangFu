<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getModel, getModels } from '../api/model'
import type { ModelDetail, ModelListItem, ModelType } from '../api/model'
import { getModelIcon } from '../assets/model-icons/svg'
import { getModelArchitectures, getModelCapabilities } from '../data/modelFilterTaxonomy'

interface FilterOption {
  label: string
  value: string
  count: number
}

interface FilterGroup {
  key: FilterKey
  label: string
  description: string
  options: FilterOption[]
  kind: 'primary' | 'more'
  defaultLimit: number
}

type FilterKey = 'types' | 'architectures' | 'capabilities' | 'providers' | 'statuses' | 'years'

interface SelectedFilterChip {
  key: FilterKey | 'keyword'
  value: string
  label: string
}

interface DetailConfigItem {
  label: string
  value: string
}

const loading = ref(false)
const loadError = ref('')
const models = ref<ModelListItem[]>([])
const keyword = ref('')
const SIDEBAR_WIDTH_STORAGE_KEY = 'marketplace-filter-sidebar-width'
const MIN_SIDEBAR_WIDTH = 260
const DEFAULT_SIDEBAR_WIDTH = 320
const MAX_SIDEBAR_WIDTH = 520

const selectedFilters = ref<Record<FilterKey, string[]>>({
  types: [],
  architectures: [],
  capabilities: [],
  providers: [],
  statuses: [],
  years: []
})
const filterVisible = ref(false)
const moreFiltersVisible = ref(false)
const expandedGroups = ref<Partial<Record<FilterKey, boolean>>>({})
const marketplaceWorkspace = ref<HTMLElement | null>(null)
const sidebarWidth = ref(readStoredSidebarWidth())

let resizeStartX = 0
let resizeStartWidth = DEFAULT_SIDEBAR_WIDTH
let resizeMaxWidth = MAX_SIDEBAR_WIDTH
let resizing = false

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const selectedModel = ref<ModelDetail | null>(null)

const typeOptions = computed(() => buildOptions(models.value.map((model) => model.modelType), modelTypeLabel))
const architectureOptions = computed(() =>
  buildOptions(models.value.flatMap(getModelArchitectures), (value) => value, 2)
)
const capabilityOptions = computed(() =>
  buildOptions(models.value.flatMap(getModelCapabilities), (value) => value, 2)
)
const providerOptions = computed(() => buildOptions(models.value.map((model) => model.provider)))
const statusOptions = computed(() => buildOptions(models.value.map((model) => model.status), statusLabel))
const yearOptions = computed(() =>
  buildOptions(models.value.map((model) => (model.releaseYear ? String(model.releaseYear) : '')))
    .sort((left, right) => Number(right.value) - Number(left.value))
)

const filterGroups = computed<FilterGroup[]>(() => {
  const groups: FilterGroup[] = [
    { key: 'types', label: '模型类型', description: '按预测任务和输入形态分类', options: typeOptions.value, kind: 'primary', defaultLimit: 99 },
    { key: 'architectures', label: '架构体系', description: '基于真实家族与 modelCode 规范化归类', options: architectureOptions.value, kind: 'primary', defaultLimit: 6 },
    { key: 'capabilities', label: '能力标签', description: '由真实标签与 modelCode 归一化生成', options: capabilityOptions.value, kind: 'primary', defaultLimit: 6 },
    { key: 'providers', label: '来源机构', description: '来自接口 provider 字段', options: providerOptions.value, kind: 'more', defaultLimit: 6 },
    { key: 'statuses', label: '可用状态', description: '来自接口 status 字段', options: statusOptions.value, kind: 'more', defaultLimit: 6 },
    { key: 'years', label: '发布年份', description: '来自接口 releaseYear 字段', options: yearOptions.value, kind: 'more', defaultLimit: 6 }
  ]
  return groups.filter((group) => group.options.length > 0)
})

const primaryFilterGroups = computed(() => filterGroups.value.filter((group) => group.kind === 'primary'))
const moreFilterGroups = computed(() => filterGroups.value.filter((group) => group.kind === 'more'))

const activeFilterCount = computed(() =>
  Object.values(selectedFilters.value).reduce((total, values) => total + values.length, 0)
)

const selectedFilterChips = computed<SelectedFilterChip[]>(() => {
  const chips: SelectedFilterChip[] = []
  const searchText = keyword.value.trim()
  if (searchText) chips.push({ key: 'keyword', value: searchText, label: `搜索：${searchText}` })
  for (const group of filterGroups.value) {
    for (const value of selectedFilters.value[group.key]) {
      const option = group.options.find((item) => item.value === value)
      if (option) chips.push({ key: group.key, value, label: `${group.label}：${option.label}` })
    }
  }
  return chips
})

const filteredModels = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLocaleLowerCase()

  return models.value.filter((model) => {
    const matchesType = matchesSelected('types', model.modelType)
    const matchesArchitecture = matchesAnySelected('architectures', getModelArchitectures(model))
    const matchesCapability = matchesAnySelected('capabilities', getModelCapabilities(model))
    const matchesProvider = matchesSelected('providers', model.provider)
    const matchesStatus = matchesSelected('statuses', model.status)
    const matchesYear = matchesSelected('years', model.releaseYear ? String(model.releaseYear) : '')
    const searchableText = [
      model.modelName,
      model.modelCode,
      model.description,
      model.shortDescription,
      model.modelFamily,
      model.provider,
      ...(model.tags ?? [])
    ]
      .filter((value): value is string => Boolean(cleanText(value)))
      .join(' ')
      .toLocaleLowerCase()
    const matchesKeyword = !normalizedKeyword || searchableText.includes(normalizedKeyword)

    return matchesType && matchesArchitecture && matchesCapability && matchesProvider && matchesStatus && matchesYear && matchesKeyword
  })
})

const hasActiveFilters = computed(() => Boolean(keyword.value.trim()) || activeFilterCount.value > 0)

const emptyDescription = computed(() => {
  if (!models.value.length) return '接口当前没有返回可展示的模型'
  if (keyword.value.trim() && activeFilterCount.value) return '没有匹配当前搜索与筛选条件的模型'
  if (keyword.value.trim()) return '没有匹配当前搜索内容的模型'
  if (activeFilterCount.value) return '没有匹配当前筛选条件的模型'
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

onBeforeUnmount(() => {
  stopSidebarResize()
})

async function fetchModels() {
  loading.value = true
  loadError.value = ''
  try {
    models.value = await getModels()
    pruneUnavailableFilters()
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

function clearFilterGroup(key: FilterKey) {
  selectedFilters.value[key] = []
}

function removeSelectedFilter(chip: SelectedFilterChip) {
  if (chip.key === 'keyword') {
    keyword.value = ''
    return
  }
  selectedFilters.value[chip.key] = selectedFilters.value[chip.key].filter((value) => value !== chip.value)
}

function clearAllFilters() {
  keyword.value = ''
  for (const key of Object.keys(selectedFilters.value) as FilterKey[]) {
    selectedFilters.value[key] = []
  }
}

function matchesSelected(key: FilterKey, value: unknown) {
  const selected = selectedFilters.value[key]
  const normalized = cleanText(value)
  return !selected.length || Boolean(normalized && selected.includes(normalized))
}

function matchesAnySelected(key: FilterKey, values: string[]) {
  const selected = selectedFilters.value[key]
  return !selected.length || values.some((value) => selected.includes(value))
}

function buildOptions(
  values: unknown[],
  labelFormatter: (value: string) => string = (value) => value,
  minimumCount = 1
): FilterOption[] {
  const counts = new Map<string, number>()
  values.forEach((value) => {
    const normalized = cleanText(value)
    if (normalized) counts.set(normalized, (counts.get(normalized) ?? 0) + 1)
  })
  return Array.from(counts.entries())
    .filter(([, count]) => count >= minimumCount)
    .map(([value, count]) => ({ value, count, label: labelFormatter(value) }))
    .sort((left, right) => right.count - left.count || left.label.localeCompare(right.label, 'zh-CN'))
}

function pruneUnavailableFilters() {
  const available: Record<FilterKey, Set<string>> = {
    types: new Set(typeOptions.value.map((option) => option.value)),
    architectures: new Set(architectureOptions.value.map((option) => option.value)),
    capabilities: new Set(capabilityOptions.value.map((option) => option.value)),
    providers: new Set(providerOptions.value.map((option) => option.value)),
    statuses: new Set(statusOptions.value.map((option) => option.value)),
    years: new Set(yearOptions.value.map((option) => option.value))
  }
  for (const key of Object.keys(selectedFilters.value) as FilterKey[]) {
    selectedFilters.value[key] = selectedFilters.value[key].filter((value) => available[key].has(value))
  }
}

function visibleOptions(group: FilterGroup) {
  if (group.key === 'types' || expandedGroups.value[group.key]) return group.options
  return group.options.slice(0, group.defaultLimit)
}

function hasHiddenOptions(group: FilterGroup) {
  return group.key !== 'types' && group.options.length > group.defaultLimit
}

function toggleGroupExpanded(key: FilterKey) {
  expandedGroups.value[key] = !expandedGroups.value[key]
}

function readStoredSidebarWidth() {
  try {
    const rawValue = localStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY)
    if (rawValue === null) return DEFAULT_SIDEBAR_WIDTH
    const stored = Number(rawValue)
    return Number.isFinite(stored) ? clamp(stored, MIN_SIDEBAR_WIDTH, MAX_SIDEBAR_WIDTH) : DEFAULT_SIDEBAR_WIDTH
  } catch {
    return DEFAULT_SIDEBAR_WIDTH
  }
}

function storeSidebarWidth() {
  try {
    localStorage.setItem(SIDEBAR_WIDTH_STORAGE_KEY, String(sidebarWidth.value))
  } catch {
    // 浏览器禁用本地存储时仍保留本次页面内的拖拽结果。
  }
}

function startSidebarResize(event: PointerEvent) {
  if (event.button !== 0 || !marketplaceWorkspace.value) return
  const workspaceWidth = marketplaceWorkspace.value.getBoundingClientRect().width
  resizeStartX = event.clientX
  resizeStartWidth = sidebarWidth.value
  resizeMaxWidth = Math.max(MIN_SIDEBAR_WIDTH, Math.min(MAX_SIDEBAR_WIDTH, workspaceWidth * 0.45))
  resizing = true
  document.body.classList.add('is-resizing-model-filter')
  window.addEventListener('pointermove', handleSidebarResize)
  window.addEventListener('pointerup', stopSidebarResize)
  window.addEventListener('pointercancel', stopSidebarResize)
  event.preventDefault()
}

function handleSidebarResize(event: PointerEvent) {
  if (!resizing) return
  sidebarWidth.value = Math.round(clamp(resizeStartWidth + event.clientX - resizeStartX, MIN_SIDEBAR_WIDTH, resizeMaxWidth))
}

function stopSidebarResize() {
  if (!resizing) return
  resizing = false
  storeSidebarWidth()
  document.body.classList.remove('is-resizing-model-filter')
  window.removeEventListener('pointermove', handleSidebarResize)
  window.removeEventListener('pointerup', stopSidebarResize)
  window.removeEventListener('pointercancel', stopSidebarResize)
}

function resizeSidebarWithKeyboard(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  const direction = event.key === 'ArrowLeft' ? -1 : 1
  const workspaceWidth = marketplaceWorkspace.value?.getBoundingClientRect().width ?? MAX_SIDEBAR_WIDTH / 0.45
  const maxWidth = Math.max(MIN_SIDEBAR_WIDTH, Math.min(MAX_SIDEBAR_WIDTH, workspaceWidth * 0.45))
  sidebarWidth.value = Math.round(clamp(sidebarWidth.value + direction * 16, MIN_SIDEBAR_WIDTH, maxWidth))
  storeSidebarWidth()
  event.preventDefault()
}

function clamp(value: number, minimum: number, maximum: number) {
  return Math.min(maximum, Math.max(minimum, value))
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

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    ONLINE: '已上线',
    TESTING: '测试中',
    OFFLINE: '仅展示'
  }
  const value = cleanText(status)
  return value ? labels[value] ?? value : ''
}

function statusTagType(status?: string) {
  const value = cleanText(status)
  if (value === 'ONLINE') return 'success'
  if (value === 'TESTING') return 'warning'
  return 'info'
}

function displayDescription(model: ModelListItem | ModelDetail) {
  return cleanText(model.shortDescription) || cleanText(model.description)
}

function hasTextList(value?: string[]) {
  return Array.isArray(value) && value.some((item) => Boolean(cleanText(item)))
}

function formatMetric(value: unknown, suffix = '') {
  return isNumber(value) ? `${Number(value).toFixed(3)}${suffix}` : '-'
}

function cleanText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

function hasSchema(value?: string | Record<string, unknown>) {
  if (!value) return false
  if (typeof value === 'object') return Object.keys(value).length > 0
  return Boolean(cleanText(value))
}

function formatSchema(value?: string | Record<string, unknown>) {
  if (!value) return ''
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
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
    <div class="page-heading">
      <h1>模型广场</h1>
    </div>

    <section class="marketplace-toolbar" aria-label="模型搜索与筛选">
      <el-button class="filter-toggle" plain @click="filterVisible = !filterVisible">
        <span class="filter-icon" aria-hidden="true">☷</span>
        {{ filterVisible ? '隐藏筛选器' : '展开筛选器' }}
        <span v-if="activeFilterCount" class="filter-count">{{ activeFilterCount }}</span>
      </el-button>

      <el-input
        v-model="keyword"
        class="model-search"
        clearable
        size="large"
        placeholder="搜索模型名称、标签或来源"
        aria-label="搜索模型名称、标签或来源"
      >
        <template #prefix>
          <span class="search-icon" aria-hidden="true">⌕</span>
        </template>
      </el-input>
    </section>

    <div
      ref="marketplaceWorkspace"
      class="marketplace-workspace"
      :class="{ 'has-filter-sidebar': filterVisible }"
      :style="{ '--filter-sidebar-width': `${sidebarWidth}px` }"
    >
      <aside v-if="filterVisible" class="filter-sidebar" aria-label="模型筛选条件">
        <div class="filter-sidebar-head">
          <div>
            <strong>筛选模型</strong>
            <p>组内多选，筛选组之间组合生效</p>
          </div>
          <el-button v-if="activeFilterCount" text type="primary" @click="clearAllFilters">全部清空</el-button>
        </div>

        <div class="filter-sidebar-body">
          <section v-for="group in primaryFilterGroups" :key="group.key" class="filter-group">
            <div class="filter-section-heading">
              <div>
                <strong>{{ group.label }}</strong>
                <p>{{ group.description }}</p>
              </div>
              <el-button
                v-if="selectedFilters[group.key].length"
                text
                type="primary"
                @click="clearFilterGroup(group.key)"
              >
                清空
              </el-button>
            </div>

            <el-checkbox-group
              v-model="selectedFilters[group.key]"
              class="filter-option-list"
              :class="{ 'type-option-list': group.key === 'types' }"
            >
              <el-checkbox v-for="option in visibleOptions(group)" :key="option.value" :value="option.value" border>
                <el-tooltip :content="option.label" placement="top" :disabled="option.label.length <= 9">
                  <span class="filter-option-label">{{ option.label }}</span>
                </el-tooltip>
                <small>{{ option.count }}</small>
              </el-checkbox>
            </el-checkbox-group>

            <el-button
              v-if="hasHiddenOptions(group)"
              class="group-more-button"
              text
              type="primary"
              @click="toggleGroupExpanded(group.key)"
            >
              {{ expandedGroups[group.key] ? '收起' : `更多（${group.options.length - group.defaultLimit}）` }}
            </el-button>
          </section>

          <section v-if="moreFilterGroups.length" class="more-filter-section">
            <button type="button" class="more-filter-toggle" @click="moreFiltersVisible = !moreFiltersVisible">
              <span>更多筛选</span>
              <small>来源、状态、年份</small>
              <span class="toggle-arrow" :class="{ expanded: moreFiltersVisible }">⌄</span>
            </button>

            <div v-if="moreFiltersVisible" class="more-filter-groups">
              <section v-for="group in moreFilterGroups" :key="group.key" class="filter-group compact-filter-group">
                <div class="filter-section-heading">
                  <div>
                    <strong>{{ group.label }}</strong>
                    <p>{{ group.description }}</p>
                  </div>
                  <el-button
                    v-if="selectedFilters[group.key].length"
                    text
                    type="primary"
                    @click="clearFilterGroup(group.key)"
                  >
                    清空
                  </el-button>
                </div>

                <el-checkbox-group v-model="selectedFilters[group.key]" class="filter-option-list">
                  <el-checkbox v-for="option in visibleOptions(group)" :key="option.value" :value="option.value" border>
                    <el-tooltip :content="option.label" placement="top" :disabled="option.label.length <= 9">
                      <span class="filter-option-label">{{ option.label }}</span>
                    </el-tooltip>
                    <small>{{ option.count }}</small>
                  </el-checkbox>
                </el-checkbox-group>

                <el-button
                  v-if="hasHiddenOptions(group)"
                  class="group-more-button"
                  text
                  type="primary"
                  @click="toggleGroupExpanded(group.key)"
                >
                  {{ expandedGroups[group.key] ? '收起' : `更多（${group.options.length - group.defaultLimit}）` }}
                </el-button>
              </section>
            </div>
          </section>
        </div>
      </aside>

      <div
        v-if="filterVisible"
        class="filter-resizer"
        role="separator"
        aria-label="调整筛选栏宽度"
        aria-orientation="vertical"
        :aria-valuemin="MIN_SIDEBAR_WIDTH"
        :aria-valuemax="MAX_SIDEBAR_WIDTH"
        :aria-valuenow="sidebarWidth"
        tabindex="0"
        @pointerdown="startSidebarResize"
        @keydown="resizeSidebarWithKeyboard"
      >
        <span></span>
      </div>

      <main class="model-results">
        <div v-if="selectedFilterChips.length" class="selected-filter-bar">
          <div class="selected-filter-chips">
            <el-tag
              v-for="chip in selectedFilterChips"
              :key="`${chip.key}-${chip.value}`"
              closable
              effect="plain"
              @close="removeSelectedFilter(chip)"
            >
              {{ chip.label }}
            </el-tag>
          </div>
          <div class="selected-filter-actions">
            <span>{{ filteredModels.length }} / {{ models.length }} 个模型</span>
            <el-button text type="primary" @click="clearAllFilters">清空全部</el-button>
          </div>
        </div>

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
            <span class="card-heading">
              <img
                class="model-icon"
                :src="getModelIcon(model.modelCode, model.modelType)"
                alt=""
                width="64"
                height="64"
              />
              <span class="model-heading-copy">
                <strong>{{ model.modelName }}</strong>
              </span>
            </span>

            <span v-if="displayDescription(model)" class="model-description">{{ displayDescription(model) }}</span>

            <span v-if="hasTextList(model.tags)" class="tag-row">
              <span v-for="tag in model.tags" :key="tag" class="tag-chip">{{ tag }}</span>
            </span>
          </button>
        </section>
      </main>
    </div>

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
          <img
            class="detail-icon"
            :src="getModelIcon(selectedModel.modelCode, selectedModel.modelType)"
            alt=""
            width="76"
            height="76"
          />
          <div class="detail-heading-copy">
            <p v-if="cleanText(selectedModel.modelCode)" class="detail-code">{{ selectedModel.modelCode }}</p>
            <h2>{{ selectedModel.modelName }}</h2>
            <div class="detail-badges">
              <el-tag v-if="modelTypeLabel(selectedModel.modelType)" effect="plain">
                {{ modelTypeLabel(selectedModel.modelType) }}
              </el-tag>
              <el-tag v-if="statusLabel(selectedModel.status)" :type="statusTagType(selectedModel.status)" effect="light">
                {{ statusLabel(selectedModel.status) }}
              </el-tag>
              <el-tag v-if="cleanText(selectedModel.provider)" effect="plain">
                {{ selectedModel.provider }}
              </el-tag>
            </div>
          </div>
        </section>

        <section v-if="displayDescription(selectedModel) || cleanText(selectedModel.description)" class="detail-section">
          <p class="section-kicker">介绍</p>
          <h3>模型说明</h3>
          <p v-if="displayDescription(selectedModel)" class="detail-description">{{ displayDescription(selectedModel) }}</p>
          <p v-if="cleanText(selectedModel.description) && cleanText(selectedModel.description) !== displayDescription(selectedModel)" class="detail-description">
            {{ selectedModel.description }}
          </p>
        </section>

        <section v-if="hasTextList(selectedModel.tags) || cleanText(selectedModel.modelFamily) || cleanText(selectedModel.paperUrl) || cleanText(selectedModel.sourceUrl)" class="detail-section">
          <p class="section-kicker">Reference</p>
          <h3>来源与标签</h3>
          <div v-if="hasTextList(selectedModel.tags)" class="tag-row detail-tags">
            <span v-for="tag in selectedModel.tags" :key="tag" class="tag-chip">{{ tag }}</span>
          </div>
          <dl class="meta-list">
            <div v-if="cleanText(selectedModel.modelFamily)">
              <dt>模型家族</dt>
              <dd>{{ selectedModel.modelFamily }}</dd>
            </div>
            <div v-if="cleanText(selectedModel.provider)">
              <dt>来源机构</dt>
              <dd>{{ selectedModel.provider }}</dd>
            </div>
            <div v-if="selectedModel.releaseYear">
              <dt>发布年份</dt>
              <dd>{{ selectedModel.releaseYear }}</dd>
            </div>
            <div v-if="cleanText(selectedModel.paperUrl) || cleanText(selectedModel.paperTitle)">
              <dt>论文</dt>
              <dd>
                <a v-if="cleanText(selectedModel.paperUrl)" :href="selectedModel.paperUrl" target="_blank" rel="noreferrer">
                  {{ selectedModel.paperTitle || selectedModel.paperUrl }}
                </a>
                <span v-else>{{ selectedModel.paperTitle }}</span>
              </dd>
            </div>
            <div v-if="cleanText(selectedModel.sourceUrl)">
              <dt>项目地址</dt>
              <dd><a :href="selectedModel.sourceUrl" target="_blank" rel="noreferrer">{{ selectedModel.sourceUrl }}</a></dd>
            </div>
          </dl>
        </section>

        <section v-if="hasTextList(selectedModel.capabilities) || hasTextList(selectedModel.applicableScenarios) || hasTextList(selectedModel.advantages) || hasTextList(selectedModel.limitations)" class="detail-section">
          <p class="section-kicker">Capability</p>
          <h3>能力说明</h3>
          <div class="info-grid">
            <div v-if="hasTextList(selectedModel.capabilities)" class="info-block">
              <strong>核心能力</strong>
              <ul><li v-for="item in selectedModel.capabilities" :key="item">{{ item }}</li></ul>
            </div>
            <div v-if="hasTextList(selectedModel.applicableScenarios)" class="info-block">
              <strong>适用场景</strong>
              <ul><li v-for="item in selectedModel.applicableScenarios" :key="item">{{ item }}</li></ul>
            </div>
            <div v-if="hasTextList(selectedModel.advantages)" class="info-block">
              <strong>优势</strong>
              <ul><li v-for="item in selectedModel.advantages" :key="item">{{ item }}</li></ul>
            </div>
            <div v-if="hasTextList(selectedModel.limitations)" class="info-block">
              <strong>局限</strong>
              <ul><li v-for="item in selectedModel.limitations" :key="item">{{ item }}</li></ul>
            </div>
          </div>
        </section>

        <section v-if="selectedModel.metrics?.length" class="detail-section">
          <p class="section-kicker">Metrics</p>
          <h3>参考指标</h3>
          <el-table :data="selectedModel.metrics" size="small" border>
            <el-table-column prop="datasetName" label="数据集" min-width="130" />
            <el-table-column label="MAE" width="90">
              <template #default="{ row }">{{ formatMetric(row.mae) }}</template>
            </el-table-column>
            <el-table-column label="RMSE" width="90">
              <template #default="{ row }">{{ formatMetric(row.rmse) }}</template>
            </el-table-column>
            <el-table-column label="MAPE" width="90">
              <template #default="{ row }">{{ formatMetric(row.mape, '%') }}</template>
            </el-table-column>
          </el-table>
          <p class="metric-note">指标来自论文或离线基线资料，不代表当前平台实时实测。</p>
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

        <section v-if="hasSchema(selectedModel.inputSchema)" class="detail-section">
          <p class="section-kicker">Input</p>
          <h3>输入要求</h3>
          <pre class="schema-block">{{ formatSchema(selectedModel.inputSchema) }}</pre>
        </section>

        <section v-if="hasSchema(selectedModel.outputSchema)" class="detail-section">
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

.marketplace-workspace {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
}

.marketplace-workspace.has-filter-sidebar {
  grid-template-columns: min(var(--filter-sidebar-width), 45%) 12px minmax(0, 1fr);
}

.filter-sidebar {
  position: sticky;
  top: 64px;
  min-width: 0;
  max-height: calc(100vh - 80px);
  overflow-x: hidden;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(20, 65, 120, 0.06);
  scrollbar-width: thin;
}

.filter-sidebar-head {
  position: sticky;
  z-index: 2;
  top: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 16px;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
}

.filter-sidebar-head strong {
  color: #10274c;
  font-size: 17px;
}

.filter-sidebar-head p {
  margin: 5px 0 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
}

.filter-sidebar-body {
  display: grid;
  gap: 24px;
  padding: 16px;
}

.filter-resizer {
  position: sticky;
  top: 64px;
  display: grid;
  width: 12px;
  height: calc(100vh - 80px);
  place-items: center;
  border-radius: 6px;
  cursor: col-resize;
  touch-action: none;
}

.filter-resizer span {
  width: 3px;
  height: 48px;
  border-radius: 2px;
  background: #c7d5e5;
  transition: width 160ms ease, background 160ms ease;
}

.filter-resizer:hover span,
.filter-resizer:focus-visible span {
  width: 4px;
  background: var(--color-primary);
}

.filter-resizer:focus-visible {
  outline: 2px solid rgba(29, 111, 220, 0.22);
  outline-offset: -2px;
}

.model-results {
  display: grid;
  min-width: 0;
  gap: 16px;
}

.selected-filter-bar {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid #d8e3f0;
  border-radius: 10px;
  background: #ffffff;
}

.selected-filter-chips {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
}

.selected-filter-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  color: var(--color-muted);
  font-size: 13px;
}

.model-grid {
  display: grid;
  min-width: 0;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
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

.card-heading {
  display: flex;
  align-items: center;
  gap: 13px;
}

.model-icon {
  display: block;
  width: 64px;
  height: 64px;
  flex: 0 0 64px;
  border-radius: 14px;
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

.model-description {
  display: -webkit-box;
  overflow: hidden;
  color: #5f7088;
  line-height: 1.75;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.tag-row {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
}

.model-card > .tag-row {
  margin-top: auto;
}

.tag-chip {
  display: inline-flex;
  max-width: 100%;
  min-height: 24px;
  align-items: center;
  padding: 3px 8px;
  overflow: hidden;
  border: 1px solid #d8e5f3;
  border-radius: 6px;
  color: #526a86;
  background: #f7faff;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.filter-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.filter-group {
  display: grid;
  gap: 12px;
}

.filter-section-heading {
  padding: 0 2px;
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

.filter-option-list {
  display: grid;
  min-width: 0;
  grid-template-columns: repeat(auto-fill, minmax(112px, 1fr));
  gap: 8px;
}

.filter-option-list.type-option-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.filter-option-list :deep(.el-checkbox) {
  min-width: 0;
  width: 100%;
  min-height: 42px;
  margin: 0;
  padding: 8px 12px;
  border-radius: 8px;
}

.filter-option-list :deep(.el-checkbox__label) {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  overflow: hidden;
}

.filter-option-label {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-option-list small {
  flex: 0 0 auto;
  color: #94a2b5;
}

.group-more-button {
  justify-self: start;
  padding: 0;
}

.more-filter-section {
  display: grid;
  gap: 16px;
  padding-top: 4px;
  border-top: 1px solid var(--color-border);
}

.more-filter-toggle {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 10px 0 0;
  border: 0;
  color: #10274c;
  background: transparent;
  text-align: left;
  font: inherit;
  cursor: pointer;
}

.more-filter-toggle small {
  overflow: hidden;
  color: var(--color-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toggle-arrow {
  color: #7a89a0;
  transition: transform 160ms ease;
}

.toggle-arrow.expanded {
  transform: rotate(180deg);
}

.more-filter-groups {
  display: grid;
  gap: 22px;
}

.compact-filter-group .filter-section-heading p {
  display: none;
}

:global(body.is-resizing-model-filter) {
  cursor: col-resize;
  user-select: none;
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

.detail-icon {
  display: block;
  width: 76px;
  height: 76px;
  flex: 0 0 76px;
  border-radius: 16px;
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

.detail-description + .detail-description {
  margin-top: 10px;
}

.detail-tags {
  margin-bottom: 14px;
}

.meta-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.meta-list div {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 12px;
}

.meta-list dt {
  color: #7a89a0;
}

.meta-list dd {
  min-width: 0;
  margin: 0;
  color: #30445f;
  overflow-wrap: anywhere;
}

.meta-list a {
  color: var(--color-primary);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-block {
  padding: 14px;
  border: 1px solid #dce6f1;
  border-radius: 8px;
  background: #fbfdff;
}

.info-block strong {
  display: block;
  margin-bottom: 8px;
  color: #10274c;
}

.info-block ul {
  margin: 0;
  padding-left: 18px;
  color: #566980;
  line-height: 1.8;
}

.metric-note {
  margin: 10px 0 0;
  color: #7a89a0;
  font-size: 12px;
  line-height: 1.6;
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

@media (max-width: 1000px) {
  .marketplace-workspace.has-filter-sidebar {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }

  .filter-sidebar {
    position: static;
    width: 100%;
    max-height: none;
  }

  .filter-resizer {
    display: none;
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

  .selected-filter-bar,
  .selected-filter-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .filter-sidebar-body {
    padding: 14px;
  }

  .model-card {
    min-height: 230px;
  }
}
</style>
