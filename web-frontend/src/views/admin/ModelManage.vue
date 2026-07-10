<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createModel,
  getAdminModels,
  getModel,
  updateModel,
  updateModelStatus,
  type Model,
  type ModelPayload
} from '../../api/model'

type ModelStatus = 'ONLINE' | 'OFFLINE' | 'TESTING'
type ModelCategory = '时序基线' | '云图/视觉融合' | '视频时空递归' | '多模态模型'

interface ModelRow {
  modelId: number
  modelName: string
  modelCode: string
  category: ModelCategory
  modelType: string
  modelVersion: string
  modelStatus: ModelStatus
  price: number
  quota: number
  apiPath: string
  serviceModelName: string
  inputWindowMinutes?: number
  inputFrameIntervalSeconds?: number
  outputSteps?: number
  outputStepMinutes?: number
  inputSchema?: string
  outputSchema?: string
  score: number
  latency: number
  description: string
  tags: string[]
  updatedAt: string
}

interface ModelForm extends ModelRow {
  tagText: string
}

const filters = reactive({
  keyword: '',
  category: '',
  status: ''
})

const dialogVisible = ref(false)
const editingId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const remoteReady = ref(false)

const modelForm = reactive<ModelForm>({
  modelId: 0,
  modelName: '',
  modelCode: '',
  category: '时序基线',
  modelType: 'NUMERIC',
  modelVersion: 'v1.0',
  modelStatus: 'TESTING',
  price: 0.08,
  quota: 1000,
  apiPath: '/openapi/v1/predict',
  serviceModelName: '',
  inputWindowMinutes: 30,
  inputFrameIntervalSeconds: 60,
  outputSteps: 6,
  outputStepMinutes: 5,
  inputSchema: '{"input":[{"time":"yyyy-MM-dd HH:mm:ss","power":0,"temperature":0,"irradiance":0}]}',
  outputSchema: '{"predictions":[{"timeOffset":5,"predictPower":0}]}',
  score: 90,
  latency: 160,
  description: '',
  tags: [],
  tagText: '',
  updatedAt: ''
})

const fallbackRows: ModelRow[] = [
  {
    modelId: 1001,
    modelName: 'PatchTST 短期功率预测',
    modelCode: 'patchtst_short',
    category: '时序基线',
    modelType: 'NUMERIC',
    modelVersion: 'v1.2',
    modelStatus: 'ONLINE',
    price: 0.08,
    quota: 2000,
    apiPath: '/openapi/v1/predict',
    serviceModelName: 'PatchTST',
    inputWindowMinutes: 30,
    inputFrameIntervalSeconds: 60,
    outputSteps: 6,
    outputStepMinutes: 5,
    inputSchema: '{"input":[{"time":"yyyy-MM-dd HH:mm:ss","power":0,"temperature":0,"irradiance":0}]}',
    outputSchema: '{"predictions":[{"timeOffset":5,"predictPower":0}]}',
    score: 92.6,
    latency: 118,
    description: '面向光伏功率序列的短期预测模型，适合稳定站点调度。',
    tags: ['PatchTST', '30min', '低延迟'],
    updatedAt: '2026-07-06 10:12:00'
  },
  {
    modelId: 2001,
    modelName: 'SimVP+GSTA 云图递归预测',
    modelCode: 'simvp_gsta',
    category: '视频时空递归',
    modelType: 'IMAGE_TO_NUMERIC',
    modelVersion: 'v1.0',
    modelStatus: 'ONLINE',
    price: 0.18,
    quota: 600,
    apiPath: '/openapi/v1/cloud/predict',
    serviceModelName: 'SimVP_gSTA',
    inputWindowMinutes: 50,
    inputFrameIntervalSeconds: 300,
    outputSteps: 10,
    outputStepMinutes: 5,
    inputSchema: '{"frames":["base64 image x10"]}',
    outputSchema: '{"frames":["base64 image x10"],"cloudTrend":[]}',
    score: 88.4,
    latency: 236,
    description: '读取连续 10 张云图，预测未来 10 张云图和云量趋势。',
    tags: ['SimVP', 'GSTA', '云图'],
    updatedAt: '2026-07-05 16:30:00'
  },
  {
    modelId: 3001,
    modelName: 'CNN+LSTM 多模态预测',
    modelCode: 'cnn_lstm_multi',
    category: '多模态模型',
    modelType: 'MULTIMODAL',
    modelVersion: 'v1.4',
    modelStatus: 'TESTING',
    price: 0.12,
    quota: 1200,
    apiPath: '/openapi/v1/predict',
    serviceModelName: 'CNN_LSTM',
    inputWindowMinutes: 30,
    inputFrameIntervalSeconds: 60,
    outputSteps: 6,
    outputStepMinutes: 5,
    inputSchema: '{"input":[{"power":0,"temperature":0,"irradiance":0}],"images":[]}',
    outputSchema: '{"predictions":[{"timeOffset":5,"predictPower":0}]}',
    score: 94.1,
    latency: 192,
    description: '融合云图特征、天气字段和功率序列，适合复杂天气预测。',
    tags: ['CNN', 'LSTM', '多模态'],
    updatedAt: '2026-07-04 13:26:00'
  },
  {
    modelId: 3002,
    modelName: '3D-CNN+LSTM 融合模型',
    modelCode: '3dcnn_lstm',
    category: '云图/视觉融合',
    modelType: 'IMAGE_TO_NUMERIC',
    modelVersion: 'v1.0',
    modelStatus: 'OFFLINE',
    price: 0.1,
    quota: 800,
    apiPath: '/openapi/v1/predict',
    serviceModelName: '3DCNN_LSTM',
    inputWindowMinutes: 30,
    inputFrameIntervalSeconds: 60,
    outputSteps: 6,
    outputStepMinutes: 5,
    inputSchema: '{"input":[{"power":0,"temperature":0,"irradiance":0}],"imageFrames":[]}',
    outputSchema: '{"predictions":[{"timeOffset":5,"predictPower":0}]}',
    score: 91.3,
    latency: 228,
    description: '用于云图局部时空块和气象序列的联合建模评测。',
    tags: ['3D-CNN', '评测', '视觉融合'],
    updatedAt: '2026-07-02 09:42:00'
  }
]

