<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import type { Component } from 'vue'
import { useRoute } from 'vue-router'
import { Bell, Cpu, Document, Key, Sunny } from '@element-plus/icons-vue'

interface AdminModuleDefinition {
  component: keyof typeof componentMap
  code: string
  overline: string
  title: string
  description: string
  service: string
  icon: Component
}

const route = useRoute()

const componentMap = {
  usersApis: defineAsyncComponent(() => import('./admin/UsersApisManage.vue')),
  pvoutput: defineAsyncComponent(() => import('./admin/PvOutputManage.vue')),
  models: defineAsyncComponent(() => import('./admin/ModelManage.vue')),
  news: defineAsyncComponent(() => import('./admin/NewsManage.vue')),
  announcements: defineAsyncComponent(() => import('./admin/AnnouncementManage.vue'))
}

const modules: Record<string, AdminModuleDefinition> = {
  'users-apis': {
    component: 'usersApis', code: 'IAM', overline: 'IDENTITY & API AUTHORITY', title: '用户与 API 管理',
    description: '审查平台用户凭证、访问状态与开放接口权限。', service: '身份权限服务', icon: Key
  },
  pvoutput: {
    component: 'pvoutput', code: 'SRC', overline: 'PVOUTPUT SOURCE CONTROL', title: '数据源管理',
    description: '维护公开光伏数据源、同步状态与接入配置。', service: '数据源控制服务', icon: Sunny
  },
  models: {
    component: 'models', code: 'MDL', overline: 'MODEL CATALOG CONTROL', title: '模型管理',
    description: '管理预测模型目录、版本、价格与服务状态。', service: '模型目录服务', icon: Cpu
  },
  news: {
    component: 'news', code: 'NEWS', overline: 'NEWS OPERATIONS CENTER', title: '新闻管理',
    description: '维护平台新闻内容、发布状态与展示顺序。', service: '内容发布服务', icon: Bell
  },
  announcements: {
    component: 'announcements', code: 'NTF', overline: 'ANNOUNCEMENT CONTROL', title: '公告管理',
    description: '编辑全站公告并控制通知发布与下线状态。', service: '平台通知服务', icon: Document
  }
}

const moduleKey = computed(() => String(route.meta.visualAdminModule || 'users-apis'))
const moduleDefinition = computed(() => modules[moduleKey.value] ?? modules['users-apis']!)
const activeComponent = computed(() => componentMap[moduleDefinition.value.component])
</script>

<template>
  <section class="visual-module-page visual-admin-module" :class="`admin-module-${moduleDefinition.component}`">
    <header class="module-command-heading">
      <div class="module-command-icon" aria-hidden="true">
        <el-icon><component :is="moduleDefinition.icon" /></el-icon>
        <i></i>
      </div>
      <div class="module-command-title">
        <span>{{ moduleDefinition.overline }} · {{ moduleDefinition.code }}</span>
        <h1>{{ moduleDefinition.title }}</h1>
        <p>{{ moduleDefinition.description }}</p>
      </div>
      <div class="module-telemetry" aria-label="管理模块状态">
        <article><small>CONTROL SERVICE</small><strong>{{ moduleDefinition.service }}</strong><em><i></i>在线</em></article>
        <article><small>AUTHORITY MODE</small><strong>管理员权限</strong><em><i></i>已验证</em></article>
        <article><small>AUDIT CHANNEL</small><strong>操作留痕</strong><em><i></i>启用</em></article>
      </div>
      <div class="module-heading-axis" aria-hidden="true"><i></i><b></b></div>
    </header>

    <section class="module-command-panel">
      <span class="panel-corner corner-tl" aria-hidden="true"></span>
      <span class="panel-corner corner-br" aria-hidden="true"></span>
      <div class="module-content admin-content visual-admin-content-host">
        <component :is="activeComponent" />
      </div>
    </section>
  </section>
</template>

<style src="../styles/visualization-modules.css"></style>
<style src="../styles/visualization-admin.css"></style>
