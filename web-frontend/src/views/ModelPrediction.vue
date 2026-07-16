<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getModels } from '../api/model'
import { createPrediction, getPredictionResults } from '../api/prediction'
import { getStations } from '../api/station'
import { getApiKeys } from '../api/open'
import type { Model } from '../api/model'
import type { PredictionResult, PredictionImageFrame } from '../api/prediction'
import type { ApiKey } from '../api/open'
import { mockStations } from '../data/mock'

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
  actualPower: number | null
}

interface StationOption {
  stationId: number
  stationName: string
}

const categories: ModelCategoryOption[] = [
  { value: 'power-sequence', label: '功率时序模型' },
  { value: 'cloud-sequence', label: '云图时空模型' },
  { value: 'multimodal', label: '多模态融合模型' }
]

const route = useRoute()
const isVisualizationTheme = computed(() => route.path.startsWith('/visualization-ui/'))

// 19 个模型，分类依据 更改.txt
const fallbackModels: UsableModel[] = [
  // 功率时序模型
  { modelId: 1001, modelName: 'PatchTST', modelCode: 'PatchTST', category: 'power-sequence' },
  { modelId: 1002, modelName: 'DLinear', modelCode: 'DLinear', category: 'power-sequence' },
  { modelId: 1003, modelName: 'iTransformer', modelCode: 'iTransformer', category: 'power-sequence' },
  { modelId: 1004, modelName: 'TimeXer', modelCode: 'TimeXer', category: 'power-sequence' },
  { modelId: 1005, modelName: 'TimeMixer', modelCode: 'TimeMixer', category: 'power-sequence' },
  { modelId: 1006, modelName: 'TSMixer', modelCode: 'TSMixer', category: 'power-sequence' },
  { modelId: 1007, modelName: 'Transformer', modelCode: 'Transformer', category: 'power-sequence' },
  // 云图时空模型
  { modelId: 2001, modelName: 'SimVP+GSTA', modelCode: 'SimVP_gSTA', category: 'cloud-sequence' },
  { modelId: 2002, modelName: 'TAU', modelCode: 'TAU', category: 'cloud-sequence' },
  { modelId: 2003, modelName: 'PredRNN', modelCode: 'PredRNN', category: 'cloud-sequence' },
  { modelId: 2004, modelName: 'PredRNN++', modelCode: 'PredRNN++', category: 'cloud-sequence' },
  { modelId: 2005, modelName: 'ConvLSTM', modelCode: 'ConvLSTM', category: 'cloud-sequence' },
  { modelId: 2006, modelName: 'E3D-LSTM', modelCode: 'E3D_LSTM', category: 'cloud-sequence' },
  { modelId: 2007, modelName: 'SwinLSTM', modelCode: 'swinLSTM', category: 'cloud-sequence' },
  { modelId: 2008, modelName: 'SUNSET', modelCode: 'SUNSET', category: 'cloud-sequence' },
  // 多模态融合模型
  { modelId: 3001, modelName: 'CNN+LSTM', modelCode: 'CNN_LSTM', category: 'multimodal' },
  { modelId: 3002, modelName: '3D-CNN+LSTM', modelCode: '3DCNN_LSTM', category: 'multimodal' },
  { modelId: 3003, modelName: 'CNN+MLP', modelCode: 'CNN_MLP', category: 'multimodal' },
  { modelId: 3004, modelName: 'ConvLSTM+LSTM', modelCode: 'ConvLSTM_LSTM', category: 'multimodal' },
]

const loading = ref(false)
const running = ref(false)
const selectedCategory = ref<ModelCategory>('power-sequence')
const selectedModelId = ref<number>()
const selectedApiKeyId = ref<number>()
const models = ref<UsableModel[]>(fallbackModels)
const stations = ref<StationOption[]>([])
const apiKeys = ref<ApiKey[]>([])
const dataFile = ref<File>()
const cloudFiles = ref<File[]>([])
const folderInput = ref<HTMLInputElement>()
const historicalValues = ref<number[]>([])
const predictedValues = ref<number[]>([])
const actualValues = ref<number[]>([])
const resultRows = ref<ResultRow[]>([])

