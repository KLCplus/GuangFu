<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getModels } from '../api/model'
import type { Model } from '../api/model'

type ModelCategory = 'power-sequence' | 'cloud-sequence' | 'multimodal'

interface ModelCategoryOption {
  value: ModelCategory
  label: string
}

interface UsableModel {
  modelId: number
  modelName: string
  modelCode: string
  category: ModelCategory
}

interface ResultRow {
  index: number
  predictTime: string
  predictPower: number
}

const categories: ModelCategoryOption[] = [
  { value: 'power-sequence', label: '功率时序模型' },
  { value: 'cloud-sequence', label: '云图时空模型' },
  { value: 'multimodal', label: '多模态融合模型' }
]

const fallbackModels: UsableModel[] = [
  { modelId: 1001, modelName: 'PatchTST 功率预测', modelCode: 'patchtst_power', category: 'power-sequence' },
  { modelId: 1002, modelName: 'iTransformer 功率预测', modelCode: 'itransformer_power', category: 'power-sequence' },
  { modelId: 2001, modelName: 'PredRNN 云图预测', modelCode: 'predrnn_cloud', category: 'cloud-sequence' },
  { modelId: 2002, modelName: 'SimVP 云图预测', modelCode: 'simvp_cloud', category: 'cloud-sequence' },
  { modelId: 3001, modelName: 'ConvLSTM-LSTM 融合预测', modelCode: 'convlstm_lstm', category: 'multimodal' },
  { modelId: 3002, modelName: '3DCNN-LSTM 融合预测', modelCode: '3dcnn_lstm', category: 'multimodal' }
]

const loading = ref(false)
const running = ref(false)
const selectedCategory = ref<ModelCategory>('power-sequence')
const selectedModelId = ref<number>()
const models = ref<UsableModel[]>(fallbackModels)
const dataFile = ref<File>()
const cloudFiles = ref<File[]>([])
const folderInput = ref<HTMLInputElement>()
const resultRows = ref<ResultRow[]>(generateResultRows('power-sequence'))

