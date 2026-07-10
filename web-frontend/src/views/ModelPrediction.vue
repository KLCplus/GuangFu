<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getModels } from '../api/model'
import { createPrediction, getPredictionResults } from '../api/prediction'
import { getStations } from '../api/station'
import type { Model, ModelStatus } from '../api/model'
import type { PredictionResult } from '../api/prediction'
import { mockStations } from '../data/mock'

type ModelCategory = 'time-series' | 'video-recursive' | 'multimodal'

interface ModelCategoryOption {
  value: ModelCategory
  label: string
  countLabel: string
}

interface StationOption {
  stationId: number
  stationName: string
}

interface UsableModel {
  modelId: number
  modelName: string
  modelCode: string
  modelType: string
  modelVersion: string
  category: ModelCategory
  modelStatus: ModelStatus
  description: string
  inputRequirement: string
  outputResult: string
  scenario: string
  tags: string[]
  score: number
  latency: number
  inputWindowMinutes: number
  outputSteps: number
  outputStepMinutes: number
}

interface ResultRow {
  timeOffset: number
  predictTime: string
  predictPower: number
  lowerBound: number
  upperBound: number
  confidence: number
}

interface TaskSummary {
  taskNo: string
  status: string
  costTime: number
  source: string
}

const categories: ModelCategoryOption[] = [
  { value: 'time-series', label: '时序模型', countLabel: '功率序列' },
  { value: 'video-recursive', label: '视频时空递归模型', countLabel: '云图帧序列' },
  { value: 'multimodal', label: '多模态模型', countLabel: '天气 + 图像 + 功率' }
]

const fallbackModels: UsableModel[] = [
  {
    modelId: 1001,
    modelName: 'PatchTST 短期功率预测',
    modelCode: 'patchtst_short',
    modelType: 'NUMERIC',
    modelVersion: 'v1.2',
    category: 'time-series',
    modelStatus: 'ONLINE',
    description: '面向光伏功率序列的 Transformer 分块预测模型，适合稳定站点的短时预测。',
    inputRequirement: '读取过去 30 分钟功率、辐照度和温度序列。',
    outputResult: '输出未来 6 个步长，每步 5 分钟的功率预测。',
    scenario: '常规电站短时调度、日报预测、异常对比。',
    tags: ['PatchTST', '30min', '低延迟'],
    score: 92.6,
    latency: 118,
    inputWindowMinutes: 30,
    outputSteps: 6,
    outputStepMinutes: 5
  },
  {
    modelId: 1002,
    modelName: 'iTransformer 融合序列模型',
    modelCode: 'itransformer_pv',
    modelType: 'NUMERIC',
    modelVersion: 'v2.0',
    category: 'time-series',
    modelStatus: 'TESTING',
    description: '强化多变量相关性的时序模型，适合天气波动较大的电站。',
    inputRequirement: '功率、辐照度、温度、湿度和风速多变量序列。',
    outputResult: '输出功率曲线、置信区间和任务耗时。',
    scenario: '天气敏感站点的预测对比和模型试用。',
    tags: ['iTransformer', '多变量', '测试'],
    score: 90.8,
    latency: 146,
    inputWindowMinutes: 30,
    outputSteps: 6,
    outputStepMinutes: 5
  },
  {
    modelId: 2001,
    modelName: 'SimVP+GSTA 云图递归预测',
    modelCode: 'simvp_gsta',
    modelType: 'IMAGE',
    modelVersion: 'v1.0',
    category: 'video-recursive',
    modelStatus: 'ONLINE',
    description: '使用连续云图帧做时空递归，预测云层运动对未来功率的影响。',
    inputRequirement: '连续 10 帧历史云图和最近功率摘要。',
    outputResult: '输出云图未来帧摘要和对应功率预测。',
    scenario: '云量变化快、晴雨切换频繁的场景。',
    tags: ['SimVP', 'GSTA', '云图'],
    score: 88.4,
    latency: 236,
    inputWindowMinutes: 50,
    outputSteps: 10,
    outputStepMinutes: 5
  },
  {
    modelId: 2002,
    modelName: 'PredRNN 递归时空模型',
    modelCode: 'predrnn_video',
    modelType: 'IMAGE',
    modelVersion: 'v1.1',
    category: 'video-recursive',
    modelStatus: 'ONLINE',
    description: '递归建模连续帧变化，用于云图视频序列和功率趋势联合预测。',
    inputRequirement: '历史云图帧序列、站点容量和最近功率均值。',
    outputResult: '输出未来时刻功率曲线和时空趋势标签。',
    scenario: '连续帧预测演示、视频模型能力展示。',
    tags: ['PredRNN', '递归', '时空'],
    score: 87.9,
    latency: 254,
    inputWindowMinutes: 50,
    outputSteps: 10,
    outputStepMinutes: 5
  },
  {
    modelId: 3001,
    modelName: 'CNN+LSTM 多模态预测',
    modelCode: 'cnn_lstm_multi',
    modelType: 'MULTIMODAL',
    modelVersion: 'v1.4',
    category: 'multimodal',
    modelStatus: 'ONLINE',
    description: '融合云图特征、天气字段和功率序列，提升遮挡变化下的预测稳定性。',
    inputRequirement: '天气、功率、辐照度、云图特征和站点基础信息。',
    outputResult: '输出功率预测、置信区间和主要影响因子。',
    scenario: '综合分析报告、复杂天气下的业务预测。',
    tags: ['CNN', 'LSTM', '多模态'],
    score: 94.1,
    latency: 192,
    inputWindowMinutes: 30,
    outputSteps: 6,
    outputStepMinutes: 5
  },
  {
    modelId: 3002,
    modelName: '3D-CNN+LSTM 融合模型',
    modelCode: '3dcnn_lstm',
    modelType: 'MULTIMODAL',
    modelVersion: 'v1.0',
    category: 'multimodal',
    modelStatus: 'TESTING',
    description: '把云图局部时空块与气象序列一起建模，适合做模型效果对比。',
    inputRequirement: '云图片段、历史功率和气象序列。',
    outputResult: '输出未来功率、误差预估和推理耗时。',
    scenario: '模型评测、广场试用、论文式对比展示。',
    tags: ['3D-CNN', 'LSTM', '评测'],
    score: 91.3,
    latency: 228,
    inputWindowMinutes: 30,
    outputSteps: 6,
    outputStepMinutes: 5
  }
]