const filteredModels = computed(() => models.value.filter((item) => item.category === selectedCategory.value))
const selectedModel = computed(() => filteredModels.value.find((item) => item.modelId === selectedModelId.value))
const selectedStationId = computed(() => stations.value[0]?.stationId ?? mockStations[0]?.stationId ?? 1)
const cloudFolderName = computed(() => {
  const firstPath = cloudFiles.value[0]?.webkitRelativePath
  return firstPath ? firstPath.split('/')[0] : ''
})
const needsCloudFolder = computed(() => selectedCategory.value !== 'power-sequence')

// 图表计算：三色折线，X轴5分钟刻度共1小时，Y轴动态范围
const chartData = computed(() => {
  const hist = historicalValues.value
  const pred = predictedValues.value
  const real = actualValues.value
  if (!hist.length && !pred.length) return null

  const allValues = [...hist, ...pred, ...real].filter((v) => isFinite(v))
  if (!allValues.length) return null

  const dataMin = Math.min(...allValues)
  const dataMax = Math.max(...allValues)
  const range = dataMax - dataMin || 1
  const yMin = dataMin - range / 10
  const yMax = dataMax + range / 10

  const W = 680
  const H = 260
  const padL = 56
  const padR = 20
  const padT = 36
  const padB = 34
  const plotW = W - padL - padR
  const plotH = H - padT - padB

  const xScale = (min: number) => padL + (min / 60) * plotW
  const yScale = (val: number) => padT + plotH - ((val - yMin) / (yMax - yMin)) * plotH

  // 历史功率：30个点，x = 0..29 分钟
  const histPoints = hist
    .map((v, i) => `${xScale(i).toFixed(1)},${yScale(v).toFixed(1)}`)
    .join(' ')

  // 预测功率：6个点，x = 30,35,40,45,50,55 分钟
  const predPoints = pred
    .map((v, i) => `${xScale(30 + i * 5).toFixed(1)},${yScale(v).toFixed(1)}`)
    .join(' ')

  // 真实功率：与预测同 x 坐标
  const realPoints = real
    .map((v, i) => `${xScale(30 + i * 5).toFixed(1)},${yScale(v).toFixed(1)}`)
    .join(' ')

  // X轴刻度：0, 5, 10, ..., 60
  const xTicks = Array.from({ length: 13 }, (_, i) => ({
    x: xScale(i * 5),
    label: String(i * 5),
  }))

  // Y轴刻度：5等分
  const yTickCount = 5
  const yTicks = Array.from({ length: yTickCount + 1 }, (_, i) => {
    const val = yMin + (i / yTickCount) * (yMax - yMin)
    return { y: yScale(val), label: val.toFixed(1) }
  })

  return { histPoints, predPoints, realPoints, xTicks, yTicks, W, H }
})

onMounted(async () => {
  loading.value = true
  await Promise.all([loadModels(), loadStations(), loadApiKeys()])
  selectedModelId.value = filteredModels.value[0]?.modelId
  selectedApiKeyId.value = apiKeys.value[0]?.apiKeyId
  loading.value = false
})

async function loadModels() {
  try {
    const records = await getModels()
    const apiModels = records.map(mapApiModel)
    const merged = [...apiModels]
    fallbackModels.forEach((model) => {
      const hasCode = merged.some((item) => item.modelCode === model.modelCode)
      if (!hasCode) merged.push(model)
    })
    models.value = merged.length ? merged : fallbackModels
  } catch {
    models.value = fallbackModels
  }
}

async function loadStations() {
  try {
    const result = await getStations({ pageNum: 1, pageSize: 50 })
    const records = result.records?.length ? result.records : mockStations
    stations.value = records.map((item) => ({
      stationId: item.stationId,
      stationName: item.stationName,
    }))
  } catch {
    stations.value = mockStations.map((item) => ({
      stationId: item.stationId,
      stationName: item.stationName,
    }))
  }
}

