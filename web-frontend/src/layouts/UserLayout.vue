<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 协作提示：本菜单对应 docs/hanxxi-pc-user-work-summary.md 的 PC 用户端页面范围。
// 两位同学开发页面时，页面根节点优先使用 .page-shell / .page-section / .toolbar，避免各写一套间距和标题风格。
const menus = [
  { path: '/dashboard', label: '看板' },
  { path: '/models/use', label: '模型' },
  { path: '/cloud-forecast', label: '云图' },
  { path: '/weather', label: '天气' },
  { path: '/pvoutput', label: '公开电站' },
  { path: '/marketplace', label: '广场' },
  { path: '/api', label: 'API' },
  { path: '/reports', label: '报告' },
  { path: '/news', label: '新闻' },
  { path: '/profile', label: '我的' }
]

const activePath = computed(() => {
  const matched = menus.find((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))
  return matched?.path ?? route.path
})

const displayName = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || '用户')

function logout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="app-layout user-layout">
    <aside class="app-sidebar">
      <router-link class="app-logo" to="/dashboard">
        <span class="brand-mark">PV</span>
        <span>光伏平台</span>
      </router-link>
      <el-menu :default-active="activePath" router class="app-menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <div class="app-sidebar-footer">
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
            <span class="sidebar-avatar">{{ displayName.charAt(0) }}</span>
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
