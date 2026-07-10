<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  emailCodeLogin,
  faceLogin,
  getOAuthAuthorizeUrl,
  getProfile,
  login,
  oauthCallback,
  sendEmailCode,
  type LoginResult
} from '../api/auth'
import { useUserStore } from '../store/user'

type LoginMode = 'password' | 'code' | 'face'
type SubmitAction = () => void | Promise<void>

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeMode = ref<LoginMode>('password')
const loading = ref(false)
const codeSending = ref(false)
const codeCountdown = ref(0)
const faceFile = ref<File>()
const facePreview = ref('')
const videoRef = ref<HTMLVideoElement>()
const cameraActive = ref(false)
let countdownTimer: number | undefined
let cameraStream: MediaStream | undefined

const passwordForm = reactive({
  username: '',
  password: ''
})

const codeForm = reactive({
  email: '',
  code: ''
})

const redirectPath = computed(() => {
  const redirect = firstQueryValue(route.query.redirect)
  if (redirect && redirect.startsWith('/')) return redirect
  return '/dashboard'
})

function resolveHome(roles?: string[]) {
  return roles?.includes('ADMIN') ? '/admin' : '/dashboard'
}

onMounted(() => {
  handleOAuthReturn()
})

onBeforeUnmount(() => {
  if (countdownTimer) window.clearInterval(countdownTimer)
  stopCamera()
  clearFacePreview()
})

function firstQueryValue(value: unknown) {
  return Array.isArray(value) ? value[0] : typeof value === 'string' ? value : ''
}

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

async function finishLogin(result: LoginResult) {
  saveLogin(result)
  ElMessage.success('登录成功')
  const redirect = firstQueryValue(route.query.redirect)
  if (redirect && redirect.startsWith('/')) {
    await router.replace(redirect)
  } else {
    await router.replace(resolveHome(result.userInfo.roles))
  }
}

async function handleOAuthReturn() {
  const error = firstQueryValue(route.query.error)
  if (error) {
    ElMessage.error(error)
    await router.replace('/login')
    return
  }

  const token = firstQueryValue(route.query.token)
  if (token) {
    userStore.setToken(token)
    userStore.setRefreshToken(firstQueryValue(route.query.refreshToken))
    try {
      const profile = await getProfile()
      userStore.setUserInfo(profile)
      ElMessage.success('GitHub 登录成功')
      const redirect = firstQueryValue(route.query.redirect)
      if (redirect && redirect.startsWith('/')) {
        await router.replace(redirect)
      } else {
        await router.replace(resolveHome(profile.roles))
      }
    } catch {
      ElMessage.error('GitHub 登录成功，但获取用户信息失败')
      await router.replace('/login')
    }
    return
  }

  const code = firstQueryValue(route.query.code)
  const state = firstQueryValue(route.query.state)
  const provider = firstQueryValue(route.query.provider) || 'github'
  if (code && state) {
    loading.value = true
    try {
      const result = await oauthCallback(provider, {
        code,
        state,
        redirectUri: `${window.location.origin}/login`
      })
      await finishLogin(result)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '第三方登录失败')
    } finally {
      loading.value = false
    }
  }
}

async function submitPasswordLogin() {
  if (!passwordForm.username.trim() || !passwordForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    const result = await login({
      username: passwordForm.username.trim(),
      password: passwordForm.password
    })
    await finishLogin(result)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '登录失败')
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

async function sendLoginCode() {
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

async function submitCodeLogin() {
  if (!codeForm.email.trim() || !codeForm.code.trim()) {
    ElMessage.warning('请输入邮箱和验证码')
    return
  }

  loading.value = true
  try {
    const result = await emailCodeLogin({
      email: codeForm.email.trim(),
      code: codeForm.code.trim()
    })
    await finishLogin(result)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '验证码登录失败')
  } finally {
    loading.value = false
  }
}

function clearFacePreview() {
  if (facePreview.value) URL.revokeObjectURL(facePreview.value)
  facePreview.value = ''
}

function setFaceFile(file: File) {
  faceFile.value = file
  clearFacePreview()
  facePreview.value = URL.createObjectURL(file)
}

function selectFaceFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  setFaceFile(file)
}

async function openCamera() {
  if (!navigator.mediaDevices?.getUserMedia) {
    ElMessage.error('当前浏览器不支持摄像头调用')
    return
  }

  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user' },
      audio: false
    })
    cameraActive.value = true
    await nextTick()
    if (videoRef.value) {
      videoRef.value.srcObject = cameraStream
      await videoRef.value.play()
    }
  } catch {
    ElMessage.error('无法打开摄像头，请检查浏览器权限')
  }
}

function stopCamera() {
  cameraStream?.getTracks().forEach((track) => track.stop())
  cameraStream = undefined
  cameraActive.value = false
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
}