const filteredModels = computed(() => models.value.filter((item) => item.category === selectedCategory.value))
const selectedModel = computed(() => filteredModels.value.find((item) => item.modelId === selectedModelId.value))
const cloudFolderName = computed(() => {
  const firstPath = cloudFiles.value[0]?.webkitRelativePath
  return firstPath ? firstPath.split('/')[0] : ''
})
const resultStats = computed(() => {
  const values = resultRows.value.map((item) => item.predictPower)
  const total = values.reduce((sum, value) => sum + value, 0)
  return {
    avg: values.length ? total / values.length : 0,
    max: values.length ? Math.max(...values) : 0
  }
})
const chartPoints = computed(() => {
  const values = resultRows.value.map((item) => item.predictPower)
  if (!values.length) return ''

  const width = 680
  const height = 220
  const paddingX = 34
  const paddingY = 24
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = Math.max(max - min, 1)

  return values
    .map((value, index) => {
      const x = paddingX + (index / Math.max(values.length - 1, 1)) * (width - paddingX * 2)
      const y = height - paddingY - ((value - min) / range) * (height - paddingY * 2)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

onMounted(async () => {
  loading.value = true
  await loadModels()
  selectedModelId.value = filteredModels.value[0]?.modelId
  loading.value = false
})

async function loadModels() {
  try {
    const records = await getModels()
    const apiModels = records.map(mapApiModel)
    const merged = [...apiModels]

    fallbackModels.forEach((model) => {
      const hasCategoryModel = merged.some((item) => item.category === model.category)
      if (!hasCategoryModel) merged.push(model)
    })

    models.value = merged.length ? merged : fallbackModels
  } catch {
    models.value = fallbackModels
  }
}

function mapApiModel(model: Model): UsableModel {
  return {
    modelId: model.modelId,
    modelName: model.modelName,
    modelCode: model.modelCode || `model_${model.modelId}`,
    category: getModelCategory(model)
  }
}

function getModelCategory(model: Pick<Model, 'modelName' | 'modelCode' | 'modelType'>): ModelCategory {
  const text = [model.modelName, model.modelCode, model.modelType].filter(Boolean).join(' ').toLowerCase()
  if (text.includes('3dcnn') || text.includes('convlstm_lstm') || text.includes('multi') || text.includes('融合')) {
    return 'multimodal'
  }
  if (
    text.includes('predrnn') ||
    text.includes('simvp') ||
    text.includes('convlstm') ||
    text.includes('cloud') ||
    text.includes('image') ||
    text.includes('云图')
  ) {
    return 'cloud-sequence'
  }
  return 'power-sequence'
}

function handleCategoryChange() {
  selectedModelId.value = filteredModels.value[0]?.modelId
  resultRows.value = generateResultRows(selectedCategory.value)
}

function handleDataFileChange(file?: File) {
  if (!file) return

  const valid = /\.(xlsx|xls|csv)$/i.test(file.name)
  if (!valid) {
    ElMessage.warning('只能上传 Excel 或 CSV 文件')
    dataFile.value = undefined
    return
  }
  dataFile.value = file
}

function chooseCloudFolder() {
  folderInput.value?.click()
}

function handleCloudFolderChange(event: Event) {
  const input = event.target as HTMLInputElement
  cloudFiles.value = Array.from(input.files ?? [])
}

function runPrediction() {
  if (!selectedModel.value) {
    ElMessage.warning('请选择模型')
    return
  }
  if (!dataFile.value) {
    ElMessage.warning('请上传 Excel 或 CSV 数据文件')
    return
  }
  if (!cloudFiles.value.length) {
    ElMessage.warning('请选择云图文件夹')
    return
  }

  running.value = true
  window.setTimeout(() => {
    resultRows.value = generateResultRows(selectedCategory.value)
    running.value = false
    ElMessage.success('预测完成')
  }, 600)
}

function generateResultRows(category: ModelCategory): ResultRow[] {
  const baseMap: Record<ModelCategory, number> = {
    'power-sequence': 520,
    'cloud-sequence': 545,
    multimodal: 570
  }
  const base = baseMap[category]

  return Array.from({ length: 8 }, (_, index) => {
    const offset = (index + 1) * 15
    const curve = Math.sin((index + 1) / 2.2) * 22
    const trend = index * 9.5
    return {
      index: index + 1,
      predictTime: formatFutureTime(offset),
      predictPower: Number((base + trend + curve).toFixed(1))
    }
  })
}

function formatFutureTime(offsetMinutes: number) {
  const date = new Date(Date.now() + offsetMinutes * 60 * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<template>
  <section class="page-shell model-page" v-loading="loading">
    <section class="page-section control-panel">
      <div class="control-grid">
        <div class="control-group">
          <div class="model-picker">
            <el-form-item label="模型类别">
              <el-select v-model="selectedCategory" class="full-control" @change="handleCategoryChange">
                <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>

            <el-form-item label="模型">
              <el-select v-model="selectedModelId" class="full-control">
                <el-option
                  v-for="model in filteredModels"
                  :key="model.modelId"
                  :label="model.modelName"
                  :value="model.modelId"
                />
              </el-select>
            </el-form-item>
          </div>
        </div>

        <div class="control-group">
          <div class="input-row">
            <div class="file-field">
              <span class="file-label">Excel / CSV</span>
              <label class="file-control">
                <input
                  type="file"
                  accept=".xlsx,.xls,.csv"
                  @change="handleDataFileChange(($event.target as HTMLInputElement).files?.[0])"
                />
                <strong>{{ dataFile?.name || '选择功率数据文件' }}</strong>
              </label>
            </div>

            <div class="file-field">
              <span class="file-label">云图文件夹</span>
              <button class="file-control" type="button" @click="chooseCloudFolder">
                <strong>{{ cloudFolderName || '选择云图文件夹' }}</strong>
                <small v-if="cloudFiles.length">{{ cloudFiles.length }} 个文件</small>
              </button>
            </div>

            <input
              ref="folderInput"
              class="hidden-input"
              type="file"
              webkitdirectory
              directory
              multiple
              @change="handleCloudFolderChange"
            />
          </div>
        </div>
      </div>
    </section>

    <section class="page-section result-section">
      <div class="panel-head">
        <div>
          <h3>功率预测结果</h3>
        </div>
        <div class="result-actions">
          <el-button type="primary" :loading="running" @click="runPrediction">开始预测</el-button>
        </div>
      </div>

      <div class="result-layout">
        <div class="result-chart">
          <svg viewBox="0 0 680 220" role="img" aria-label="功率预测曲线">
            <line x1="34" y1="196" x2="646" y2="196" class="chart-axis" />
            <polyline :points="chartPoints" class="chart-line" />
            <circle
              v-for="point in chartPoints.split(' ').filter(Boolean)"
              :key="point"
              :cx="Number(point.split(',')[0])"
              :cy="Number(point.split(',')[1])"
              r="4"
              class="chart-point"
            />
          </svg>
        </div>

        <div class="result-summary">
          <span>平均功率 <b>{{ resultStats.avg.toFixed(1) }} kW</b></span>
          <span>峰值功率 <b>{{ resultStats.max.toFixed(1) }} kW</b></span>
        </div>
      </div>

      <el-table :data="resultRows" size="large">
        <el-table-column prop="index" label="序号" width="90" />
        <el-table-column prop="predictTime" label="预测时间" min-width="140" />
        <el-table-column prop="predictPower" label="预测功率(kW)" min-width="160">
          <template #default="{ row }">{{ row.predictPower.toFixed(1) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<style scoped>
.model-page {
  gap: 16px;
}

.control-panel {
  min-height: auto;
  padding: 16px 18px;
}

.control-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  align-items: stretch;
}

.control-group {
  min-width: 0;
  overflow: hidden;
  padding: 12px;
  border: 1px solid #e1eaf5;
  border-radius: 8px;
  background: #f9fbfe;
}

.model-picker {
  display: grid;
  grid-template-columns: minmax(180px, 0.72fr) minmax(320px, 1.28fr);
  gap: 20px;
  align-items: end;
}

.model-picker :deep(.el-form-item) {
  display: block;
  min-width: 0;
  margin-bottom: 0;
}

.model-picker :deep(.el-form-item__label) {
  display: block;
  height: auto;
  margin-bottom: 6px;
  text-align: left;
}

.model-picker :deep(.el-form-item__content) {
  display: block;
  min-width: 0;
}

.model-picker :deep(.el-select__selected-item),
.model-picker :deep(.el-select__placeholder) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-head h3 {
  margin: 4px 0 0;
}

.full-control {
  width: 100%;
}

.input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
  align-items: end;
}

.file-field {
  min-width: 0;
}

.file-label {
  display: block;
  height: 32px;
  margin-bottom: 6px;
  color: #606266;
  font-size: 14px;
  line-height: 32px;
}

.file-control {
  min-width: 0;
  display: grid;
  align-content: center;
  gap: 2px;
  min-height: 56px;
  width: 100%;
  padding: 8px 20px;
  border: 1px solid #d8e3f0;
  border-radius: 8px;
  background: #ffffff;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.file-control:hover {
  border-color: var(--color-primary);
  background: #eef6ff;
}

.file-control input,
.hidden-input {
  display: none;
}

.file-control small {
  color: var(--color-muted);
  font-size: 12px;
}

.file-control strong {
  overflow: hidden;
  overflow-wrap: anywhere;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #10274c;
  font-size: 14px;
  line-height: 1.25;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.result-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 14px;
  align-items: stretch;
  margin-bottom: 14px;
}

.result-summary {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.result-summary span {
  min-height: 124px;
  padding: 16px;
  border-radius: 8px;
  background: #f6f9fd;
  color: var(--color-muted);
}

.result-summary b {
  display: block;
  margin-top: 8px;
  color: #10274c;
  font-size: 20px;
}

.result-chart {
  min-height: 260px;
  border: 1px solid #dfeaf7;
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.result-chart svg {
  display: block;
  width: 100%;
  height: 260px;
}

.chart-axis {
  stroke: #cfdae8;
  stroke-width: 1;
}

.chart-line {
  fill: none;
  stroke: var(--color-primary);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
}

.chart-point {
  fill: #ffffff;
  stroke: var(--color-primary);
  stroke-width: 2;
}

@media (max-width: 1180px) {
  .control-panel,
  .control-grid,
  .result-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .model-picker,
  .input-row,
  .result-summary {
    grid-template-columns: 1fr;
  }

  .panel-head {
    align-items: stretch;
    flex-direction: column;
  }

  .result-actions {
    justify-content: flex-start;
  }
}
</style>
