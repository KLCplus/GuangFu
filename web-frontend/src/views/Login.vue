<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/store/user'

const form = reactive({ username: 'demo', password: '123456' })
const router = useRouter()
const store = useUserStore()

async function submit() {
  try {
    const response = await login(form)
    store.setToken(response.data.data.token)
    await router.push('/')
  } catch {
    ElMessage.error('登录失败，请确认后端已启动')
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>光伏发电分析平台</h2>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" style="width: 100%" @click="submit">登录</el-button>
      </el-form>
      <p>骨架版本：任意非空账号密码均可登录。</p>
    </el-card>
  </div>
</template>