const modelRows = ref<ModelRow[]>([...fallbackRows])

const categoryOptions: ModelCategory[] = ['时序基线', '云图/视觉融合', '视频时空递归', '多模态模型']

const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return modelRows.value.filter((row) => {
    const matchesKeyword =
      !keyword ||
      [row.modelName, row.modelCode, row.serviceModelName, row.description, ...row.tags].some((value) =>
        value.toLowerCase().includes(keyword)
      )
    const matchesCategory = !filters.category || row.category === filters.category
    const matchesStatus = !filters.status || row.modelStatus === filters.status
    return matchesKeyword && matchesCategory && matchesStatus
  })
})

const summaryCards = computed(() => {
  const online = modelRows.value.filter((row) => row.modelStatus === 'ONLINE').length
  const testing = modelRows.value.filter((row) => row.modelStatus === 'TESTING').length
  const avgScore =
    modelRows.value.reduce((sum, row) => sum + row.score, 0) / Math.max(modelRows.value.length, 1)
  return [
    { label: '模型总数', value: `${modelRows.value.length}` },
    { label: '已上架', value: `${online}` },
    { label: '测试中', value: `${testing}` },
    { label: '平均评分', value: avgScore.toFixed(1) }
  ]
})

function statusType(status: ModelStatus) {
  if (status === 'ONLINE') return 'success'
  if (status === 'TESTING') return 'warning'
  return 'info'
}

function statusText(status: ModelStatus) {
  const map: Record<ModelStatus, string> = {
    ONLINE: '已上架',
    OFFLINE: '已下架',
    TESTING: '测试中'
  }
  return map[status]
}

function resetForm() {
  Object.assign(modelForm, {
    modelId: 0,
    modelName: '',
    modelCode: '',
    category: '时序基线',
    modelType: 'NUMERIC',
    modelVersion: 'v1.0',
    modelStatus: 'TESTING',
    price: 0.08,
    quota: 1000,
    apiPath: '/openapi/v1/predict',
    serviceModelName: '',
    inputWindowMinutes: 30,
    inputFrameIntervalSeconds: 60,
    outputSteps: 6,
    outputStepMinutes: 5,
    inputSchema: '{"input":[{"time":"yyyy-MM-dd HH:mm:ss","power":0,"temperature":0,"irradiance":0}]}',
    outputSchema: '{"predictions":[{"timeOffset":5,"predictPower":0}]}',
    score: 90,
    latency: 160,
    description: '',
    tags: [],
    tagText: '',
    updatedAt: ''
  })
}

function openCreate() {
  editingId.value = undefined
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: ModelRow) {
  editingId.value = row.modelId
  Object.assign(modelForm, row, { tagText: row.tags.join('、') })
  if (remoteReady.value) {
    try {
      const detail = await getModel(row.modelId)
      const mapped = mapModel(detail, row)
      Object.assign(modelForm, mapped, { tagText: mapped.tags.join('、') })
    } catch (error) {
      ElMessage.warning(error instanceof Error ? `使用列表数据编辑：${error.message}` : '使用列表数据编辑')
    }
  }
  dialogVisible.value = true
}

