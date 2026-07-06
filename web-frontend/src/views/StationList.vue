<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getStations } from '@/api/station'

const rows = ref<Record<string, unknown>[]>([])
const router = useRouter()
onMounted(async () => {
  const response = await getStations()
  rows.value = response.data.data.records
})
</script>

<template>
  <h2 class="page-title">光伏电站</h2>
  <el-card>
    <el-table :data="rows">
      <el-table-column prop="stationId" label="ID" width="80" />
      <el-table-column prop="stationName" label="名称" />
      <el-table-column prop="city" label="城市" />
      <el-table-column prop="capacity" label="装机容量(kW)" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作">
        <template #default="{ row }"><el-button link type="primary" @click="router.push(`/stations/${row.stationId}`)">详情</el-button></template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