async function loadApiKeys() {
  try {
    apiKeys.value = (await getApiKeys()).filter((key) => key.status === 'ACTIVE')
  } catch {
    apiKeys.value = []
  }
}

function mapApiModel(model: Model): UsableModel {
  return {
    modelId: model.modelId,
    modelName: model.modelName,
    modelCode: model.modelCode || `model_${model.modelId}`,
    category: getModelCategory(model),
  }
}

function getModelCategory(model: Pick<Model, 'modelName' | 'modelCode' | 'modelType'>): ModelCategory {
  const type = (model.modelType || '').toUpperCase()
  if (type === 'FUSION') return 'multimodal'
  if (type === 'MULTIMODAL' || type === 'IMAGE') return 'cloud-sequence'
  // 按 modelCode 匹配
  const code = (model.modelCode || '').toLowerCase()
  const cloudCodes = ['simvp_gsta', 'tau', 'predrnn', 'predrnn++', 'convlstm', 'e3d_lstm', 'swinlstm', 'sunset']
  const fusionCodes = ['cnn_lstm', '3dcnn_lstm', 'cnn_mlp', 'convlstm_lstm']
  if (fusionCodes.some((c) => code.includes(c))) return 'multimodal'
  if (cloudCodes.some((c) => code.includes(c))) return 'cloud-sequence'
  return 'power-sequence'
}

