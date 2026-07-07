<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 协作提示：本菜单严格对应 docs/光伏预测平台前端页面拆分文档.md 的管理端 3 页。
// 管理端以表格和筛选为主，筛选区用 .toolbar，主体用 .page-section，减少营销化卡片。
const menus = [
  { path: '/admin/users-apis', label: '用户/API' },
  { path: '/admin/stations', label: '电站' },
  { path: '/admin/models', label: '模型' }
]

const activePath = computed(() => {
  const matched = menus.find((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))
  return matched?.path ?? route.path
})

function logout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="app-layout admin-layout">
    <aside class="app-sidebar">
      <router-link class="app-logo" to="/admin/users-apis">
        <span class="brand-mark">PV</span>
        <span>管理后台</span>
      </router-link>
      <el-menu :default-active="activePath" router class="app-menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <section class="app-main">
      <header class="app-header">
        <div>
          <p class="page-kicker">管理端</p>
          <h1>{{ route.meta.title || '用户/API' }}</h1>
        </div>
        <div class="header-actions">
          <el-button text type="primary" @click="router.push('/dashboard')">返回用户端</el-button>
          <el-button plain @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="app-content">
        <router-view />
      </main>
    </section>
  </div>
</template>
