<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getPredictionHistory } from '@/api/prediction'
const rows = ref([])
onMounted(async () => { rows.value = (await getPredictionHistory()).data.data })
</script>
<template><h2 class="page-title">预测历史</h2><el-card><el-table :data="rows"><el-table-column prop="taskId" label="任务 ID" /><el-table-column prop="modelName" label="模型" /><el-table-column prop="taskStatus" label="状态" /><el-table-column prop="costTime" label="耗时(ms)" /></el-table></el-card></template>
