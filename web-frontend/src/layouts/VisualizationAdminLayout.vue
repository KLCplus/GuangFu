<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftBold, ArrowRightBold, House, User } from '@element-plus/icons-vue'
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
    label: '平台资源',
    code: 'PLATFORM ASSETS',
    items: [
      { path: '/visualization-admin/users-apis', label: '用户与 API', code: 'IAM', icon: '/images/keys.png' },
      { path: '/visualization-admin/pvoutput', label: '数据源管理', code: 'SRC', icon: '/images/pvout_source.png' },
      { path: '/visualization-admin/models', label: '模型管理', code: 'MDL', icon: '/images/models.png' }
    ]
  },
  {
    label: '内容运营',
    code: 'CONTENT OPS',
    items: [
      { path: '/visualization-admin/news', label: '资讯管理', code: 'NEWS', icon: '/images/news.png' },
      { path: '/visualization-admin/announcements', label: '消息管理', code: 'NTF', icon: '/images/announcement.png' }
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
  <div class="visual-app-shell visual-admin-shell" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <div class="visual-shell-atmosphere" aria-hidden="true">
      <i class="visual-shell-orbit orbit-a"></i>
      <i class="visual-shell-orbit orbit-b"></i>
      <i class="visual-shell-scan"></i>
    </div>

    <aside class="visual-sidebar">
      <router-link class="visual-brand" to="/visualization-admin/users-apis" aria-label="进入平台管理控制台">
        <span class="visual-brand-core" aria-hidden="true">
          <img src="/images/logo.png" alt="" />
          <i></i>
        </span>
        <span class="visual-brand-copy">
          <strong>光伏管控</strong>
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
        <span>平台管理端</span>
      </div>

      <nav class="visual-nav" aria-label="可视化管理端导航">
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
        <button type="button" class="visual-user-entry" title="返回用户端" aria-label="返回用户端" @click="router.push('/visualization-ui/cockpit')">
          <el-icon aria-hidden="true"><User /></el-icon>
          <span>返回用户运行端</span>
        </button>
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

    </section>
  </div>
</template>

<style src="../styles/visualization-layout.css"></style>
<style src="../styles/visualization-theme.css"></style>
<style src="../styles/visualization-admin.css"></style>