async function saveModel() {
  if (!modelForm.modelName.trim() || !modelForm.modelCode.trim() || !modelForm.serviceModelName.trim()) {
    ElMessage.warning('请填写模型名称、编码和服务模型名')
    return
  }

  saving.value = true
  try {
    const desiredStatus = modelForm.modelStatus
    let nextRow: ModelRow

    if (remoteReady.value) {
      const payload = toModelPayload(modelForm)
      const saved = editingId.value ? await updateModel(editingId.value, payload) : await createModel(payload)
      nextRow = mapModel(saved, modelForm)

      if (nextRow.modelStatus !== desiredStatus) {
        try {
          const statusModel = await changeRemoteModelStatus(nextRow, desiredStatus)
          nextRow = mapModel(statusModel, { ...nextRow, modelStatus: desiredStatus })
        } catch (error) {
          ElMessage.warning(error instanceof Error ? `基础信息已保存，状态更新失败：${error.message}` : '基础信息已保存，状态更新失败')
        }
      }
    } else {
      nextRow = {
        ...modelForm,
        modelId: editingId.value ?? Date.now(),
        tags: parseTags(modelForm.tagText),
        updatedAt: formatDateTime()
      }
    }

    if (editingId.value) {
      const index = modelRows.value.findIndex((row) => row.modelId === editingId.value)
      if (index >= 0) modelRows.value[index] = nextRow
      ElMessage.success('模型配置已更新')
    } else {
      modelRows.value.unshift(nextRow)
      ElMessage.success('模型已新增')
    }
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: ModelRow) {
  const nextStatus: ModelStatus = row.modelStatus === 'ONLINE' ? 'OFFLINE' : 'ONLINE'
  await changeModelStatus(row, nextStatus, nextStatus === 'ONLINE' ? '模型已上架' : '模型已下架')
}

async function markTesting(row: ModelRow) {
  await changeModelStatus(row, 'TESTING', '模型已切换为测试中')
}

async function removeModel(row: ModelRow) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.modelName}」吗？`, '删除模型', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    if (remoteReady.value && row.modelStatus !== 'OFFLINE') {
      await changeRemoteModelStatus(row, 'OFFLINE')
    }
    modelRows.value = modelRows.value.filter((item) => item.modelId !== row.modelId)
    ElMessage.success(remoteReady.value ? '后端已下架，当前列表已移除' : '模型已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '模型删除失败')
    }
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.category = ''
  filters.status = ''
}

function formatDateTime() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes()
  )}:${pad(date.getSeconds())}`
}

async function loadModels() {
  loading.value = true
  try {
    const models = await getAdminModels()
    modelRows.value = models.map((model, index) => mapModel(model, fallbackRows[index]))
    remoteReady.value = true
  } catch (error) {
    modelRows.value = [...fallbackRows]
    remoteReady.value = false
    ElMessage.warning(error instanceof Error ? `使用演示数据：${error.message}` : '使用演示数据')
  } finally {
    loading.value = false
  }
}

async function changeModelStatus(row: ModelRow, status: ModelStatus, successMessage: string) {
  if (row.modelStatus === status) {
    ElMessage.info('模型已处于目标状态')
    return
  }

  try {
    if (remoteReady.value) {
      const updated = await changeRemoteModelStatus(row, status)
      Object.assign(row, mapModel(updated, { ...row, modelStatus: status }))
    } else {
      row.modelStatus = status
      row.updatedAt = formatDateTime()
    }
    ElMessage.success(successMessage)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型状态更新失败')
  }
}

async function changeRemoteModelStatus(row: ModelRow, status: ModelStatus) {
  if (status === 'TESTING' && row.modelStatus === 'ONLINE') {
    await updateModelStatus(row.modelId, { modelStatus: 'OFFLINE' })
  }
  return updateModelStatus(row.modelId, { modelStatus: status })
}

