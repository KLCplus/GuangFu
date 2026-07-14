<script setup lang="ts">
import { computed } from 'vue'
import { Key, Lock, User, Wallet } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import Profile from './Profile.vue'

const userStore = useUserStore()
const displayName = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || '运行用户')
const roleLabel = computed(() => {
  if (userStore.hasRole('ADMIN')) return '系统管理员'
  if (userStore.hasRole('API_USER')) return 'API 用户'
  return '运行用户'
})
</script>

<template>
  <section class="visual-profile-page">
    <header class="profile-command-heading">
      <div class="profile-identity-node" aria-hidden="true">
        <span class="identity-orbit orbit-outer"></span>
        <span class="identity-orbit orbit-inner"></span>
        <el-avatar :size="58" :src="userStore.userInfo.avatarUrl">{{ displayName.charAt(0) }}</el-avatar>
        <i></i>
      </div>

      <div class="profile-command-title">
        <h1>我的能源账户</h1>
        <p>统一管理身份资料、安全认证、资金账户与模型调用权益。</p>
      </div>

      <div class="profile-node-matrix" aria-label="账户运行状态">
        <article>
          <el-icon><User /></el-icon>
          <span><strong>{{ displayName }}</strong></span>
          <em>{{ roleLabel }}</em>
        </article>
        <article>
          <el-icon><Lock /></el-icon>
          <span><strong>认证链路</strong></span>
          <em><i></i>在线</em>
        </article>
        <article>
          <el-icon><Wallet /></el-icon>
          <span><strong>账户服务</strong></span>
          <em><i></i>同步</em>
        </article>
        <article>
          <el-icon><Key /></el-icon>
          <span><strong>权益节点</strong></span>
          <em><i></i>可用</em>
        </article>
      </div>

      <div class="profile-heading-axis" aria-hidden="true"><i></i><b></b></div>
    </header>

    <section class="profile-command-panel">
      <span class="panel-corner corner-tl" aria-hidden="true"></span>
      <span class="panel-corner corner-br" aria-hidden="true"></span>
      <Profile
        base-path="/visualization-ui/profile"
        billing-path="/visualization-ui/api/billing"
        api-keys-path="/visualization-ui/api/keys"
        dialog-class="visual-profile-dialog"
      />
    </section>
  </section>
</template>

<style src="../styles/visualization-profile.css"></style>
