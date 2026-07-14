<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const visual = computed(() => route.path.startsWith('/visualization-ui') || route.path.startsWith('/visualization-admin'))

const pairs: Record<string, string> = {
  '/dashboard': '/visualization-ui/cockpit',
  '/models/use': '/visualization-ui/models/use',
  '/cloud-forecast': '/visualization-ui/cloud-forecast',
  '/marketplace': '/visualization-ui/marketplace',
  '/api/overview': '/visualization-ui/api/overview',
  '/api/keys': '/visualization-ui/api/keys',
  '/api/billing': '/visualization-ui/api/billing',
  '/api/usage': '/visualization-ui/api/usage',
  '/reports': '/visualization-ui/reports',
  '/news': '/visualization-ui/news',
  '/profile': '/visualization-ui/profile',
  '/admin/users-apis': '/visualization-admin/users-apis',
  '/admin/pvoutput': '/visualization-admin/pvoutput',
  '/admin/models': '/visualization-admin/models',
  '/admin/news': '/visualization-admin/news',
  '/admin/announcements': '/visualization-admin/announcements'
}

function toggle() {
  const path = route.path
  if (visual.value) {
    const target = Object.entries(pairs).find(([, value]) => path === value || path.startsWith(`${value}/`))?.[0] || '/dashboard'
    void router.push(target)
    return
  }
  const target = pairs[path] || Object.entries(pairs).find(([key]) => path.startsWith(`${key}/`))?.[1] || '/visualization-ui/cockpit'
  void router.push(target)
}
</script>

<template>
  <button class="theme-toggle" type="button" :aria-label="visual ? '切换标准主题' : '切换可视化主题'" :title="visual ? '切换标准主题' : '切换可视化主题'" @click="toggle">
    <el-icon class="theme-toggle-icon" aria-hidden="true"><Monitor /></el-icon>
    <span class="theme-toggle-label">{{ visual ? '标准主题' : '可视化主题' }}</span>
  </button>
</template>

<style scoped>
.theme-toggle { display: inline-flex; align-items: center; justify-content: flex-start; gap: 7px; width: 100%; min-height: 34px; border: 1px solid currentColor; border-radius: 7px; padding: 6px 9px; background: transparent; color: inherit; font: inherit; font-size: 12px; cursor: pointer; opacity: .82; }
.theme-toggle:hover { opacity: 1; }
.theme-toggle-icon { flex: 0 0 auto; font-size: 16px; }
</style>
