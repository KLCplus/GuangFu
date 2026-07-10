<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

interface CloudFrame {
  id: string
  name: string
  url: string
  source: 'upload' | 'demo' | 'mock'
}

interface PredictionFrame extends CloudFrame {
  timeOffset: number
  confidence: number
  cloudCoverage: number
}

const requiredFrameCount = 10
const fileInput = ref<HTMLInputElement>()
const uploadedFrames = ref<CloudFrame[]>([])
const predictionFrames = ref<PredictionFrame[]>([])
const predicting = ref(false)
const progress = ref(0)
const progressLabel = ref('等待上传')
const draggingFrameIndex = ref<number | null>(null)
const dragOverFrameIndex = ref<number | null>(null)

const canPredict = computed(() => uploadedFrames.value.length === requiredFrameCount && !predicting.value)

function openFilePicker() {
  fileInput.value?.click()
}

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  if (!files.length) return

  const imageFiles = files.filter((file) => file.type.startsWith('image/'))
  const available = requiredFrameCount - uploadedFrames.value.length
  const selected = imageFiles.slice(0, available)

  if (selected.length < files.length) {
    ElMessage.warning(`最多保留 ${requiredFrameCount} 张图片，已自动忽略超出部分`)
  }

  uploadedFrames.value.push(
    ...selected.map((file, index) => ({
      id: `${Date.now()}-${index}-${file.name}`,
      name: file.name,
      url: URL.createObjectURL(file),
      source: 'upload' as const
    }))
  )
  predictionFrames.value = []
  target.value = ''
}

function fillDemoFrames() {
  revokeUploadedObjectUrls()
  uploadedFrames.value = Array.from({ length: requiredFrameCount }, (_, index) => ({
    id: `demo-${index + 1}`,
    name: `history-cloud-${String(index + 1).padStart(2, '0')}.png`,
    url: createCloudSvg(index, 'history'),
    source: 'demo'
  }))
  predictionFrames.value = []
  progress.value = 0
  progressLabel.value = '演示云图已准备'
}

function removeFrame(index: number) {
  const frame = uploadedFrames.value[index]
  if (frame?.source === 'upload') URL.revokeObjectURL(frame.url)
  uploadedFrames.value.splice(index, 1)
  predictionFrames.value = []
}

function moveFrame(index: number, direction: -1 | 1) {
  const nextIndex = index + direction
  if (nextIndex < 0 || nextIndex >= uploadedFrames.value.length) return
  const frames = [...uploadedFrames.value]
  const current = frames[index]
  frames[index] = frames[nextIndex]
  frames[nextIndex] = current
  uploadedFrames.value = frames
  predictionFrames.value = []
}