function handleCategoryChange() {
  selectedModelId.value = filteredModels.value[0]?.modelId
  historicalValues.value = []
  predictedValues.value = []
  actualValues.value = []
  resultRows.value = []
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

// 解析功率文件（CSV 或 Excel），返回数值数组
async function parsePowerFile(file: File): Promise<number[]> {
  const isExcel = /\.xlsx?$/i.test(file.name)

  if (isExcel) {
    const XLSX = await import('xlsx')
    const data = await file.arrayBuffer()
    const workbook = XLSX.read(data, { type: 'array' })
    const sheet = workbook.Sheets[workbook.SheetNames[0]]
    const rows = XLSX.utils.sheet_to_json(sheet, { header: 1 }) as unknown[][]
    const values: number[] = []
    for (const row of rows) {
      if (!Array.isArray(row)) continue
      for (const cell of row) {
        const num = Number(cell)
        if (!isNaN(num) && isFinite(num)) {
          values.push(num)
          break
        }
      }
    }
    return values
  }

  // CSV 解析
  const text = await file.text()
  const lines = text.trim().split(/\r?\n/).filter((l) => l.trim())
  const values: number[] = []
  for (const line of lines) {
    const parts = line.split(/[,;\t]/)
    for (const part of parts) {
      const num = Number(part.trim())
      if (!isNaN(num) && isFinite(num)) {
        values.push(num)
        break
      }
    }
  }
  return values
}

function readImageAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

function buildPredictionValues(histValues: number[]) {
  const start = Date.now() - 29 * 60 * 1000
  return histValues.map((value, index) => ({
    time: formatDateTime(new Date(start + index * 60 * 1000)),
    value,
  }))
}

async function buildPredictionImages(): Promise<PredictionImageFrame[]> {
  const files = cloudFiles.value.slice(0, 6)
  const start = Date.now() - 5 * 5 * 60 * 1000
  return Promise.all(
    files.map(async (file, index) => ({
      time: formatDateTime(new Date(start + index * 5 * 60 * 1000)),
      image: await readImageAsBase64(file),
    }))
  )
}

function normalizePredictionRows(predictions: PredictionResult[], realValues: number[]): ResultRow[] {
  return predictions.map((item, index) => ({
    index: index + 1,
    predictTime: item.predictTime?.slice(11, 16) || formatFutureTime(item.timeOffset),
    predictPower: Number(item.predictPower ?? 0),
    actualPower: realValues[index] != null ? realValues[index] : null,
  }))
}

async function runPrediction() {
  if (!selectedModel.value) {
    ElMessage.warning('请选择模型')
    return
  }

  const cat = selectedModel.value.category

  // 输入校验
  if (!dataFile.value) {
    ElMessage.warning('请上传功率数据文件')
    return
  }
  if (cat !== 'power-sequence' && !cloudFiles.value.length) {
    ElMessage.warning('请选择云图文件夹')
    return
  }
  if (cat !== 'power-sequence' && cloudFiles.value.length !== 6) {
    ElMessage.warning(`云图文件夹需要包含6张图片，当前${cloudFiles.value.length}张`)
    return
  }

  running.value = true
  try {
    // 解析功率文件，需要恰好36行
    const allValues = await parsePowerFile(dataFile.value!)
    if (allValues.length !== 36) {
      ElMessage.error(`功率数据需要恰好36行（前30步×1分钟历史 + 后6步×5分钟真实），当前${allValues.length}行`)
      return
    }

    const histValues = allValues.slice(0, 30)
    const realValues = allValues.slice(30)

    // 构建请求
    const payload = {
      stationId: selectedStationId.value,
      modelId: selectedModel.value.modelId,
      apiKeyId: selectedApiKeyId.value,
      inputMode: 'MANUAL_MULTIMODAL',
      numericValues: buildPredictionValues(histValues),
      inputImages: cat !== 'power-sequence' ? await buildPredictionImages() : undefined,
    }

    const task = await createPrediction(payload)
    const predictions = await getPredictionResults(task.taskId)

    if (predictions.length) {
      historicalValues.value = histValues
      predictedValues.value = predictions.map((p) => p.predictPower)
      actualValues.value = realValues
      resultRows.value = normalizePredictionRows(predictions, realValues)
      ElMessage.success('预测完成')
    } else {
      ElMessage.warning('预测任务已提交，暂无结果')
    }
  } catch (e) {
    ElMessage.error('预测失败: ' + (e instanceof Error ? e.message : '未知错误'))
  } finally {
    running.value = false
  }
}

function formatFutureTime(offsetMinutes: number) {
  const date = new Date(Date.now() + offsetMinutes * 60 * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<template>
  <section
    class="page-shell model-page"
    :class="{ 'model-workbench': isVisualizationTheme }"
    v-loading="loading"
  >
    <div v-if="!isVisualizationTheme" class="page-heading">
      <h1>模型预测</h1>
    </div>
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
              <span class="file-label">功率数据文件 (36行)</span>
              <label class="file-control">
                <input
                  type="file"
                  accept=".xlsx,.xls,.csv"
                  @change="handleDataFileChange(($event.target as HTMLInputElement).files?.[0])"
                />
                <strong>{{ dataFile?.name || '选择 Excel / CSV 文件' }}</strong>
              </label>
            </div>

            <div class="file-field" v-if="needsCloudFolder">
              <span class="file-label">云图文件夹 (6张)</span>
              <button class="file-control" type="button" @click="chooseCloudFolder">
                <strong>{{ cloudFolderName || '选择云图文件夹' }}</strong>
                <small v-if="cloudFiles.length">{{ cloudFiles.length }} 个文件</small>
              </button>
            </div>
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

      <div class="result-chart">
        <svg v-if="chartData" :viewBox="`0 0 ${chartData.W} ${chartData.H}`" role="img" aria-label="功率预测曲线">
          <!-- 坐标轴 -->
          <line :x1="56" :y1="chartData.H - 34" :x2="chartData.W - 20" :y2="chartData.H - 34" class="chart-axis" />
          <line :x1="56" :y1="36" :x2="56" :y2="chartData.H - 34" class="chart-axis" />

          <!-- X轴刻度和标签 -->
          <g v-for="tick in chartData.xTicks" :key="`x-${tick.label}`">
            <line :x1="tick.x" :y1="chartData.H - 34" :x2="tick.x" :y2="chartData.H - 29" class="chart-tick" />
            <text :x="tick.x" :y="chartData.H - 18" class="chart-label" text-anchor="middle">{{ tick.label }}</text>
          </g>

          <!-- Y轴刻度和标签 -->
          <g v-for="(tick, i) in chartData.yTicks" :key="`y-${i}`">
            <line :x1="51" :y1="tick.y" :x2="56" :y2="tick.y" class="chart-tick" />
            <text :x="48" :y="tick.y + 4" class="chart-label" text-anchor="end">{{ tick.label }}</text>
          </g>

          <!-- 轴标签 -->
          <text :x="chartData.W / 2" :y="chartData.H - 2" class="chart-axis-label" text-anchor="middle">时间 (分钟)</text>
          <text :x="14" :y="chartData.H / 2" class="chart-axis-label" text-anchor="middle" transform="rotate(-90 14 130)">功率 (kW)</text>

          <!-- 历史功率线 (蓝色) -->
          <polyline :points="chartData.histPoints" class="chart-hist-line" />

          <!-- 预测功率线 (橙色) -->
          <polyline :points="chartData.predPoints" class="chart-pred-line" />

          <!-- 真实功率线 (绿色) -->
          <polyline :points="chartData.realPoints" class="chart-real-line" />

          <!-- 图例 -->
          <g transform="translate(380, 12)">
            <line x1="0" y1="6" x2="20" y2="6" class="chart-hist-line" />
            <text x="25" y="10" class="chart-legend">历史功率</text>
            <line x1="100" y1="6" x2="120" y2="6" class="chart-pred-line" />
            <text x="125" y="10" class="chart-legend">预测功率</text>
            <line x1="200" y1="6" x2="220" y2="6" class="chart-real-line" />
            <text x="225" y="10" class="chart-legend">真实功率</text>
          </g>
        </svg>
        <div v-else class="chart-empty">
          <span>请上传数据并点击"开始预测"查看结果</span>
        </div>
      </div>

      <el-table v-if="resultRows.length" :data="resultRows" size="large" style="margin-top: 14px">
        <el-table-column prop="index" label="序号" width="90" />
        <el-table-column prop="predictTime" label="预测时间" min-width="140" />
        <el-table-column label="预测功率 (kW)" min-width="160">
          <template #default="{ row }">{{ row.predictPower.toFixed(3) }}</template>
        </el-table-column>
        <el-table-column label="真实功率 (kW)" min-width="160">
          <template #default="{ row }">{{ row.actualPower != null ? row.actualPower.toFixed(3) : '—' }}</template>
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
  grid-content: center;
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

.result-chart {
  min-height: 300px;
  border: 1px solid #dfeaf7;
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.result-chart svg {
  display: block;
  width: 100%;
  height: 300px;
}

.chart-empty {
  display: grid;
  place-items: center;
  min-height: 300px;
  color: var(--color-muted);
  font-size: 14px;
}

.chart-axis {
  stroke: #cfdae8;
  stroke-width: 1;
}

.chart-tick {
  stroke: #cfdae8;
  stroke-width: 1;
}

.chart-label {
  fill: #606266;
  font-size: 11px;
}

.chart-axis-label {
  fill: #909399;
  font-size: 12px;
}

.chart-hist-line {
  fill: none;
  stroke: #409eff;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.chart-pred-line {
  fill: none;
  stroke: #e6a23c;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.5;
}

.chart-real-line {
  fill: none;
  stroke: #67c23a;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.5;
}

.chart-legend {
  fill: #606266;
  font-size: 12px;
}

@media (max-width: 1180px) {
  .control-panel,
  .control-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .model-picker,
  .input-row {
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

/* Prediction workbench — deliberately denser than the generic module cards. */
.model-workbench {
  --mp-ink: #071a34;
  --mp-panel: rgba(9, 35, 67, 0.86);
  --mp-panel-strong: #0b284b;
  --mp-line: rgba(114, 202, 239, 0.22);
  --mp-cyan: #6ee7f5;
  --mp-sun: #ffca66;
  gap: 18px;
  max-width: 1680px;
  margin: 0 auto;
}

.model-workbench .page-heading {
  position: relative;
  min-height: 84px;
  padding: 14px 4px 14px 76px;
  display: flex;
  align-items: center;
}

.model-workbench .page-heading::before {
  content: "AI";
  position: absolute;
  left: 0;
  top: 10px;
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(110, 231, 245, 0.5);
  background: linear-gradient(145deg, rgba(110, 231, 245, 0.14), rgba(7, 26, 52, 0.25));
  color: var(--mp-cyan);
  font: 700 15px/1 "Arial Narrow", "Microsoft YaHei", sans-serif;
  letter-spacing: 0.18em;
  box-shadow: inset 0 0 24px rgba(110, 231, 245, 0.08);
  clip-path: polygon(0 0, 82% 0, 100% 18%, 100% 100%, 18% 100%, 0 82%);
}

.model-workbench .page-heading h1 {
  margin: 0;
  font-size: clamp(26px, 2vw, 36px);
  font-weight: 650;
  letter-spacing: 0.04em;
}

.model-workbench .page-heading::after {
  content: "组合数据源与预测模型，生成未来功率曲线";
  position: absolute;
  left: 78px;
  bottom: 5px;
  color: rgba(178, 205, 229, 0.68);
  font-size: 13px;
  letter-spacing: 0.04em;
}

.model-workbench .page-section {
  border: 1px solid var(--mp-line);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(10, 38, 72, 0.94), rgba(6, 25, 50, 0.9));
  box-shadow: 0 22px 50px rgba(1, 11, 27, 0.18), inset 0 1px rgba(255, 255, 255, 0.025);
}

.model-workbench .control-panel {
  position: relative;
  padding: 0;
  overflow: visible;
}

.model-workbench .control-grid {
  grid-template-columns: 1.08fr 0.92fr;
  gap: 0;
}

.model-workbench .control-group {
  position: relative;
  overflow: visible;
  padding: 52px 30px 28px;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.model-workbench .control-group + .control-group {
  border-left: 1px solid var(--mp-line);
}

.model-workbench .control-group::before {
  position: absolute;
  left: 30px;
  top: 20px;
  color: var(--mp-cyan);
  font: 700 11px/1 "Arial Narrow", sans-serif;
  letter-spacing: 0.16em;
}

.model-workbench .control-group:first-child::before { content: "01  选择预测引擎"; }
.model-workbench .control-group:last-child::before { content: "02  接入输入数据"; }

.model-workbench .model-picker {
  grid-template-columns: minmax(160px, 0.72fr) minmax(260px, 1.28fr);
  gap: 16px;
}

.model-workbench :deep(.el-form-item__label),
.model-workbench .file-label {
  height: auto;
  margin-bottom: 9px;
  color: rgba(185, 211, 233, 0.72);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: 0.06em;
}

.model-workbench :deep(.el-select__wrapper) {
  min-height: 46px;
  border: 1px solid rgba(105, 177, 218, 0.26);
  border-radius: 7px;
  background: rgba(4, 22, 45, 0.66);
  box-shadow: none;
}

.model-workbench :deep(.el-select__wrapper:hover),
.model-workbench :deep(.el-select__wrapper.is-focused) {
  border-color: rgba(110, 231, 245, 0.7);
  box-shadow: 0 0 0 3px rgba(110, 231, 245, 0.08);
}

.model-workbench :deep(.el-select__selected-item) { color: #e9f7ff; }

.model-workbench .input-row { gap: 14px; }

.model-workbench .file-control {
  position: relative;
  min-height: 86px;
  padding: 16px 16px 16px 54px;
  border: 1px dashed rgba(110, 231, 245, 0.34);
  border-radius: 8px;
  background: rgba(5, 25, 50, 0.56);
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease;
}

.model-workbench .file-control::before {
  content: "+";
  position: absolute;
  left: 16px;
  top: 50%;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(110, 231, 245, 0.55);
  border-radius: 50%;
  color: var(--mp-cyan);
  font: 300 20px/1 sans-serif;
  transform: translateY(-50%);
}

.model-workbench .file-control:hover {
  border-color: var(--mp-cyan);
  background: rgba(21, 67, 98, 0.46);
  transform: translateY(-2px);
}

.model-workbench .file-control strong { color: #e7f5ff; font-size: 13px; }

.model-workbench .result-section { padding: 26px 30px 30px; }

.model-workbench .panel-head {
  align-items: center;
  margin-bottom: 22px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--mp-line);
}

.model-workbench .panel-head h3 {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: 0.04em;
}

.model-workbench .panel-head h3::before {
  content: "03";
  margin-right: 12px;
  color: var(--mp-cyan);
  font: 700 11px/1 "Arial Narrow", sans-serif;
  letter-spacing: 0.12em;
}

.model-workbench :deep(.el-button--primary) {
  min-width: 132px;
  height: 44px;
  border: 0;
  border-radius: 6px;
  background: linear-gradient(135deg, #39b9dd, #62dce9);
  color: #061d35;
  font-weight: 800;
  box-shadow: 0 8px 22px rgba(64, 205, 229, 0.22);
}

.model-workbench :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #61d7ed, #87edf3);
  transform: translateY(-1px);
}

.model-workbench .result-chart {
  position: relative;
  min-height: 340px;
  overflow: hidden;
  border-color: rgba(110, 231, 245, 0.18);
  border-radius: 10px;
  background-color: rgba(4, 22, 44, 0.72);
  background-image: linear-gradient(rgba(100, 184, 218, 0.055) 1px, transparent 1px), linear-gradient(90deg, rgba(100, 184, 218, 0.055) 1px, transparent 1px);
  background-size: 100% 25%, 12.5% 100%;
}

.model-workbench .result-chart::before {
  content: "FUTURE OUTPUT · kW";
  position: absolute;
  left: 18px;
  top: 16px;
  color: rgba(150, 194, 220, 0.5);
  font: 700 10px/1 "Arial Narrow", sans-serif;
  letter-spacing: 0.14em;
  z-index: 1;
}

.model-workbench .result-chart svg { height: 340px; position: relative; z-index: 0; }
.model-workbench .chart-axis { stroke: rgba(178, 213, 232, 0.34); }
.model-workbench .chart-tick { stroke: rgba(178, 213, 232, 0.34); }
.model-workbench .chart-label { fill: rgba(178, 205, 229, 0.7); }
.model-workbench .chart-axis-label { fill: rgba(178, 205, 229, 0.55); }
.model-workbench .chart-hist-line { stroke: #6ee7f5; stroke-width: 2; filter: drop-shadow(0 0 4px rgba(110, 231, 245, 0.3)); }
.model-workbench .chart-pred-line { stroke: #ffca66; stroke-width: 2.5; filter: drop-shadow(0 0 4px rgba(255, 202, 102, 0.3)); }
.model-workbench .chart-real-line { stroke: #95e1a3; stroke-width: 2.5; filter: drop-shadow(0 0 4px rgba(149, 225, 163, 0.3)); }
.model-workbench .chart-legend { fill: rgba(178, 205, 229, 0.7); }
.model-workbench .chart-empty { color: rgba(178, 205, 229, 0.5); }

.model-workbench :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(7, 29, 56, 0.92);
  --el-table-row-hover-bg-color: rgba(43, 105, 137, 0.18);
  --el-table-border-color: rgba(110, 190, 225, 0.13);
  --el-table-text-color: rgba(220, 238, 249, 0.82);
  --el-table-header-text-color: rgba(160, 199, 223, 0.72);
  overflow: hidden;
  border: 1px solid rgba(110, 190, 225, 0.13);
  border-radius: 9px;
}

@media (max-width: 1180px) {
  .model-workbench .control-grid { grid-template-columns: 1fr; }
  .model-workbench .control-group + .control-group { border-left: 0; border-top: 1px solid var(--mp-line); }
}

@media (max-width: 760px) {
  .model-workbench .page-heading { padding-left: 64px; }
  .model-workbench .page-heading::after { left: 66px; font-size: 11px; }
  .model-workbench .control-group { padding: 50px 18px 22px; }
  .model-workbench .control-group::before { left: 18px; }
  .model-workbench .result-section { padding: 20px 16px; }
}

@media (prefers-reduced-motion: reduce) {
  .model-workbench .file-control,
  .model-workbench :deep(.el-button--primary) { transition: none; }
}
</style>
