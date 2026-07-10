<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { emailCodeRegister, register, sendEmailCode, type LoginResult } from '../api/auth'
import { useUserStore } from '../store/user'

type RegisterMode = 'password' | 'code'
type SubmitAction = () => void | Promise<void>

const router = useRouter()
const userStore = useUserStore()
const activeMode = ref<RegisterMode>('password')
const loading = ref(false)
const codeSending = ref(false)
const codeCountdown = ref(0)
let countdownTimer: number | undefined

const passwordForm = reactive({
  username: '',
  email: '',
  password: ''
})

const codeForm = reactive({
  username: '',
  email: '',
  code: ''
})

onBeforeUnmount(() => {
  if (countdownTimer) window.clearInterval(countdownTimer)
})

function saveLogin(result: LoginResult) {
  userStore.setToken(result.token)
  userStore.setRefreshToken(result.refreshToken)
  userStore.setUserInfo(result.userInfo)
}

function handleKeyUp(event: KeyboardEvent, action: SubmitAction) {
  if (typeof event.key !== 'string') return
  if (event.key.toLowerCase() === 'enter') {
    void action()
  }
}

function validateUsername(username: string) {
  return username.trim().length >= 4 && username.trim().length <= 32
}

function validatePassword(password: string) {
  return /^(?=.*[a-zA-Z])(?=.*\d).{8,64}$/.test(password)
}

async function submitPasswordRegister() {
  const payload = {
    username: passwordForm.username.trim(),
    email: passwordForm.email.trim(),
    password: passwordForm.password
  }

  if (!validateUsername(payload.username)) {
    ElMessage.warning('用户名长度需为 4-32 位')
    return
  }
  if (!payload.email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  if (!validatePassword(payload.password)) {
    ElMessage.warning('密码需为 8-64 位，并同时包含字母和数字')
    return
  }

  loading.value = true
  try {
    await register(payload)
    ElMessage.success('注册成功，请登录')
    await router.replace('/login')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '注册失败')
  } finally {
    loading.value = false
  }
}

function startCountdown() {
  codeCountdown.value = 60
  if (countdownTimer) window.clearInterval(countdownTimer)
  countdownTimer = window.setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0 && countdownTimer) {
      window.clearInterval(countdownTimer)
      countdownTimer = undefined
    }
  }, 1000)
}

async function sendRegisterCode() {
  if (!codeForm.email.trim()) {
    ElMessage.warning('请输入邮箱')
    return
  }

  codeSending.value = true
  try {
    await sendEmailCode({ email: codeForm.email.trim() })
    startCountdown()
    ElMessage.success('验证码已发送')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '验证码发送失败')
  } finally {
    codeSending.value = false
  }
}

async function submitCodeRegister() {
  if (!validateUsername(codeForm.username)) {
    ElMessage.warning('用户名长度需为 4-32 位')
    return
  }
  if (!codeForm.email.trim() || !codeForm.code.trim()) {
    ElMessage.warning('请输入邮箱和验证码')
    return
  }

  loading.value = true
  try {
    const result = await emailCodeRegister({
      username: codeForm.username.trim(),
      email: codeForm.email.trim(),
      code: codeForm.code.trim()
    })
    saveLogin(result)
    ElMessage.success('注册并登录成功')
    const target = result.userInfo.roles?.includes('ADMIN') ? '/admin' : '/dashboard'
    await router.replace(target)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '验证码注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-panel register-panel">
    <div class="auth-title">
      <p class="page-kicker">创建账号</p>
      <h2>开始使用平台</h2>
    </div>

    <el-tabs v-model="activeMode" stretch>
      <el-tab-pane label="密码注册" name="password">
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="用户名">
            <el-input
              v-model="passwordForm.username"
              size="large"
              placeholder="4-32 位用户名"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="passwordForm.email" size="large" placeholder="请输入邮箱" autocomplete="email" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="passwordForm.password"
              size="large"
              type="password"
              placeholder="至少 8 位，包含字母和数字"
              autocomplete="new-password"
              show-password
              @keyup="handleKeyUp($event, submitPasswordRegister)"
            />
          </el-form-item>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submitPasswordRegister">
            注册
          </el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="验证码注册" name="code">
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="用户名">
            <el-input v-model="codeForm.username" size="large" placeholder="4-32 位用户名" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="codeForm.email" size="large" placeholder="请输入邮箱" autocomplete="email" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="auth-code-row">
              <el-input
                v-model="codeForm.code"
                size="large"
                placeholder="请输入验证码"
                @keyup="handleKeyUp($event, submitCodeRegister)"
              />
              <el-button
                size="large"
                :loading="codeSending"
                :disabled="codeCountdown > 0"
                @click="sendRegisterCode"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送' }}
              </el-button>
            </div>
          </el-form-item>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submitCodeRegister">
            验证码注册
          </el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <p class="auth-switch">
      已有账号？
      <router-link to="/login">去登录</router-link>
    </p>
  </section>
</template>

<style scoped>
.register-panel {
  max-width: 460px;
}

.auth-code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px;
  gap: 10px;
  width: 100%;
}
</style>