const loading = ref(false)
const running = ref(false)
const selectedCategory = ref<ModelCategory>('time-series')
const selectedModelId = ref<number>()
const models = ref<UsableModel[]>([])
const stations = ref<StationOption[]>([])
const resultRows = ref<ResultRow[]>([])
const taskSummary = ref<TaskSummary>({
  taskNo: '未调用',
  status: '待运行',
  costTime: 0,
  source: '暂无'
})

const form = reactive({
  stationId: undefined as number | undefined,
  inputRange: 'past-30',
  inputTypes: ['power', 'weather', 'irradiance'] as string[],
  outputSteps: 6,
  outputStepMinutes: 5
})

const filteredModels = computed(() => models.value.filter((item) => item.category === selectedCategory.value))

const selectedModel = computed(() =>
  models.value.find((item) => item.modelId === selectedModelId.value) ?? filteredModels.value[0]
)

const selectedCategoryInfo = computed(() => categories.find((item) => item.value === selectedCategory.value))

const resultStats = computed(() => {
  if (!resultRows.value.length) {
    return { max: 0, avg: 0, confidence: 0 }
  }
  const total = resultRows.value.reduce((sum, item) => sum + item.predictPower, 0)
  const confidence = resultRows.value.reduce((sum, item) => sum + item.confidence, 0)
  return {
    max: Math.max(...resultRows.value.map((item) => item.predictPower)),
    avg: total / resultRows.value.length,
    confidence: confidence / resultRows.value.length
  }
})

