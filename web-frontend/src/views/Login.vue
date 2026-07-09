<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useUserStore } from '../store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const result = await login(form)
    userStore.setToken(result.token)
    userStore.setUserInfo(result.userInfo)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-panel">
    <div class="auth-title">
      <p class="page-kicker">账号登录</p>
      <h2>欢迎回来</h2>
    </div>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名">
        <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" size="large" type="password" placeholder="请输入密码" show-password />
      </el-form-item>
      <el-button class="full-button" type="primary" size="large" native-type="submit" :loading="loading">
        登录
      </el-button>
    </el-form>
    <p class="auth-switch">
      没有账号？
      <router-link to="/register">立即注册</router-link>
    </p>
  </section>
</template>

