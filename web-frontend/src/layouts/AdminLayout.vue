<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menus = [
  { path: '/admin/users-apis', label: '用户/API' },
  { path: '/admin/stations', label: '电站' },
  { path: '/admin/models', label: '模型' },
  { path: '/admin/news', label: '新闻管理' }
]

const activePath = computed(() => {
  const matched = menus.find((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))
  return matched?.path ?? route.path
})

const displayName = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || '管理员')

function logout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="admin-shell">
    <!-- 深色侧边栏 -->
    <aside class="admin-sidebar">
      <router-link class="admin-brand" to="/admin/users-apis">
        <span class="admin-brand-mark">PV</span>
        <div class="admin-brand-text">
          <span class="admin-brand-title">光伏平台</span>
          <span class="admin-brand-sub">管理后台</span>
        </div>
      </router-link>

      <el-menu
        :default-active="activePath"
        router
        class="admin-menu"
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="admin-sidebar-footer">
        <el-button text class="back-to-user" @click="router.push('/dashboard')">
          返回用户端
        </el-button>
        <el-dropdown>
          <span class="admin-user">
            <span class="admin-avatar">{{ displayName.charAt(0) }}</span>
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

    <!-- 主内容区 -->
    <div class="admin-main">
      <!-- 内容区 -->
      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 184px minmax(0, 1fr);
  min-height: 100vh;
  background: #f0f2f5;
}

/* ===== 深色侧边栏 ===== */
.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #001529;
  overflow-y: auto;
  overflow-x: hidden;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 58px;
  padding: 0 14px;
  min-width: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.admin-brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  border-radius: 8px;
  background: var(--color-primary);
  color: #ffffff;
  font-weight: 800;
  font-size: 14px;
}

.admin-brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  line-height: 1.2;
}

.admin-brand-title {
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
}

.admin-brand-sub {
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
}

/* 深色菜单覆盖 */
.admin-menu {
  flex: 1;
  margin-top: 8px;
  border-right: 0;
}

.admin-menu .el-menu-item {
  margin: 2px 7px;
  border-radius: 6px;
  height: 40px;
  line-height: 40px;
  padding-left: 14px !important;
}

.admin-menu .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.08) !important;
}

.admin-menu .el-menu-item.is-active {
  background: var(--color-primary) !important;
  color: #ffffff !important;
}

/* 侧边栏底部 */
.admin-sidebar-footer {
  display: grid;
  gap: 6px;
  padding: 14px 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.back-to-user {
  justify-content: flex-start;
  width: 100%;
  color: rgba(255, 255, 255, 0.65) !important;
  font-size: 13px;
}

.back-to-user:hover {
  color: #ffffff !important;
  background: rgba(255, 255, 255, 0.08);
}

/* ===== 主内容区 ===== */
.admin-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* 顶部栏 */
.admin-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.admin-topbar-left {
  display: flex;
  align-items: center;
}

.admin-topbar-right {
  display: flex;
  align-items: center;
}

.admin-user {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  padding: 4px 6px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.78);
  cursor: pointer;
  font-size: 14px;
}

.admin-user:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.admin-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
}

/* 页面标题区 */
.admin-page-header {
  padding: 20px 24px 0;
  background: #ffffff;
}

.admin-page-header h1 {
  margin: 0;
  padding-bottom: 16px;
  font-size: 20px;
  font-weight: 600;
  color: #001529;
  border-bottom: 1px solid #f0f0f0;
}

/* 内容区 */
.admin-content {
  flex: 1;
  min-width: 0;
  padding: 20px 24px 32px;
}

/* 响应式 */
@media (max-width: 900px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    position: static;
    height: auto;
  }

  .admin-menu {
    display: flex;
    overflow-x: auto;
  }

  .admin-menu .el-menu-item {
    flex: 0 0 auto;
  }
}
</style>
