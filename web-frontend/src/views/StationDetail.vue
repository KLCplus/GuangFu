<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getRealtime, getStation } from '@/api/station'
import PowerChart from '@/components/PowerChart.vue'

const route = useRoute()
const station = ref<Record<string, unknown>>({})
const realtime = ref<Record<string, unknown>>({})
onMounted(async () => {
  const id = Number(route.params.id)
  station.value = (await getStation(id)).data.data
  realtime.value = (await getRealtime(id)).data.data
})
</script>

<template>
  <h2 class="page-title">电站详情</h2>
  <div class="card-grid">
    <el-card><template #header>基础信息</template><el-descriptions :column="1"><el-descriptions-item label="名称">{{ station.stationName }}</el-descriptions-item><el-descriptions-item label="地址">{{ station.address }}</el-descriptions-item><el-descriptions-item label="容量">{{ station.capacity }} kW</el-descriptions-item></el-descriptions></el-card>
    <el-card><template #header>实时数据</template><el-descriptions :column="1"><el-descriptions-item label="功率">{{ realtime.power }} kW</el-descriptions-item><el-descriptions-item label="辐照度">{{ realtime.irradiance }}</el-descriptions-item><el-descriptions-item label="温度">{{ realtime.temperature }} ℃</el-descriptions-item></el-descriptions></el-card>
  </div>
  <el-card style="margin-top: 16px"><template #header>功率曲线</template><PowerChart /></el-card>
</template>