const resultChartPoints = computed(() => {
  const values = resultRows.value.map((item) => item.predictPower)
  if (!values.length) return ''
  const width = 560
  const height = 160
  const padding = 18
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = Math.max(max - min, 1)
  return values
    .map((value, index) => {
      const x = padding + (index / Math.max(values.length - 1, 1)) * (width - padding * 2)
      const y = height - padding - ((value - min) / range) * (height - padding * 2)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

onMounted(async () => {
  loading.value = true
  await Promise.all([loadModels(), loadStations()])
  selectedModelId.value = filteredModels.value[0]?.modelId
  resultRows.value = generateResultRows()
  loading.value = false
})

async function loadModels() {
  try {
    const records = await getModels()
    const apiModels = records.map(mapApiModel)
    const categorySet = new Set(apiModels.map((item) => item.category))
    const supplements = fallbackModels.filter((item) => !categorySet.has(item.category))
    models.value = [...apiModels, ...supplements]
  } catch {
    models.value = fallbackModels
  }
}

async function loadStations() {
  try {
    const result = await getStations({ pageNum: 1, pageSize: 50 })
    const records = result.records.length ? result.records : mockStations
    stations.value = records.map((item) => ({
      stationId: item.stationId,
      stationName: item.stationName
    }))
  } catch {
    stations.value = mockStations.map((item) => ({
      stationId: item.stationId,
      stationName: item.stationName
    }))
  }
  form.stationId = stations.value[0]?.stationId
}

function mapApiModel(model: Model): UsableModel {
  const category = getModelCategory(model)
  const preset = fallbackModels.find((item) => item.category === category) ?? fallbackModels[0]
  return {
    modelId: model.modelId,
    modelName: model.modelName,
    modelCode: model.modelCode || preset.modelCode,
    modelType: model.modelType,
    modelVersion: model.modelVersion || preset.modelVersion,
    category,
    modelStatus: model.modelStatus || model.status || preset.modelStatus,
    description: model.description || preset.description,
    inputRequirement: categoryText(category, 'input'),
    outputResult: categoryText(category, 'output'),
    scenario: categoryText(category, 'scenario'),
    tags: preset.tags,
    score: preset.score,
    latency: preset.latency,
    inputWindowMinutes: model.inputWindowMinutes || preset.inputWindowMinutes,
    outputSteps: model.outputSteps || preset.outputSteps,
    outputStepMinutes: model.outputStepMinutes || preset.outputStepMinutes
  }
}

function getModelCategory(model: Pick<Model, 'modelName' | 'modelCode' | 'modelType'>): ModelCategory {
  const text = [model.modelName, model.modelCode, model.modelType]
    .filter((value): value is string => typeof value === 'string')
    .join(' ')
    .toLowerCase()
  if (
    text.includes('simvp') ||
    text.includes('predrnn') ||
    text.includes('convlstm') ||
    text.includes('e3d') ||
    text.includes('swin') ||
    text.includes('sunset') ||
    text.includes('tau') ||
    text.includes('video')
  ) {
    return 'video-recursive'
  }
  if (
    text.includes('multi') ||
    text.includes('多模态') ||
    text.includes('image') ||
    text.includes('cnn') ||
    text.includes('vision')
  ) {
    return 'multimodal'
  }
  return 'time-series'
}

function categoryText(category: ModelCategory, field: 'input' | 'output' | 'scenario') {
  const text: Record<ModelCategory, Record<typeof field, string>> = {
    'time-series': {
      input: '读取过去 30 分钟功率、辐照度和天气序列。',
      output: '输出未来 6 个步长，每步 5 分钟的功率预测。',
      scenario: '常规电站短期预测和预测历史对比。'
    },
    'video-recursive': {
      input: '读取连续云图帧、站点功率摘要和天气标签。',
      output: '输出未来帧趋势和对应功率预测。',
      scenario: '云图预测、遮挡变化和视频模型演示。'
    },
    multimodal: {
      input: '读取功率、天气、辐照度、云图特征和站点容量。',
      output: '输出功率预测、置信区间和关键影响因子。',
      scenario: '复杂天气下的综合预测和报告分析。'
    }
  }
  return text[category][field]
}

function selectCategory(value: ModelCategory) {
  selectedCategory.value = value
  const firstModel = filteredModels.value[0]
  if (firstModel) {
    selectModel(firstModel)
  }
}

function selectModel(model: UsableModel) {
  selectedModelId.value = model.modelId
  form.outputSteps = model.outputSteps
  form.outputStepMinutes = model.outputStepMinutes
  resultRows.value = generateResultRows()
}

async function runPrediction() {
  if (!form.stationId) {
    ElMessage.warning('请先选择电站')
    return
  }
  if (!selectedModel.value) {
    ElMessage.warning('请先选择模型')
    return
  }

  running.value = true
  taskSummary.value = {
    taskNo: '运行中',
    status: 'RUNNING',
    costTime: 0,
    source: '实时接口'
  }

  try {
    const task = await createPrediction({
      stationId: form.stationId,
      modelId: selectedModel.value.modelId,
      inputMode: 'STATION_HISTORY'
    })
    const predictions = task.predictions?.length
      ? task.predictions
      : await getPredictionResults(task.taskId).catch(() => [] as PredictionResult[])
    resultRows.value = predictions.length ? normalizePredictionRows(predictions) : generateResultRows()
    taskSummary.value = {
      taskNo: task.taskNo || `TASK-${task.taskId}`,
      status: task.taskStatus,
      costTime: task.costTime ?? selectedModel.value.latency,
      source: predictions.length ? '实时接口' : '演示结果'
    }
    ElMessage.success('模型调用完成')
  } catch {
    resultRows.value = generateResultRows()
    taskSummary.value = {
      taskNo: `DEMO-${Date.now().toString().slice(-6)}`,
      status: 'SUCCESS',
      costTime: selectedModel.value.latency,
      source: '演示结果'
    }
    ElMessage.warning('模型服务暂不可用，已展示演示预测结果')
  } finally {
    running.value = false
  }
}

function normalizePredictionRows(values: Partial<PredictionResult>[]): ResultRow[] {
  return values.map((item, index) => {
    const timeOffset = Number(item.timeOffset ?? (index + 1) * form.outputStepMinutes)
    const predictPower = Number(item.predictPower ?? 0)
    const confidence = Math.max(82, 96 - index * 2)
    return {
      timeOffset,
      predictTime: item.predictTime ?? formatFutureTime(timeOffset),
      predictPower,
      lowerBound: Number((predictPower * 0.96).toFixed(1)),
      upperBound: Number((predictPower * 1.04).toFixed(1)),
      confidence
    }
  })
}

function generateResultRows(): ResultRow[] {
  const model = selectedModel.value ?? fallbackModels[0]
  const stationSeed = form.stationId ?? 1
  const categorySeed = categories.findIndex((item) => item.value === model.category) + 1
  const base = 480 + stationSeed * 18 + categorySeed * 24
  const steps = Math.max(1, Number(form.outputSteps || model.outputSteps))
  const stepMinutes = Math.max(1, Number(form.outputStepMinutes || model.outputStepMinutes))
  return Array.from({ length: steps }, (_, index) => {
    const curve = Math.sin((index + 1) / steps) * 16
    const predictPower = Number((base + index * 7.2 + curve).toFixed(1))
    const confidence = Math.max(84, 96 - index * 2)
    return {
      timeOffset: (index + 1) * stepMinutes,
      predictTime: formatFutureTime((index + 1) * stepMinutes),
      predictPower,
      lowerBound: Number((predictPower * 0.96).toFixed(1)),
      upperBound: Number((predictPower * 1.04).toFixed(1)),
      confidence
    }
  })
}

function formatFutureTime(offsetMinutes: number) {
  const date = new Date(Date.now() + offsetMinutes * 60 * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function statusTagType(status: string) {
  if (status === 'ONLINE' || status === 'SUCCESS') return 'success'
  if (status === 'TESTING' || status === 'RUNNING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function statusText(status: string) {
  const map: Record<string, string> = {
    ONLINE: '在线',
    OFFLINE: '离线',
    TESTING: '测试中',
    SUCCESS: '成功',
    RUNNING: '运行中',
    FAILED: '失败'
  }
  return map[status] ?? status
}
</script>

<template>
  <section class="page-shell model-page" v-loading="loading">
    <div class="page-section model-header">
      <div>
        <p class="page-kicker">模型选择与使用界面</p>
        <h2>{{ selectedCategoryInfo?.label }}</h2>
        <p>{{ selectedCategoryInfo?.countLabel }}</p>
      </div>
      <el-radio-group v-model="selectedCategory" size="large" @change="selectCategory">
        <el-radio-button v-for="item in categories" :key="item.value" :label="item.value">
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <div class="model-grid">
      <section class="page-section model-list-section">
        <div class="panel-head">
          <div>
            <p class="page-kicker">模型选择</p>
            <h3>可用模型</h3>
          </div>
          <el-tag>{{ filteredModels.length }} 个</el-tag>
        </div>
        <div class="model-list">
          <button
            v-for="model in filteredModels"
            :key="model.modelId"
            class="model-card"
            :class="{ active: model.modelId === selectedModel?.modelId }"
            type="button"
            @click="selectModel(model)"
          >
            <span class="model-card-top">
              <strong>{{ model.modelName }}</strong>
              <el-tag :type="statusTagType(model.modelStatus)" effect="light">
                {{ statusText(model.modelStatus) }}
              </el-tag>
            </span>
            <small>{{ model.modelCode }} · {{ model.modelVersion }}</small>
            <p>{{ model.description }}</p>
            <span class="model-tags">
              <em v-for="tag in model.tags" :key="tag">{{ tag }}</em>
            </span>
          </button>
        </div>
      </section>

      <section class="page-section model-detail-section" v-if="selectedModel">
        <div class="panel-head">
          <div>
            <p class="page-kicker">模型说明</p>
            <h3>{{ selectedModel.modelName }}</h3>
          </div>
          <el-tag type="primary">{{ selectedModel.modelType }}</el-tag>
        </div>
        <div class="detail-grid">
          <div>
            <span>模型简介</span>
            <p>{{ selectedModel.description }}</p>
          </div>
          <div>
            <span>输入要求</span>
            <p>{{ selectedModel.inputRequirement }}</p>
          </div>
          <div>
            <span>输出结果</span>
            <p>{{ selectedModel.outputResult }}</p>
          </div>
          <div>
            <span>适用场景</span>
            <p>{{ selectedModel.scenario }}</p>
          </div>
        </div>
        <div class="model-kpis">
          <div>
            <span>评分</span>
            <strong>{{ selectedModel.score }}</strong>
          </div>
          <div>
            <span>输入窗口</span>
            <strong>{{ selectedModel.inputWindowMinutes }} min</strong>
          </div>
          <div>
            <span>输出步长</span>
            <strong>{{ selectedModel.outputStepMinutes }} min</strong>
          </div>
          <div>
            <span>平均耗时</span>
            <strong>{{ selectedModel.latency }} ms</strong>
          </div>
        </div>
      </section>
    </div>

    <div class="run-grid">
      <section class="page-section config-section">
        <div class="panel-head">
          <div>
            <p class="page-kicker">模型调用</p>
            <h3>参数配置</h3>
          </div>
          <el-button type="primary" size="large" :loading="running" @click="runPrediction">
            调用模型
          </el-button>
        </div>
        <el-form label-position="top" class="config-form">
          <el-form-item label="电站">
            <el-select v-model="form.stationId" class="full-control" @change="resultRows = generateResultRows()">
              <el-option
                v-for="station in stations"
                :key="station.stationId"
                :label="station.stationName"
                :value="station.stationId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="输入范围">
            <el-select v-model="form.inputRange" class="full-control">
              <el-option label="过去 30 分钟" value="past-30" />
              <el-option label="过去 60 分钟" value="past-60" />
              <el-option label="最近一次成功任务" value="latest-task" />
            </el-select>
          </el-form-item>
          <el-form-item label="输入数据类型">
            <el-checkbox-group v-model="form.inputTypes">
              <el-checkbox label="power">功率</el-checkbox>
              <el-checkbox label="weather">天气</el-checkbox>
              <el-checkbox label="irradiance">辐照度</el-checkbox>
              <el-checkbox label="cloud">云图</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <div class="step-controls">
            <el-form-item label="预测步数">
              <el-input-number v-model="form.outputSteps" :min="1" :max="12" @change="resultRows = generateResultRows()" />
            </el-form-item>
            <el-form-item label="步长(分钟)">
              <el-input-number
                v-model="form.outputStepMinutes"
                :min="5"
                :max="30"
                :step="5"
                @change="resultRows = generateResultRows()"
              />
            </el-form-item>
          </div>
        </el-form>
      </section>

      <section class="page-section result-section">
        <div class="panel-head">
          <div>
            <p class="page-kicker">预测结果展示</p>
            <h3>{{ taskSummary.taskNo }}</h3>
          </div>
          <el-tag :type="statusTagType(taskSummary.status)">{{ statusText(taskSummary.status) }}</el-tag>
        </div>
        <div class="result-kpis">
          <span>均值 <b>{{ resultStats.avg.toFixed(1) }} kW</b></span>
          <span>峰值 <b>{{ resultStats.max.toFixed(1) }} kW</b></span>
          <span>置信度 <b>{{ resultStats.confidence.toFixed(1) }}%</b></span>
          <span>来源 <b>{{ taskSummary.source }}</b></span>
        </div>
        <div class="result-chart">
          <svg viewBox="0 0 560 160" role="img" aria-label="模型预测结果曲线">
            <line x1="18" y1="142" x2="542" y2="142" class="chart-axis" />
            <polyline :points="resultChartPoints" class="chart-line" />
            <circle
              v-for="point in resultChartPoints.split(' ').filter(Boolean)"
              :key="point"
              :cx="Number(point.split(',')[0])"
              :cy="Number(point.split(',')[1])"
              r="4"
              class="chart-point"
            />
          </svg>
        </div>
        <el-table :data="resultRows" size="large">
          <el-table-column prop="timeOffset" label="步长" width="88">
            <template #default="{ row }">+{{ row.timeOffset }} min</template>
          </el-table-column>
          <el-table-column prop="predictTime" label="预测时间" width="110" />
          <el-table-column prop="predictPower" label="预测功率(kW)" min-width="130">
            <template #default="{ row }">{{ row.predictPower.toFixed(1) }}</template>
          </el-table-column>
          <el-table-column label="置信区间(kW)" min-width="150">
            <template #default="{ row }">{{ row.lowerBound }} - {{ row.upperBound }}</template>
          </el-table-column>
          <el-table-column prop="confidence" label="置信度" width="100">
            <template #default="{ row }">{{ row.confidence }}%</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </section>
</template>

<style scoped>
.model-page {
  gap: 18px;
}

.model-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.model-header h2,
.panel-head h3 {
  margin: 4px 0 0;
}

.model-header p:not(.page-kicker) {
  margin: 8px 0 0;
  color: var(--color-muted);
}

.model-grid {
  display: grid;
  grid-template-columns: 420px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.model-list {
  display: grid;
  gap: 12px;
}

.model-card {
  width: 100%;
  min-height: 148px;
  padding: 15px;
  border: 1px solid #dce7f4;
  border-radius: 8px;
  background: #ffffff;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.model-card:hover,
.model-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 10px 24px rgba(29, 111, 220, 0.12);
  transform: translateY(-1px);
}

.model-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.model-card strong {
  color: #10274c;
}

.model-card small {
  display: block;
  margin-top: 8px;
  color: var(--color-muted);
}

.model-card p {
  margin: 10px 0;
  color: #3f4f66;
  line-height: 1.6;
}

.model-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.model-tags em {
  padding: 4px 8px;
  border-radius: 8px;
  background: #eef6ff;
  color: var(--color-primary);
  font-size: 12px;
  font-style: normal;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid div,
.model-kpis div,
.result-kpis span {
  border-radius: 8px;
  background: #f6f9fd;
}

.detail-grid div {
  min-height: 118px;
  padding: 14px;
}

.detail-grid span,
.model-kpis span {
  color: var(--color-muted);
  font-size: 13px;
}

.detail-grid p {
  margin: 8px 0 0;
  color: #172033;
  line-height: 1.7;
}

.model-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.model-kpis div {
  min-height: 86px;
  padding: 12px;
}

.model-kpis strong {
  display: block;
  margin-top: 10px;
  color: #10274c;
  font-size: 22px;
}

.run-grid {
  display: grid;
  grid-template-columns: 420px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.config-form {
  display: grid;
  gap: 4px;
}

.full-control {
  width: 100%;
}

.step-controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.result-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.result-kpis span {
  min-height: 70px;
  padding: 12px;
  color: var(--color-muted);
}

.result-kpis b {
  display: block;
  margin-top: 8px;
  color: #10274c;
  font-size: 18px;
}

.result-chart {
  height: 190px;
  margin-bottom: 12px;
  border: 1px solid #dfeaf7;
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.result-chart svg {
  width: 100%;
  height: 100%;
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
  .model-grid,
  .run-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .model-header,
  .panel-head {
    align-items: stretch;
    flex-direction: column;
  }

  .detail-grid,
  .model-kpis,
  .step-controls,
  .result-kpis {
    grid-template-columns: 1fr;
  }
}
</style>
