<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createModel,
  getAdminModels,
  updateModelStatus,
  type Model,
  type ModelPayload,
  type ModelStatus
} from '../../api/model'

type ManagedStatus = 'ONLINE' | 'OFFLINE' | 'TESTING'

interface ModelForm {
  modelCode: string
  modelName: string
  modelType: string
  modelVersion: string
  serviceModelName: string
  apiPath: string
  inputWindowMinutes: number
  inputFrameIntervalSeconds: number
  outputSteps: number
  outputStepMinutes: number
  inputSchema: string
  outputSchema: string
  description: string
}

const filters = reactive({ keyword: '', type: '', status: '' })
const rows = ref<Model[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const statusUpdatingId = ref<number>()

const emptyForm = (): ModelForm => ({
  modelCode: '',
  modelName: '',
  modelType: 'NUMERIC',
  modelVersion: 'v1.0',
  serviceModelName: '',
  apiPath: '/model-api/predict',
  inputWindowMinutes: 30,
  inputFrameIntervalSeconds: 60,
  outputSteps: 6,
  outputStepMinutes: 5,
  inputSchema: '{}',
  outputSchema: '{}',
  description: ''
})

const form = reactive<ModelForm>(emptyForm())

const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesKeyword =
      !keyword || [row.modelName, row.modelCode || '', row.serviceModelName || ''].some((value) =>
        value.toLowerCase().includes(keyword)
      )
    const matchesType = !filters.type || row.modelType === filters.type
    const matchesStatus = !filters.status || modelStatus(row) === filters.status
    return matchesKeyword && matchesType && matchesStatus
  })
})

const summaryCards = computed(() => [
  { label: '模型总数', value: rows.value.length },
  { label: '已上线', value: rows.value.filter((row) => modelStatus(row) === 'ONLINE').length },
  { label: '测试中', value: rows.value.filter((row) => modelStatus(row) === 'TESTING').length },
  { label: '已下线', value: rows.value.filter((row) => modelStatus(row) === 'OFFLINE').length }
])

function modelStatus(row: Model): ManagedStatus {
  const status = row.modelStatus || row.status
  if (status === 'ONLINE' || status === 'TESTING') return status
  return 'OFFLINE'
}