async function captureFacePhoto() {
  const video = videoRef.value
  if (!video || !video.videoWidth || !video.videoHeight) {
    ElMessage.warning('摄像头画面尚未准备好')
    return
  }

  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  const context = canvas.getContext('2d')
  if (!context) {
    ElMessage.error('无法生成拍照图片')
    return
  }

  context.drawImage(video, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
  if (!blob) {
    ElMessage.error('拍照失败，请重试')
    return
  }

  setFaceFile(new File([blob], `face-${Date.now()}.jpg`, { type: 'image/jpeg' }))
  stopCamera()
  ElMessage.success('已完成拍照')
}

async function submitFaceLogin() {
  if (!faceFile.value) {
    ElMessage.warning('请先拍照或选择人脸图片')
    return
  }

  loading.value = true
  try {
    const result = await faceLogin(faceFile.value)
    await finishLogin(result)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '人脸登录暂不可用')
  } finally {
    loading.value = false
  }
}

async function loginWithGithub() {
  loading.value = true
  try {
    const result = await getOAuthAuthorizeUrl('github', {
      redirectUri: `${window.location.origin}/login`
    })
    window.location.href = result.authorizeUrl
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : 'GitHub 登录暂不可用')
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-panel login-panel">
    <div class="auth-title">
      <p class="page-kicker"></p>
      <h2>欢迎回来光伏智云</h2>
    </div>

    <el-tabs v-model="activeMode" stretch>
      <el-tab-pane label="密码登录" name="password">
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="用户名">
            <el-input
              v-model="passwordForm.username"
              size="large"
              placeholder="请输入用户名"
              autocomplete="username"
              @keyup="handleKeyUp($event, submitPasswordLogin)"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="passwordForm.password"
              size="large"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
              @keyup="handleKeyUp($event, submitPasswordLogin)"
            />
          </el-form-item>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submitPasswordLogin">
            登录
          </el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="验证码登录" name="code">
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="邮箱">
            <el-input v-model="codeForm.email" size="large" placeholder="请输入邮箱" autocomplete="email" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="auth-code-row">
              <el-input
                v-model="codeForm.code"
                size="large"
                placeholder="请输入验证码"
                @keyup="handleKeyUp($event, submitCodeLogin)"
              />
              <el-button
                size="large"
                :loading="codeSending"
                :disabled="codeCountdown > 0"
                @click="sendLoginCode"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送' }}
              </el-button>
            </div>
          </el-form-item>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submitCodeLogin">
            验证码登录
          </el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="人脸登录" name="face">
        <div class="face-login-box">
          <p class="face-note">请使用已录入人脸的账号。首次使用请先通过其他方式登录并完成人脸录入。</p>
          <p class="face-title">点击下方按钮打开摄像头拍照</p>

          <div v-if="cameraActive" class="camera-panel">
            <video ref="videoRef" autoplay muted playsinline />
            <div class="face-actions">
              <el-button type="primary" size="large" @click="captureFacePhoto">拍照</el-button>
              <el-button size="large" @click="stopCamera">关闭摄像头</el-button>
            </div>
          </div>

          <label v-else class="face-picker">
            <input type="file" accept="image/*" @change="selectFaceFile" />
            <img v-if="facePreview" :src="facePreview" alt="人脸预览" />
            <span v-else>选择文件</span>
          </label>

          <el-button class="full-button camera-button" size="large" @click="openCamera">
            打开摄像头
          </el-button>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submitFaceLogin">
            人脸登录
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <div class="oauth-divider"><span>或</span></div>
    <el-button class="full-button github-button" size="large" :loading="loading" @click="loginWithGithub">
      GitHub 登录
    </el-button>

    <p class="auth-switch">
      没有账号？
      <router-link to="/register">立即注册</router-link>
    </p>
  </section>
</template>

<style scoped>
.login-panel {
  width: 100%;
}

.auth-code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px;
  gap: 10px;
  width: 100%;
}

.face-login-box {
  display: grid;
  gap: 12px;
}

.face-note {
  margin: 0;
  color: #52647f;
  line-height: 1.8;
}

.face-title {
  margin: 10px 0 2px;
  color: #52647f;
  font-size: 18px;
  font-weight: 700;
  text-align: center;
}

.camera-button {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: #ffffff;
  font-weight: 700;
}

.camera-panel {
  display: grid;
  gap: 10px;
}

.camera-panel video,
.face-picker {
  width: 100%;
  height: 220px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f7fbff;
  overflow: hidden;
}

.camera-panel video {
  object-fit: cover;
}

.face-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.face-picker {
  display: grid;
  place-items: center;
  color: var(--color-muted);
  cursor: pointer;
}

.face-picker input {
  display: none;
}

.face-picker img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.oauth-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 20px 0 12px;
  color: var(--color-muted);
  font-size: 13px;
}

.oauth-divider::before,
.oauth-divider::after {
  content: "";
  height: 1px;
  flex: 1;
  background: var(--color-border);
}

.github-button {
  color: #ffffff;
  border-color: #24292f;
  background: #24292f;
  font-weight: 700;
}
</style>
