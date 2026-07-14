<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '../store/user'
import Profile from './Profile.vue'

const userStore = useUserStore()
const displayName = computed(() => userStore.userInfo.nickname || userStore.userInfo.username || '运行用户')
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