function statusText(status: ManagedStatus) {
  return { ONLINE: '已上线', TESTING: '测试中', OFFLINE: '已下线' }[status]
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function payload(): ModelPayload {
  return {
    modelCode: form.modelCode.trim(),
    modelName: form.modelName.trim(),
    modelType: form.modelType,
    modelVersion: form.modelVersion.trim(),
    serviceModelName: form.serviceModelName.trim(),
    apiPath: form.apiPath.trim(),
    inputWindowMinutes: form.inputWindowMinutes,
    inputFrameIntervalSeconds: form.inputFrameIntervalSeconds,
    outputSteps: form.outputSteps,
    outputStepMinutes: form.outputStepMinutes,
    inputSchema: form.inputSchema,
    outputSchema: form.outputSchema,
    description: form.description.trim()
  }
}

async function saveModel() {
  if (!form.modelCode.trim() || !form.modelName.trim() || !form.serviceModelName.trim()) {
    ElMessage.warning('请填写模型编码、模型名称和服务模型名称')
    return
  }

  saving.value = true
  try {
    await createModel(payload())
    ElMessage.success('模型已创建')
    dialogVisible.value = false
    await loadModels()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型保存失败')
  } finally {
    saving.value = false
  }
}

async function changeStatus(row: Model, status: ManagedStatus) {
  if (modelStatus(row) === status) return
  statusUpdatingId.value = row.modelId
  try {
    await updateModelStatus(row.modelId, { modelStatus: status as ModelStatus })
    row.status = status
    row.modelStatus = status
    ElMessage.success(`模型已切换为${statusText(status)}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型状态更新失败')
  } finally {
    statusUpdatingId.value = undefined
  }
}

async function loadModels() {
  loading.value = true
  try {
    rows.value = await getAdminModels()
  } catch (error) {
    rows.value = []
    ElMessage.error(error instanceof Error ? error.message : '模型列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.type = ''
  filters.status = ''
}

onMounted(loadModels)
</script>

<template>
  <section class="page-shell admin-model-page">
    <div class="page-title">
      <div>
        <h2>模型管理</h2>
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
          <el-input v-model="filters.keyword" clearable placeholder="搜索模型、编码或服务名" />
          <el-select v-model="filters.type" clearable placeholder="模型类型">
            <el-option label="NUMERIC" value="NUMERIC" />
            <el-option label="IMAGE_TO_NUMERIC" value="IMAGE_TO_NUMERIC" />
            <el-option label="MULTIMODAL" value="MULTIMODAL" />
            <el-option label="FUSION" value="FUSION" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="状态">
            <el-option label="已上线" value="ONLINE" />
            <el-option label="测试中" value="TESTING" />
            <el-option label="已下线" value="OFFLINE" />
          </el-select>
        </div>
        <el-button @click="resetFilters">重置筛选</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredRows"
        empty-text="暂无模型"
        height="100%"
        stripe
      >
        <el-table-column prop="modelName" label="模型名称" min-width="210" fixed="left">
          <template #default="{ row }">
            <strong class="table-title">{{ row.modelName }}</strong>
            <span class="table-sub">{{ row.modelCode }} · {{ row.modelVersion || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="modelType" label="类型" min-width="150" />
        <el-table-column prop="provider" label="提供方" min-width="130">
          <template #default="{ row }">{{ row.provider || '-' }}</template>
        </el-table-column>
        <el-table-column prop="modelFamily" label="模型族" min-width="130">
          <template #default="{ row }">{{ row.modelFamily || '-' }}</template>
        </el-table-column>
        <el-table-column label="广场可见" width="100">
          <template #default="{ row }">{{ row.marketplaceVisible === false ? '否' : '是' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="150" fixed="right">
          <template #default="{ row }">
            <el-select
              class="status-select"
              :model-value="modelStatus(row)"
              :loading="statusUpdatingId === row.modelId"
              @change="(status: ManagedStatus) => changeStatus(row, status)"
            >
              <el-option label="已上线" value="ONLINE" />
              <el-option label="测试中" value="TESTING" />
              <el-option label="已下线" value="OFFLINE" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" class="model-dialog" title="新增模型" width="760px" :close-on-click-modal="false">
      <el-form label-position="top" class="model-form">
        <el-form-item label="模型名称" required><el-input v-model="form.modelName" /></el-form-item>
        <el-form-item label="模型编码" required><el-input v-model="form.modelCode" /></el-form-item>
        <el-form-item label="模型类型" required>
          <el-select v-model="form.modelType">
            <el-option label="NUMERIC" value="NUMERIC" />
            <el-option label="IMAGE_TO_NUMERIC" value="IMAGE_TO_NUMERIC" />
            <el-option label="MULTIMODAL" value="MULTIMODAL" />
            <el-option label="FUSION" value="FUSION" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本"><el-input v-model="form.modelVersion" /></el-form-item>
        <el-form-item label="服务模型名称" required><el-input v-model="form.serviceModelName" /></el-form-item>
        <el-form-item label="服务路径"><el-input v-model="form.apiPath" /></el-form-item>
        <el-form-item label="输入窗口（分钟）"><el-input-number v-model="form.inputWindowMinutes" :min="1" /></el-form-item>
        <el-form-item label="输入帧间隔（秒）"><el-input-number v-model="form.inputFrameIntervalSeconds" :min="1" /></el-form-item>
        <el-form-item label="输出步数"><el-input-number v-model="form.outputSteps" :min="1" /></el-form-item>
        <el-form-item label="输出步长（分钟）"><el-input-number v-model="form.outputStepMinutes" :min="1" /></el-form-item>
        <el-form-item label="输入 Schema" class="wide"><el-input v-model="form.inputSchema" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="输出 Schema" class="wide"><el-input v-model="form.outputSchema" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="模型说明" class="wide"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
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
  height: 100%;
  min-height: 0;
  gap: 12px;
  grid-template-rows: auto auto minmax(0, 1fr);
}

.admin-model-page > .page-section {
  display: grid;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
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
  grid-template-columns: 300px 190px 150px;
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

.status-select {
  width: 118px;
}

.table-sub {
  margin-top: 4px;
  color: var(--admin-muted);
  font-size: 12px;
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

:deep(.model-dialog .el-dialog__body) {
  max-height: calc(86vh - 120px);
  overflow-y: auto;
  padding-top: 12px;
  padding-bottom: 8px;
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
