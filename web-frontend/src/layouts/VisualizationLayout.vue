<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftBold, ArrowRightBold, House, Management } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import ThemeToggle from '../components/ThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const currentTime = ref('')
const currentDate = ref('')
const sidebarCollapsed = ref(false)
let clockTimer: number | undefined

const navGroups = [
  {
    label: '运行监控',
    code: 'OPERATIONS',
    items: [
      { path: '/visualization-ui/cockpit', label: '智控中心', code: 'PV', icon: '/images/overview.png' },
      { path: '/visualization-ui/pvoutput', label: '公开电站', code: 'OP', icon: '/images/station.png' },
      { path: '/visualization-ui/weather', label: '气象监测', code: 'WX', icon: '/images/weather.png' }
    ]
  },
  {
    label: '智能预测',
    code: 'FORECAST',
    items: [
      { path: '/visualization-ui/models/use', label: '模型应用', code: 'AI', icon: '/images/model.png' },
      { path: '/visualization-ui/cloud-forecast', label: '云图预测', code: 'CLD', icon: '/images/clouds_prediction.png' },
      { path: '/visualization-ui/marketplace', label: '模型广场', code: 'MKT', icon: '/images/models.png' }
    ]
  },
  {
    label: '开放服务',
    code: 'OPEN SERVICE',
    items: [
      { path: '/visualization-ui/api/overview', label: 'API 概览', code: 'API', icon: '/images/api.png' },
      { path: '/visualization-ui/api/keys', label: 'API Keys', code: 'KEY', icon: '/images/keys.png' },
      { path: '/visualization-ui/api/billing', label: '余额与流水', code: 'WAL', icon: '/images/balance.png' },
      { path: '/visualization-ui/api/usage', label: '使用统计', code: 'USE', icon: '/images/use_statistics.png' }
    ]
  },
  {
    label: '数据与协作',
    code: 'DATA CENTER',
    items: [
      { path: '/visualization-ui/reports', label: 'Agent 分析', code: 'AGT', icon: '/images/agent.png' },
      { path: '/visualization-ui/news', label: '新闻通知', code: 'NEWS', icon: '/images/news.png' },
      { path: '/visualization-ui/profile', label: '个人中心', code: 'ID', icon: '/images/my.png' }
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
  <div class="visual-app-shell" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <div class="visual-shell-atmosphere" aria-hidden="true">
      <i class="visual-shell-orbit orbit-a"></i>
      <i class="visual-shell-orbit orbit-b"></i>
      <i class="visual-shell-scan"></i>
    </div>

    <aside class="visual-sidebar">
      <router-link class="visual-brand" to="/visualization-ui/cockpit" aria-label="进入光伏智控中心">
        <span class="visual-brand-core" aria-hidden="true">
          <img src="/images/logo.png" alt="" />
          <i></i>
        </span>
        <span class="visual-brand-copy">
          <strong>光伏智云</strong>
        </span>
      </router-link>
      <button
        type="button"
        class="visual-sidebar-toggle"
        :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
        :title="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        <el-icon><component :is="sidebarCollapsed ? ArrowRightBold : ArrowLeftBold" /></el-icon>
      </button>

      <div class="visual-console-scope">
        <span>用户运行端</span>
      </div>

      <nav class="visual-nav" aria-label="可视化模式导航">
        <section v-for="group in navGroups" :key="group.code" class="visual-nav-group">
          <p><span>{{ group.label }}</span></p>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="visual-nav-item"
            :class="{ active: isActive(item.path) }"
          >
            <span class="nav-energy-node"><i></i></span>
            <img class="visual-nav-icon" :src="item.icon" alt="" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </router-link>
        </section>
      </nav>

      <footer class="visual-sidebar-footer">
        <ThemeToggle />
        <button
          v-if="userStore.hasRole('ADMIN')"
          type="button"
          class="visual-admin-entry"
          title="进入管理端"
          aria-label="进入管理端"
          @click="router.push('/visualization-admin/users-apis')"
        >
          <el-icon aria-hidden="true"><Management /></el-icon>
          <span>进入管理端</span>
        </button>
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
      </header>

      <main class="visual-content">
        <router-view />
      </main>

    </section>
  </div>
</template>

<style src="../styles/visualization-layout.css"></style>
<style src="../styles/visualization-theme.css"></style>
