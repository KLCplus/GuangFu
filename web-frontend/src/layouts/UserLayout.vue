<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import ThemeToggle from '../components/ThemeToggle.vue'
import { DataAnalysis, Cpu, Cloudy, Document, Menu, Setting, User, Tools } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 协作提示：本菜单对应 docs/hanxxi-pc-user-work-summary.md 的 PC 用户端页面范围。
// 两位同学开发页面时，页面根节点优先使用 .page-shell / .page-section / .toolbar，避免各写一套间距和标题风格。
const menus = [
  { path: '/dashboard', label: '看板', icon: Menu },
  { path: '/visualization', label: '数据可视化', icon: DataAnalysis },
  { path: '/models/use', label: '模型', icon: Cpu },
  { path: '/cloud-forecast', label: '云图', icon: Cloudy },
  { path: '/marketplace', label: '广场', icon: Tools },
  { path: '/api/overview', label: '概览', icon: Document },
  { path: '/api/keys', label: 'API Keys', icon: Document },
  { path: '/api/billing', label: '余额与流水', icon: Document },
  { path: '/api/usage', label: '使用统计', icon: DataAnalysis },
  { path: '/reports', label: 'Agent', icon: DataAnalysis },
  { path: '/news', label: '新闻通知', icon: Document },
  { path: '/profile', label: '我的', icon: User }
]

const modelMenus = menus.filter((item) => ['/models/use', '/cloud-forecast', '/marketplace'].includes(item.path))
const apiMenus = menus.filter((item) => item.path.startsWith('/api/'))
const primaryMenusBeforeModel = menus.filter((item) => item.path === '/dashboard')
const primaryMenusAfterModel = menus.filter((item) => item.path !== '/dashboard' && !modelMenus.some((child) => child.path === item.path) && !apiMenus.some((child) => child.path === item.path))

const activePath = computed(() => {
  const matched = menus.find((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))
  return matched?.path ?? route.path
})
const modelGroupOpen = computed(() => ['/models/use', '/cloud-forecast', '/marketplace'].some((path) => route.path === path || route.path.startsWith(`${path}/`)))
const apiGroupOpen = computed(() => route.path.startsWith('/api'))

const displayName = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || '用户')

function logout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="app-layout user-layout">
    <aside class="app-sidebar">
      <router-link class="app-logo" to="/dashboard" aria-label="光伏智云首页">
        <img class="brand-icon" src="https://api.iconify.design/solar:sun-2-linear.svg?color=%23202124" alt="" aria-hidden="true" />
        <span class="brand-lockup"><span class="brand-wordmark">GuangFu</span><span class="brand-cn">光伏智云</span></span>
      </router-link>
      <el-menu :default-active="activePath" :default-openeds="[...(modelGroupOpen ? ['model-group'] : []), ...(apiGroupOpen ? ['api-group'] : [])]" router class="app-menu">
        <el-menu-item v-for="item in primaryMenusBeforeModel" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span class="menu-label">{{ item.label }}</span>
        </el-menu-item>
        <el-sub-menu index="model-group">
          <template #title><el-icon><component :is="Cpu" /></el-icon><span class="menu-label">模型</span></template>
          <el-menu-item v-for="item in modelMenus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span class="menu-label">{{ item.label }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="api-group">
          <template #title><el-icon><component :is="Document" /></el-icon><span class="menu-label">API</span></template>
          <el-menu-item v-for="item in apiMenus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span class="menu-label">{{ item.label }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-for="item in primaryMenusAfterModel" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span class="menu-label">{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <div class="app-sidebar-footer">
        <ThemeToggle class="sidebar-theme-toggle" />
        <el-button
          v-if="userStore.hasRole('ADMIN')"
          text
          type="primary"
          class="sidebar-action"
          @click="router.push('/admin/users-apis')"
        >
          管理后台
        </el-button>
        <el-dropdown>
          <span class="sidebar-user">
            <el-avatar class="sidebar-avatar" :size="28" :src="userStore.userInfo.avatarUrl">{{ displayName.charAt(0) }}</el-avatar>
            <span>{{ displayName }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <section class="app-main">
      <main class="app-content">
        <router-view />
      </main>
    </section>
  </div>
</template>
