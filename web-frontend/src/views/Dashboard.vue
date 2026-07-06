<script setup lang="ts">
import PowerChart from '@/components/PowerChart.vue'
</script>

<template>
  <h2 class="page-title">运行总览</h2>
  <div class="card-grid">
    <el-card><span>电站数量</span><p class="metric">2</p></el-card>
    <el-card><span>当前总功率</span><p class="metric">1,036 kW</p></el-card>
    <el-card><span>在线模型</span><p class="metric">2</p></el-card>
    <el-card><span>今日预测任务</span><p class="metric">12</p></el-card>
  </div>
  <el-card style="margin-top: 16px">
    <template #header>近 30 分钟功率趋势</template>
    <PowerChart />
  </el-card>
</template>
