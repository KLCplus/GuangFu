<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'

interface ModuleDefinition {
  component: keyof typeof componentMap
  code: string
  overline: string
  title: string
  description: string
  service: string
  icon: string
}

const route = useRoute()

const componentMap = {
  model: defineAsyncComponent(() => import('./ModelPrediction.vue')),
  cloud: defineAsyncComponent(() => import('./CloudForecast.vue')),
  weather: defineAsyncComponent(() => import('./Weather.vue')),
  marketplace: defineAsyncComponent(() => import('./Marketplace.vue')),
  api: defineAsyncComponent(() => import('./ApiPlatform.vue')),
  reports: defineAsyncComponent(() => import('./AnalysisReport.vue')),
  pvoutput: defineAsyncComponent(() => import('./PublicPvOutput.vue'))
}

const modules: Record<string, ModuleDefinition> = {
  model: {
    component: 'model', code: 'AI', overline: 'MODEL EXECUTION TERMINAL', title: '模型预测控制台',
    description: '选择预测模型与输入数据，执行光伏功率推演并查看结果曲线。', service: '模型推理服务', icon: '/images/model.png'
  },
  cloud: {
    component: 'cloud', code: 'CLD', overline: 'CLOUD IMAGE FORECAST', title: '云图预测控制台',
    description: '上传连续云图帧，追踪云团演变并生成后续时序预测。', service: '云图推演服务', icon: '/images/clouds_prediction.png'
  },
  weather: {
    component: 'weather', code: 'WX', overline: 'WEATHER OBSERVATION', title: '气象监测中心',
    description: '汇总电站实时天气、关键气象指标与短期预报。', service: '气象数据链路', icon: '/images/weather.png'
  },
  marketplace: {
    component: 'marketplace', code: 'MKT', overline: 'MODEL MARKETPLACE', title: '模型能力广场',
    description: '按任务、输入模态与模型架构筛选可用预测能力。', service: '模型目录服务', icon: '/images/models.png'
  },
  'api-overview': {
    component: 'api', code: 'API', overline: 'OPEN API SERVICE', title: 'API 开放平台',
    description: '查看开放能力、接口资源与模型服务接入方式。', service: '开放接口网关', icon: '/images/api.png'
  },
  'api-keys': {
    component: 'api', code: 'KEY', overline: 'ACCESS KEY CONTROL', title: 'API Key 控制台',
    description: '创建、启停和管理 API 访问凭证及安全状态。', service: '凭证管理服务', icon: '/images/keys.png'
  },
  'api-billing': {
    component: 'api', code: 'WAL', overline: 'ACCOUNT BILLING CENTER', title: '余额与流水中心',
    description: '查看账户余额、资金变动与开放服务消费记录。', service: '计费账户服务', icon: '/images/balance.png'
  },
  'api-usage': {
    component: 'api', code: 'USE', overline: 'API USAGE TELEMETRY', title: 'API 使用统计',
    description: '分析调用趋势、模型分布、Key 使用量与调用日志。', service: '调用遥测服务', icon: '/images/use_statistics.png'
  },
  reports: {
    component: 'reports', code: 'AGT', overline: 'ENERGY ANALYSIS AGENT', title: 'Agent 分析工作台',
    description: '通过智能会话编排数据工具、分析过程与报告输出。', service: '智能分析服务', icon: '/images/agent.png'
  },
  pvoutput: {
    component: 'pvoutput', code: 'OP', overline: 'PUBLIC PV OBSERVATORY', title: '公开电站观测台',
    description: '浏览公开光伏系统的实时功率、天气与发电趋势。', service: 'PVOutput 数据链路', icon: '/images/station.png'
  }
}

const moduleKey = computed(() => String(route.meta.visualModule || 'model'))
const moduleDefinition = computed(() => modules[moduleKey.value] ?? modules.model!)
const activeComponent = computed(() => componentMap[moduleDefinition.value.component])
const componentProps = computed(() => {
  if (moduleDefinition.value.component === 'api') {
    return {
      routeBase: '/visualization-ui/api',
      marketplacePath: '/visualization-ui/marketplace',
      visualTheme: true
    }
  }
  return {}
})
</script>

<template>
  <section class="visual-module-page" :class="`module-${moduleDefinition.component}`">
    <header class="module-command-heading">
      <div class="module-command-icon" aria-hidden="true">
        <img :src="moduleDefinition.icon" alt="" />
        <i></i>
      </div>
      <div class="module-command-title">
        <h1>{{ moduleDefinition.title }}</h1>
        <p>{{ moduleDefinition.description }}</p>
      </div>
      <div class="module-heading-axis" aria-hidden="true"><i></i><b></b></div>
    </header>

    <section class="module-command-panel">
      <span class="panel-corner corner-tl" aria-hidden="true"></span>
      <span class="panel-corner corner-br" aria-hidden="true"></span>
      <div class="module-content">
        <component :is="activeComponent" v-bind="componentProps" />
      </div>
    </section>
  </section>
</template>

<style src="../styles/visualization-modules.css"></style>
<style src="../styles/visualization-api.css"></style>
