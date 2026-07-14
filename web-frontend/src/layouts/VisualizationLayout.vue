<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  Cloudy,
  Cpu,
  DataAnalysis,
  Document,
  Grid,
  House,
  Sunny,
  User
} from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import ThemeToggle from '../components/ThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const currentTime = ref('')
const currentDate = ref('')
let clockTimer: number | undefined

const navGroups = [
  {
    label: '运行监控',
    code: 'OPERATIONS',
    items: [
      { path: '/visualization-ui/cockpit', label: '能源驾驶舱', code: 'PV', icon: DataAnalysis },
      { path: '/visualization-ui/pvoutput', label: '公开电站', code: 'OP', icon: Sunny },
      { path: '/visualization-ui/weather', label: '气象监测', code: 'WX', icon: Cloudy }
    ]
  },
  {
    label: '智能预测',
    code: 'FORECAST',
    items: [
      { path: '/visualization-ui/models/use', label: '模型应用', code: 'AI', icon: Cpu },
      { path: '/visualization-ui/cloud-forecast', label: '云图预测', code: 'CLD', icon: Cloudy },
      { path: '/visualization-ui/marketplace', label: '模型广场', code: 'MKT', icon: Grid }
    ]
  },
  {
    label: '开放服务',
    code: 'OPEN SERVICE',
    items: [
      { path: '/visualization-ui/api/overview', label: 'API 概览', code: 'API', icon: Document },
      { path: '/visualization-ui/api/keys', label: 'API Keys', code: 'KEY', icon: Document },
      { path: '/visualization-ui/api/billing', label: '余额与流水', code: 'WAL', icon: Document },
      { path: '/visualization-ui/api/usage', label: '使用统计', code: 'USE', icon: DataAnalysis }
    ]
  },
  {
    label: '数据与协作',
    code: 'DATA CENTER',
    items: [
      { path: '/visualization-ui/reports', label: 'Agent 分析', code: 'AGT', icon: DataAnalysis },
      { path: '/visualization-ui/news', label: '新闻通知', code: 'NEWS', icon: Bell },
      { path: '/visualization-ui/profile', label: '个人中心', code: 'ID', icon: User }
    ]
  }
]

const displayName = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || '运行用户')
const pageTitle = computed(() => String(route.meta.title || '光伏能源运行平台'))
const isCockpit = computed(() => route.name === 'VisualizationCockpit')

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function updateClock() {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
  currentDate.value = now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short'
  })
}

function logout() {
  userStore.logout()
  void router.replace('/login')
}

onMounted(() => {
  updateClock()
  clockTimer = window.setInterval(updateClock, 1000)
})

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
})
</script>

<template>
  <div class="visual-app-shell">
    <div class="visual-shell-atmosphere" aria-hidden="true">
      <i class="visual-shell-orbit orbit-a"></i>
      <i class="visual-shell-orbit orbit-b"></i>
      <i class="visual-shell-scan"></i>
    </div>

    <aside class="visual-sidebar">
      <router-link class="visual-brand" to="/visualization-ui/cockpit" aria-label="进入能源驾驶舱">
        <span class="visual-brand-core" aria-hidden="true">
          <el-icon><Sunny /></el-icon>
          <i></i>
        </span>
        <span class="visual-brand-copy">
          <small>PHOTOVOLTAIC ENERGY OS</small>
          <strong>光伏智云</strong>
          <em>GUANGFU CONTROL</em>
        </span>
      </router-link>

      <div class="visual-system-state">
        <span><i></i> SYSTEM ONLINE</span>
        <b>GF / 01</b>
      </div>
      <div class="visual-console-scope">
        <span>用户运行端</span>
        <small>USER CONSOLE</small>
      </div>

      <nav class="visual-nav" aria-label="可视化模式导航">
        <section v-for="group in navGroups" :key="group.code" class="visual-nav-group">
          <p><span>{{ group.label }}</span><small>{{ group.code }}</small></p>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="visual-nav-item"
            :class="{ active: isActive(item.path) }"
          >
            <span class="nav-energy-node"><i></i></span>
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <small>{{ item.code }}</small>
          </router-link>
        </section>
      </nav>

      <footer class="visual-sidebar-footer">
        <ThemeToggle />
        <button
          v-if="userStore.hasRole('ADMIN')"
          type="button"
          class="visual-admin-entry"
          @click="router.push('/visualization-admin/users-apis')"
        >
          <span>进入管理端</span>
          <small>ADMIN CONSOLE ↗</small>
        </button>
        <div class="visual-channel-state">
          <span><i></i>能源数据通道</span>
          <strong>实时</strong>
        </div>
        <div class="visual-user">
          <el-avatar :size="34" :src="userStore.userInfo.avatarUrl">{{ displayName.charAt(0) }}</el-avatar>
          <span><strong>{{ displayName }}</strong><small>{{ userStore.hasRole('ADMIN') ? '系统管理员' : '运行用户' }}</small></span>
          <button type="button" @click="logout">退出</button>
        </div>
      </footer>
    </aside>

    <section class="visual-main" :class="{ 'is-cockpit': isCockpit }">
      <header v-if="!isCockpit" class="visual-topbar">
        <div class="visual-breadcrumb">
          <el-icon><House /></el-icon>
          <span>光伏能源运行平台</span>
          <i>/</i>
          <strong>{{ pageTitle }}</strong>
        </div>
        <div class="visual-topbar-axis" aria-hidden="true"><i></i></div>
        <div class="visual-clock">
          <span><i></i>数据链路正常</span>
          <div><strong>{{ currentTime }}</strong><small>{{ currentDate }}</small></div>
        </div>
        <ThemeToggle />
      </header>

      <main class="visual-content">
        <router-view />
      </main>

      <footer v-if="!isCockpit" class="visual-shell-footer">
        <span>GUANGFU DIGITAL ENERGY PLATFORM</span>
        <span><i></i> SERVICE STATUS · NORMAL</span>
        <span>DATA UPDATE · REAL-TIME</span>
      </footer>
    </section>
  </div>
</template>

<style src="../styles/visualization-layout.css"></style>
<style src="../styles/visualization-theme.css"></style>
