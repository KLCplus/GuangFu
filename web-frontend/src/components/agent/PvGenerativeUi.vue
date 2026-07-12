<script setup lang="ts">
export interface PvUiInstruction {
  component: string
  props: Record<string, unknown>
}

const props = defineProps<{
  instructions?: PvUiInstruction[]
}>()

const allowedComponents = new Set([
  'StationSummaryCard',
  'WeatherImpactCard',
  'PredictionTrendCard',
  'PowerMetricCard',
  'RiskAssessmentCard',
  'MaintenanceRecommendationCard',
  'ReportPreviewCard',
  'ApprovalActionCard',
  'ToolProgressCard',
  'ErrorRecoveryCard'
])

function firstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  }
  return ''
}

function asStringArray(value: unknown) {
  return Array.isArray(value) ? value.map((item) => firstText(item)).filter(Boolean) : []
}

function safeInstructions() {
  return (props.instructions || []).filter((item) => allowedComponents.has(item.component))
}

function title(instruction: PvUiInstruction) {
  const titles: Record<string, string> = {
    StationSummaryCard: '电站概览',
    WeatherImpactCard: '天气影响',
    PredictionTrendCard: '预测趋势',
    PowerMetricCard: '功率指标',
    RiskAssessmentCard: '风险摘要',
    MaintenanceRecommendationCard: '运维建议',
    ReportPreviewCard: '报告预览',
    ApprovalActionCard: '确认操作',
    ToolProgressCard: '工具进度',
    ErrorRecoveryCard: '错误恢复'
  }
  return titles[instruction.component] || instruction.component
}

function fields(instruction: PvUiInstruction) {
  const source = instruction.props || {}
  const rows: Array<{ label: string; value: string }> = []
  const push = (label: string, ...values: unknown[]) => {
    const value = firstText(...values)
    if (value) rows.push({ label, value })
  }
  if (instruction.component === 'StationSummaryCard') {
    push('电站', source.stationName)
    push('容量', source.capacity !== undefined ? `${source.capacity} MW` : '')
    push('状态', source.status)
    push('位置', source.location)
  } else if (instruction.component === 'WeatherImpactCard') {
    push('天气', source.weather)
    push('温度', source.temperature !== undefined ? `${source.temperature}℃` : '')
    push('湿度', source.humidity !== undefined ? `${source.humidity}%` : '')
    push('风速', source.windSpeed !== undefined ? `${source.windSpeed} m/s` : '')
    push('影响', source.impact)
  } else if (instruction.component === 'PredictionTrendCard') {
    push('摘要', source.summary)
    asStringArray(source.highlights).forEach((item, index) => rows.push({ label: index === 0 ? '要点' : '', value: item }))
  } else if (instruction.component === 'ApprovalActionCard') {
    push('工具', source.toolName)
    push('原因', source.reason)
    push('确认ID', source.approvalId)
  } else if (instruction.component === 'ErrorRecoveryCard') {
    push('工具', source.toolName)
    push('错误', source.error)
  } else {
    Object.entries(source).forEach(([key, value]) => push(key, value))
  }
  return rows
}
</script>

<template>
  <div v-if="safeInstructions().length" class="pv-generative-ui">
    <section
      v-for="(instruction, index) in safeInstructions()"
      :key="`${instruction.component}-${index}`"
      class="pv-ui-card"
      :class="instruction.component"
    >
      <header>
        <strong>{{ title(instruction) }}</strong>
        <span>{{ instruction.component }}</span>
      </header>
      <dl>
        <div v-for="field in fields(instruction)" :key="`${field.label}-${field.value}`">
          <dt>{{ field.label }}</dt>
          <dd>{{ field.value }}</dd>
        </div>
      </dl>
    </section>
  </div>
</template>

<style scoped>
.pv-generative-ui {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
  max-width: 760px;
}

.pv-ui-card {
  border: 1px solid rgba(47, 117, 230, 0.18);
  border-radius: 8px;
  padding: 12px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(70, 96, 140, 0.07);
}

.pv-ui-card header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.pv-ui-card header strong {
  color: #1d2f4f;
  font-size: 14px;
}

.pv-ui-card header span {
  color: #8090a8;
  font-size: 11px;
}

.pv-ui-card dl {
  display: grid;
  gap: 6px;
  margin: 0;
}

.pv-ui-card dl div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
}

.pv-ui-card dt {
  color: #7a879a;
  font-size: 12px;
}

.pv-ui-card dd {
  margin: 0;
  color: #2b3950;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.pv-ui-card.WeatherImpactCard {
  border-color: rgba(22, 163, 74, 0.22);
}

.pv-ui-card.PredictionTrendCard {
  border-color: rgba(126, 87, 194, 0.22);
}

.pv-ui-card.ApprovalActionCard {
  border-color: rgba(217, 119, 6, 0.28);
}

.pv-ui-card.ErrorRecoveryCard {
  border-color: rgba(180, 35, 24, 0.26);
}
</style>
