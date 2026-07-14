<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Cpu, Document, House, Key, Sunny, Tools } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const currentTime = ref('')
const currentDate = ref('')
let clockTimer: number | undefined

const navGroups = [
  {
    label: '平台资源',
    code: 'PLATFORM ASSETS',
    items: [
      { path: '/visualization-admin/users-apis', label: '用户与 API', code: 'IAM', icon: Key },
      { path: '/visualization-admin/pvoutput', label: '数据源管理', code: 'SRC', icon: Sunny },
      { path: '/visualization-admin/models', label: '模型管理', code: 'MDL', icon: Cpu }
    ]
  },
  {
    label: '内容运营',
    code: 'CONTENT OPS',
    items: [
      { path: '/visualization-admin/news', label: '新闻管理', code: 'NEWS', icon: Bell },
      { path: '/visualization-admin/announcements', label: '公告管理', code: 'NTF', icon: Document }
    ]
  }
]

const displayName = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || '系统管理员')
const pageTitle = computed(() => String(route.meta.title || '平台管理控制台'))

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
  <div class="visual-app-shell visual-admin-shell">
    <div class="visual-shell-atmosphere" aria-hidden="true">
      <i class="visual-shell-orbit orbit-a"></i>
      <i class="visual-shell-orbit orbit-b"></i>
      <i class="visual-shell-scan"></i>
    </div>

    <aside class="visual-sidebar">
      <router-link class="visual-brand" to="/visualization-admin/users-apis" aria-label="进入平台管理控制台">
        <span class="visual-brand-core" aria-hidden="true">
          <el-icon><Tools /></el-icon>
          <i></i>
        </span>
        <span class="visual-brand-copy">
          <small>PHOTOVOLTAIC ADMIN OS</small>
          <strong>光伏管控</strong>
          <em>GUANGFU ADMIN</em>
        </span>
      </router-link>

      <div class="visual-system-state">
        <span><i></i> ADMIN CHANNEL</span>
        <b>GF / A1</b>
      </div>
      <div class="visual-console-scope">
        <span>平台管理端</span>
        <small>ADMIN CONSOLE</small>
      </div>

      <nav class="visual-nav" aria-label="可视化管理端导航">
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
        <button type="button" class="visual-user-entry" @click="router.push('/visualization-ui/cockpit')">
          <span>返回用户运行端</span>
          <small>USER CONSOLE ↗</small>
        </button>
        <div class="visual-channel-state">
          <span><i></i>管理权限通道</span>
          <strong>已授权</strong>
        </div>
        <div class="visual-user">
          <el-avatar :size="34" :src="userStore.userInfo.avatarUrl">{{ displayName.charAt(0) }}</el-avatar>
          <span><strong>{{ displayName }}</strong><small>系统管理员</small></span>
          <button type="button" @click="logout">退出</button>
        </div>
      </footer>
    </aside>

    <section class="visual-main">
      <header class="visual-topbar">
        <div class="visual-breadcrumb">
          <el-icon><House /></el-icon>
          <span>光伏平台管理中枢</span>
          <i>/</i>
          <strong>{{ pageTitle }}</strong>
        </div>
        <div class="visual-topbar-axis" aria-hidden="true"><i></i></div>
        <div class="visual-clock">
          <span><i></i>权限链路已验证</span>
          <div><strong>{{ currentTime }}</strong><small>{{ currentDate }}</small></div>
        </div>
      </header>

      <main class="visual-content">
        <router-view />
      </main>

      <footer class="visual-shell-footer">
        <span>GUANGFU PLATFORM ADMINISTRATION</span>
        <span><i></i> AUTHORITY STATUS · VERIFIED</span>
        <span>CONTROL CHANNEL · SECURE</span>
      </footer>
    </section>
  </div>
</template>

<style src="../styles/visualization-layout.css"></style>
<style src="../styles/visualization-theme.css"></style>
<style src="../styles/visualization-admin.css"></style>
