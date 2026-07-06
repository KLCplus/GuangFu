<script setup lang="ts">
import { ref } from 'vue'
import { createReport } from '@/api/analysis'
const report = ref<Record<string, string> | null>(null)
async function generate() { report.value = (await createReport({ stationId: 1, taskId: 1001, includeWeather: true, includePrediction: true })).data.data }
</script>
<template><h2 class="page-title">综合分析报告</h2><el-button type="primary" @click="generate">生成 mock 报告</el-button><el-card v-if="report" style="margin-top: 16px"><h3>{{ report.stationName }}</h3><p>{{ report.summary }}</p><p>{{ report.weatherAnalysis }}</p><p>{{ report.predictionAnalysis }}</p><el-alert :title="report.suggestion" type="success" :closable="false" /></el-card></template>