function mapModel(model: Model, extra?: Partial<ModelRow>): ModelRow {
  const modelCode = model.modelCode || extra?.modelCode || slugify(model.modelName)
  const modelType = normalizeModelType(model.modelType || extra?.modelType)
  const category = normalizeCategory(model.category || extra?.category || inferCategory(modelType, modelCode, model.modelName))
  return {
    modelId: model.modelId,
    modelName: model.modelName,
    modelCode,
    category,
    modelType,
    modelVersion: model.modelVersion || extra?.modelVersion || 'v1.0',
    modelStatus: normalizeModelStatus(model.modelStatus || model.status || extra?.modelStatus),
    price: model.price ?? extra?.price ?? inferPrice(category),
    quota: model.quota ?? extra?.quota ?? inferQuota(category),
    apiPath: model.apiPath || extra?.apiPath || '/openapi/v1/predict',
    serviceModelName: model.serviceModelName || extra?.serviceModelName || modelCode,
    inputWindowMinutes: model.inputWindowMinutes ?? extra?.inputWindowMinutes ?? 30,
    inputFrameIntervalSeconds: model.inputFrameIntervalSeconds ?? extra?.inputFrameIntervalSeconds ?? 60,
    outputSteps: model.outputSteps ?? extra?.outputSteps ?? 6,
    outputStepMinutes: model.outputStepMinutes ?? extra?.outputStepMinutes ?? 5,
    inputSchema: model.inputSchema || extra?.inputSchema || '{"input":[]}',
    outputSchema: model.outputSchema || extra?.outputSchema || '{"predictions":[]}',
    score: model.score ?? extra?.score ?? inferScore(category),
    latency: model.latency ?? extra?.latency ?? inferLatency(category),
    description: model.description || extra?.description || '',
    tags: model.tags || extra?.tags || inferTags(category, modelCode),
    updatedAt: model.updatedAt || extra?.updatedAt || formatDateTime()
  }
}

function toModelPayload(row: ModelForm): ModelPayload {
  return {
    modelCode: row.modelCode.trim(),
    modelName: row.modelName.trim(),
    modelType: normalizeModelType(row.modelType),
    modelVersion: row.modelVersion || 'v1.0',
    inputWindowMinutes: row.inputWindowMinutes || 30,
    inputFrameIntervalSeconds: row.inputFrameIntervalSeconds || 60,
    outputSteps: row.outputSteps || 6,
    outputStepMinutes: row.outputStepMinutes || 5,
    serviceModelName: row.serviceModelName.trim(),
    apiPath: row.apiPath || '/openapi/v1/predict',
    inputSchema: row.inputSchema || '{}',
    outputSchema: row.outputSchema || '{}',
    description: row.description
  }
}

