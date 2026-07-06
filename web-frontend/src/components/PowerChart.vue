<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref } from 'vue'

const element = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

onMounted(() => {
  if (!element.value) return
  chart = echarts.init(element.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['10:00', '10:05', '10:10', '10:15', '10:20', '10:25', '10:30'] },
    yAxis: { type: 'value', name: 'kW' },
    series: [{ type: 'line', smooth: true, areaStyle: {}, data: [500, 510, 516, 522, 530, 536, 548] }]
  })
})

onBeforeUnmount(() => chart?.dispose())
</script>

<template><div ref="element" style="height: 320px" /></template>
