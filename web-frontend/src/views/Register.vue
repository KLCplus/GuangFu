<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'

const router = useRouter()
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: ''
})

async function submit() {
  if (!form.username || !form.email || !form.password) {
    ElMessage.warning('请完整填写注册信息')
    return
  }
  loading.value = true
  try {
    await register(form)
    ElMessage.success('注册成功，请登录')
    router.replace('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-panel">
    <div class="auth-title">
      <p class="page-kicker">创建账号</p>
      <h2>开始使用平台</h2>
    </div>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名">
        <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" size="large" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" size="large" type="password" placeholder="请输入密码" show-password />
      </el-form-item>
      <el-button class="full-button" type="primary" size="large" native-type="submit" :loading="loading">
        注册
      </el-button>
    </el-form>
    <p class="auth-switch">
      已有账号？
      <router-link to="/login">去登录</router-link>
    </p>
  </section>
</template>