function parseTags(value: string) {
  return value
    .split(/[、,\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function normalizeModelStatus(status?: string): ModelStatus {
  if (status === 'ONLINE' || status === 'OFFLINE' || status === 'TESTING') return status
  return 'OFFLINE'
}

function normalizeModelType(type?: string) {
  if (type === 'NUMERIC' || type === 'MULTIMODAL' || type === 'IMAGE_TO_NUMERIC') return type
  if (type === 'IMAGE' || type === 'VIDEO') return 'IMAGE_TO_NUMERIC'
  return 'NUMERIC'
}

function normalizeCategory(category?: string): ModelCategory {
  if (category === '时序基线' || category === '云图/视觉融合' || category === '视频时空递归' || category === '多模态模型') {
    return category
  }
  return '时序基线'
}

function inferCategory(modelType: string, modelCode: string, modelName: string): ModelCategory {
  const value = `${modelCode} ${modelName}`.toLowerCase()
  if (/simvp|tau|predrnn|convlstm|e3d|swin|sunset/.test(value)) return '视频时空递归'
  if (modelType === 'MULTIMODAL') return '多模态模型'
  if (modelType === 'IMAGE_TO_NUMERIC' || /cnn|cloud|vision|云图/.test(value)) return '云图/视觉融合'
  return '时序基线'
}

function inferPrice(category: ModelCategory) {
  if (category === '时序基线') return 0.08
  if (category === '云图/视觉融合') return 0.12
  if (category === '视频时空递归') return 0.18
  return 0.14
}

function inferQuota(category: ModelCategory) {
  if (category === '时序基线') return 2000
  if (category === '视频时空递归') return 600
  return 1000
}

function inferScore(category: ModelCategory) {
  if (category === '多模态模型') return 94
  if (category === '视频时空递归') return 88
  return 92
}

function inferLatency(category: ModelCategory) {
  if (category === '时序基线') return 120
  if (category === '视频时空递归') return 240
  return 190
}

function inferTags(category: ModelCategory, modelCode: string) {
  const base = category === '时序基线' ? ['短时预测'] : category === '视频时空递归' ? ['云图', '时空递归'] : ['多源融合']
  return [modelCode, ...base]
}

function slugify(value: string) {
  return value.trim().replace(/\s+/g, '_') || `model_${Date.now()}`
}

onMounted(loadModels)
</script>

<template>
  <section class="page-shell admin-model-page">
    <div class="page-title">
      <div>
        <h2>模型管理</h2>
        <p>维护广场展示模型，配置介绍、类别、上下架、价格、额度和调用方式。</p>
      </div>
      <div class="title-actions">
        <el-button :loading="loading" @click="loadModels">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增模型</el-button>
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
          <el-input v-model="filters.keyword" clearable placeholder="搜索模型、编码、标签" />
          <el-select v-model="filters.category" clearable placeholder="类别">
            <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="状态">
            <el-option label="已上架" value="ONLINE" />
            <el-option label="测试中" value="TESTING" />
            <el-option label="已下架" value="OFFLINE" />
          </el-select>
        </div>
        <el-button @click="resetFilters">重置筛选</el-button>
      </div>

      <el-table v-loading="loading" :data="filteredRows" size="large" stripe>
        <el-table-column prop="modelName" label="模型名称" min-width="220" fixed="left">
          <template #default="{ row }">
            <strong class="table-title">{{ row.modelName }}</strong>
            <span class="table-sub">{{ row.modelCode }} · {{ row.modelVersion }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="类别" min-width="140" />
        <el-table-column prop="modelType" label="类型" width="110" />
        <el-table-column prop="modelStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.modelStatus)">{{ statusText(row.modelStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="价格/额度" width="132">
          <template #default="{ row }">
            <span>{{ row.price.toFixed(2) }} 元/次</span>
            <span class="table-sub">{{ row.quota }} 次套餐</span>
          </template>
        </el-table-column>
        <el-table-column prop="apiPath" label="调用路径" min-width="170" />
        <el-table-column prop="score" label="评分" width="90" sortable />
        <el-table-column prop="latency" label="时延" width="92">
          <template #default="{ row }">{{ row.latency }} ms</template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag v-for="tag in row.tags" :key="tag" size="small">{{ tag }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text @click="toggleStatus(row)">
              {{ row.modelStatus === 'ONLINE' ? '下架' : '上架' }}
            </el-button>
            <el-button size="small" text @click="markTesting(row)">测试</el-button>
            <el-button size="small" text type="danger" @click="removeModel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模型' : '新增模型'" width="760px">
      <el-form label-position="top" class="model-form">
        <el-form-item label="模型名称">
          <el-input v-model="modelForm.modelName" />
        </el-form-item>
        <el-form-item label="模型编码">
          <el-input v-model="modelForm.modelCode" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="modelForm.category">
            <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="modelForm.modelType">
            <el-option label="NUMERIC" value="NUMERIC" />
            <el-option label="IMAGE_TO_NUMERIC" value="IMAGE_TO_NUMERIC" />
            <el-option label="MULTIMODAL" value="MULTIMODAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="modelForm.modelVersion" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="modelForm.modelStatus">
            <el-option label="已上架" value="ONLINE" />
            <el-option label="测试中" value="TESTING" />
            <el-option label="已下架" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格(元/次)">
          <el-input-number v-model="modelForm.price" :min="0" :precision="2" :step="0.01" />
        </el-form-item>
        <el-form-item label="套餐额度">
          <el-input-number v-model="modelForm.quota" :min="1" :step="100" />
        </el-form-item>
        <el-form-item label="服务模型名">
          <el-input v-model="modelForm.serviceModelName" />
        </el-form-item>
        <el-form-item label="调用路径">
          <el-input v-model="modelForm.apiPath" />
        </el-form-item>
        <el-form-item label="评分">
          <el-input-number v-model="modelForm.score" :min="0" :max="100" :precision="1" />
        </el-form-item>
        <el-form-item label="平均时延(ms)">
          <el-input-number v-model="modelForm.latency" :min="1" :step="10" />
        </el-form-item>
        <el-form-item label="标签" class="wide">
          <el-input v-model="modelForm.tagText" placeholder="用顿号、逗号或空格分隔" />
        </el-form-item>
        <el-form-item label="模型介绍" class="wide">
          <el-input v-model="modelForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-model-page {
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
  font-size: 26px;
}

.filter-row {
  display: grid;
  grid-template-columns: 300px 180px 150px;
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

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.model-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.model-form .wide {
  grid-column: 1 / -1;
}

.model-form :deep(.el-input-number),
.model-form :deep(.el-select) {
  width: 100%;
}

@media (max-width: 980px) {
  .summary-grid,
  .filter-row,
  .model-form {
    grid-template-columns: 1fr;
  }

  .model-form .wide {
    grid-column: auto;
  }
}
</style>