function handleFrameDragStart(event: DragEvent, index: number) {
  if (!uploadedFrames.value[index]) return
  draggingFrameIndex.value = index
  event.dataTransfer?.setData('text/plain', String(index))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function handleFrameDragOver(event: DragEvent, index: number) {
  if (draggingFrameIndex.value === null || !uploadedFrames.value[index]) return
  event.preventDefault()
  dragOverFrameIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function handleFrameDrop(event: DragEvent, index: number) {
  event.preventDefault()
  const dataIndex = Number(event.dataTransfer?.getData('text/plain'))
  const fromIndex = draggingFrameIndex.value ?? dataIndex
  if (!Number.isInteger(fromIndex) || fromIndex === index || !uploadedFrames.value[fromIndex] || !uploadedFrames.value[index]) {
    clearFrameDragState()
    return
  }

  const frames = [...uploadedFrames.value]
  const [draggedFrame] = frames.splice(fromIndex, 1)
  frames.splice(index, 0, draggedFrame)
  uploadedFrames.value = frames
  predictionFrames.value = []
  clearFrameDragState()
}

function clearFrameDragState() {
  draggingFrameIndex.value = null
  dragOverFrameIndex.value = null
}

function clearFrames() {
  revokeUploadedObjectUrls()
  uploadedFrames.value = []
  predictionFrames.value = []
  progress.value = 0
  progressLabel.value = '等待上传'
}

async function runPrediction() {
  if (uploadedFrames.value.length !== requiredFrameCount) {
    ElMessage.warning('请先上传或填充 10 张历史云图')
    return
  }

  predicting.value = true
  predictionFrames.value = []
  progress.value = 8
  progressLabel.value = '校验云图序列'

  const stages = [
    { value: 28, label: '提取云层纹理特征' },
    { value: 52, label: '执行时空递归预测' },
    { value: 78, label: '生成未来云图帧' },
    { value: 100, label: '预测完成' }
  ]

  for (const stage of stages) {
    await wait(260)
    progress.value = stage.value
    progressLabel.value = stage.label
  }

  predictionFrames.value = Array.from({ length: requiredFrameCount }, (_, index) => {
    const cloudCoverage = 38 + Math.round(Math.sin(index / 1.8) * 12 + index * 1.8)
    return {
      id: `forecast-${index + 1}`,
      name: `forecast-cloud-${String(index + 1).padStart(2, '0')}.png`,
      url: createCloudSvg(index, 'forecast'),
      source: 'mock',
      timeOffset: (index + 1) * 5,
      confidence: Math.max(84, 96 - index * 1.2),
      cloudCoverage: Math.max(18, Math.min(82, cloudCoverage))
    }
  })
  predicting.value = false
  ElMessage.success('已生成未来 10 张云图预测结果')
}

function downloadFrame(frame: CloudFrame) {
  const link = document.createElement('a')
  link.href = frame.url
  link.download = frame.name
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function downloadAll() {
  if (!predictionFrames.value.length) {
    ElMessage.warning('暂无可下载的预测结果')
    return
  }
  predictionFrames.value.forEach((frame, index) => {
    window.setTimeout(() => downloadFrame(frame), index * 80)
  })
  ElMessage.success('已开始批量下载预测云图')
}

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function revokeUploadedObjectUrls() {
  uploadedFrames.value.forEach((frame) => {
    if (frame.source === 'upload') URL.revokeObjectURL(frame.url)
  })
}

function createCloudSvg(index: number, type: 'history' | 'forecast') {
  const width = 360
  const height = 240
  const hue = type === 'history' ? 206 : 194
  const offset = type === 'history' ? index * 12 : index * 18 + 24
  const cloudA = 42 + ((index * 9) % 35)
  const cloudB = 66 - ((index * 7) % 28)
  const label = type === 'history' ? `H-${String(index + 1).padStart(2, '0')}` : `T+${(index + 1) * 5}m`
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
      <defs>
        <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="hsl(${hue}, 78%, 62%)"/>
          <stop offset="58%" stop-color="hsl(${hue + 16}, 72%, 82%)"/>
          <stop offset="100%" stop-color="hsl(${hue + 38}, 84%, 92%)"/>
        </linearGradient>
        <filter id="soft">
          <feGaussianBlur stdDeviation="4"/>
        </filter>
      </defs>
      <rect width="${width}" height="${height}" fill="url(#sky)"/>
      <circle cx="${280 - index * 4}" cy="${54 + index}" r="28" fill="#ffe28a" opacity="0.86"/>
      <g fill="#ffffff" opacity="0.9" filter="url(#soft)">
        <ellipse cx="${68 + offset}" cy="${cloudA}" rx="58" ry="22"/>
        <ellipse cx="${118 + offset}" cy="${cloudA + 8}" rx="76" ry="28"/>
        <ellipse cx="${208 - offset / 2}" cy="${cloudB + 28}" rx="84" ry="30"/>
        <ellipse cx="${282 - offset / 3}" cy="${cloudB + 12}" rx="54" ry="22"/>
        <ellipse cx="${80 + index * 10}" cy="${168 - index * 3}" rx="92" ry="26" opacity="0.72"/>
      </g>
      <path d="M0 190 C70 170 110 188 164 172 C234 151 280 175 360 154 V240 H0 Z" fill="#356e8a" opacity="0.18"/>
      <rect x="18" y="18" width="86" height="34" rx="8" fill="rgba(16,39,76,0.68)"/>
      <text x="61" y="40" fill="#fff" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" font-weight="700">${label}</text>
    </svg>
  `
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}
</script>

<template>
  <section class="page-shell cloud-page">
    <div class="cloud-layout">
      <section class="page-section upload-panel">
        <div class="panel-head">
          <div>
            <h3>历史云图上传墙</h3>
          </div>
          <el-tag :type="uploadedFrames.length === requiredFrameCount ? 'success' : 'warning'">
            {{ uploadedFrames.length }}/{{ requiredFrameCount }}
          </el-tag>
        </div>

        <input
          ref="fileInput"
          class="file-input"
          type="file"
          accept="image/*"
          multiple
          @change="handleFileChange"
        />

        <div class="upload-actions">
          <el-button type="primary" :disabled="uploadedFrames.length >= requiredFrameCount" @click="openFilePicker">
            选择云图
          </el-button>
          <el-button @click="fillDemoFrames">填充演示样例</el-button>
          <el-button :disabled="!uploadedFrames.length" @click="clearFrames">清空</el-button>
        </div>

        <div class="frame-grid input-grid">
          <div
            v-for="slot in requiredFrameCount"
            :key="slot"
            class="frame-card"
            :class="{
              empty: !uploadedFrames[slot - 1],
              dragging: draggingFrameIndex === slot - 1,
              'drag-over': dragOverFrameIndex === slot - 1 && draggingFrameIndex !== slot - 1
            }"
            :draggable="Boolean(uploadedFrames[slot - 1])"
            @dragstart="handleFrameDragStart($event, slot - 1)"
            @dragover="handleFrameDragOver($event, slot - 1)"
            @drop="handleFrameDrop($event, slot - 1)"
            @dragend="clearFrameDragState"
            @dragleave="dragOverFrameIndex = null"
          >
            <template v-if="uploadedFrames[slot - 1]">
              <img :src="uploadedFrames[slot - 1].url" :alt="uploadedFrames[slot - 1].name" draggable="false" />
              <div class="frame-meta">
                <strong>第 {{ slot }} 帧</strong>
                <span>{{ uploadedFrames[slot - 1].name }}</span>
              </div>
              <div class="frame-actions">
                <el-button size="small" text :disabled="slot === 1" @click="moveFrame(slot - 1, -1)">前移</el-button>
                <el-button
                  size="small"
                  text
                  :disabled="slot === uploadedFrames.length"
                  @click="moveFrame(slot - 1, 1)"
                >
                  后移
                </el-button>
                <el-button size="small" text type="danger" @click="removeFrame(slot - 1)">删除</el-button>
              </div>
            </template>
            <template v-else>
              <span class="slot-index">{{ slot }}</span>
              <p>等待云图</p>
            </template>
          </div>
        </div>

        <div class="predict-bar">
          <div>
            <strong>{{ progressLabel }}</strong>
            <el-progress :percentage="progress" :stroke-width="10" />
          </div>
          <el-button type="primary" size="large" :loading="predicting" :disabled="!canPredict" @click="runPrediction">
            开始预测
          </el-button>
        </div>
      </section>

      <section class="page-section output-panel">
        <div class="panel-head">
          <div>
            <h3>未来 10 张预测云图</h3>
          </div>
          <div class="output-actions">
            <el-button :disabled="!predictionFrames.length" @click="downloadAll">批量下载</el-button>
            <el-button :disabled="!uploadedFrames.length" @click="runPrediction">重新预测</el-button>
          </div>
        </div>

        <el-empty v-if="!predictionFrames.length" description="完成 10 张云图上传后生成预测结果" />
        <div v-else class="frame-grid output-grid">
          <div v-for="frame in predictionFrames" :key="frame.id" class="frame-card forecast-card">
            <img :src="frame.url" :alt="frame.name" />
            <div class="forecast-info">
              <strong>T+{{ frame.timeOffset }} min</strong>
              <span>云量 {{ frame.cloudCoverage }}%</span>
              <span>置信度 {{ frame.confidence.toFixed(1) }}%</span>
            </div>
            <el-button size="small" text type="primary" @click="downloadFrame(frame)">下载</el-button>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.cloud-page {
  gap: 20px;
}

.panel-head,
.predict-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.panel-head h3 {
  margin: 0;
  color: #10274c;
  font-size: 18px;
  line-height: 1.35;
}

.upload-panel,
.output-panel {
  position: relative;
  overflow: hidden;
}

.upload-panel::before,
.output-panel::before {
  position: absolute;
  inset: 0 0 auto;
  height: 4px;
  content: '';
}

.upload-panel::before {
  background: linear-gradient(90deg, #1d6fdc, #26a8c8, #f5b642);
}

.output-panel::before {
  background: linear-gradient(90deg, #26a8c8, #7ec8a5, #1d6fdc);
}

.panel-head {
  margin-bottom: 16px;
}

.cloud-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.file-input {
  display: none;
}

.upload-actions,
.output-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.upload-actions {
  margin-bottom: 18px;
}

.frame-grid {
  display: grid;
  gap: 14px;
}

.input-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.output-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.frame-card {
  position: relative;
  min-height: 188px;
  overflow: hidden;
  border: 1px solid #d7e7f4;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 8px 22px rgba(19, 70, 116, 0.06);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.frame-card:hover {
  border-color: #9fc7ea;
  box-shadow: 0 12px 28px rgba(19, 70, 116, 0.1);
  transform: translateY(-1px);
}

.frame-card[draggable='true'] {
  cursor: grab;
}

.frame-card[draggable='true']:active {
  cursor: grabbing;
}

.frame-card.dragging {
  opacity: 0.46;
  transform: scale(0.98);
}

.frame-card.drag-over {
  border-color: #1d6fdc;
  box-shadow: 0 0 0 3px rgba(29, 111, 220, 0.14);
}

.frame-card.empty {
  display: grid;
  place-items: center;
  border-style: dashed;
  background:
    linear-gradient(135deg, rgba(29, 111, 220, 0.08), rgba(38, 168, 200, 0.06)),
    #f8fbff;
  color: var(--color-muted);
  box-shadow: none;
}

.slot-index {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #e8f2ff;
  color: var(--color-primary);
  font-weight: 700;
}

.frame-card.empty p {
  margin: 8px 0 0;
}

.frame-card img {
  display: block;
  width: 100%;
  aspect-ratio: 3 / 2;
  object-fit: cover;
}

.frame-meta,
.forecast-info {
  display: grid;
  gap: 4px;
  padding: 11px 12px;
}

.frame-meta strong,
.forecast-info strong {
  color: #10274c;
}

.frame-meta span,
.forecast-info span {
  overflow: hidden;
  color: var(--color-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.frame-actions {
  display: flex;
  justify-content: space-between;
  gap: 2px;
  padding: 0 6px 8px;
}

.predict-bar {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #d7e7f4;
  border-radius: 8px;
  background:
    linear-gradient(90deg, rgba(29, 111, 220, 0.08), rgba(38, 168, 200, 0.08)),
    #fbfdff;
}

.predict-bar > div {
  min-width: 0;
  flex: 1;
}

.predict-bar strong {
  display: block;
  margin-bottom: 8px;
  color: #10274c;
}

.forecast-card {
  min-height: 0;
}

.forecast-card .el-button {
  margin: 0 10px 10px;
}

@media (max-width: 1180px) {
  .input-grid,
  .output-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .panel-head,
  .predict-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .input-grid,
  .output-grid {
    grid-template-columns: 1fr;
  }

  .output-actions {
    width: 100%;
  }
}
</style>
