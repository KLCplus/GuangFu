<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const menu = [
  ['/', '总览'],
  ['/stations', '电站'],
  ['/prediction', '模型预测'],
  ['/prediction/history', '预测历史'],
  ['/analysis', '综合分析'],
  ['/api-platform', '开放平台'],
  ['/news', '新闻通知'],
  ['/admin/users', '管理端']
]
</script>

<template>
  <router-view v-if="route.meta.plain" />
  <el-container v-else class="layout">
    <el-aside width="220px">
      <div class="brand">PV Platform</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item v-for="[path, label] in menu" :key="path" :index="path">{{ label }}</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>
        <span>光伏发电综合分析与预测系统</span>
        <el-button text @click="router.push('/login')">退出</el-button>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
