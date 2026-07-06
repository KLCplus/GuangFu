<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getModels } from '@/api/model'
import { createPrediction } from '@/api/prediction'

const models = ref<Record<string, unknown>[]>([])
const result = ref<Record<string, unknown> | null>(null)
const form = reactive({ stationId: 1, modelId: 1, inputMode: 'STATION_HISTORY' })
onMounted(async () => { models.value = (await getModels()).data.data })

async function predict() {
  try {
    result.value = (await createPrediction(form)).data.data
  } catch {
    ElMessage.error('预测失败，请确认后端和模型服务均已启动')
  }
}
</script>

<template>
  <h2 class="page-title">模型预测</h2>
  <el-card>
    <el-form inline>
      <el-form-item label="电站"><el-input-number v-model="form.stationId" :min="1" /></el-form-item>
      <el-form-item label="模型"><el-select v-model="form.modelId" style="width: 240px"><el-option v-for="item in models" :key="String(item.modelId)" :label="String(item.modelName)" :value="Number(item.modelId)" /></el-select></el-form-item>
      <el-button type="primary" @click="predict">开始预测</el-button>
    </el-form>
  </el-card>
  <el-card v-if="result" style="margin-top: 16px"><template #header>预测结果（{{ result.taskStatus }}）</template><el-table :data="result.predictions"><el-table-column prop="timeOffset" label="时间偏移(min)" /><el-table-column prop="predictPower" label="预测功率(kW)" /></el-table></el-card>
</template>
