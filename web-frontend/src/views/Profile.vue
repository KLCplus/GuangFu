<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  loadProfileOverview,
  saveUserProfile,
  unbindOAuthProvider,
  updateUserPassword
} from '../api/userPages'
import type { DataSource, ProfileOverview } from '../api/userPages'
import { deleteFace, enrollFace, getFaceStatus, type ChangePasswordPayload, type UpdateProfilePayload, type UserProfile } from '../api/user'
import { useUserStore } from '../store/user'

interface ProfileForm {
  nickname: string
  email: string
  phone: string
  gender: number
}

interface PasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const passwordSaving = ref(false)
const actionLoadingId = ref<number | null>(null)
const faceSaving = ref(false)
const faceRevoking = ref(false)
const faceFile = ref<File>()
const facePreview = ref('')
const videoRef = ref<HTMLVideoElement>()
const cameraActive = ref(false)
let cameraStream: MediaStream | undefined
const loadError = ref('')
const overview = ref<ProfileOverview | null>(null)
const dataSource = ref<DataSource>('remote')

const profileForm = reactive<ProfileForm>({
  nickname: '',
  email: '',
  phone: '',
  gender: 0
})

const passwordForm = reactive<PasswordForm>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const profile = computed(() => overview.value?.profile ?? null)
const wallet = computed(() => overview.value?.wallet ?? null)
const apiKeys = computed(() => overview.value?.apiKeys ?? [])
const entitlements = computed(() => overview.value?.apiEntitlements ?? [])
const walletRecords = computed(() => overview.value?.walletRecords ?? [])
const oauthAccounts = computed(() => overview.value?.oauthAccounts ?? [])
const faceStatus = computed(() => overview.value?.faceStatus)

const profileStats = computed(() => [
  {
    label: 'API Key',
    value: String(apiKeys.value.length),
    note: `${apiKeys.value.filter((item) => item.status === 'ACTIVE').length} 个启用`
  },
  {
    label: 'API 权益',
    value: String(entitlements.value.length),
    note: '来自开放平台权益接口'
  },
  {
    label: '钱包余额',
    value: wallet.value ? `￥${wallet.value.balance.toFixed(2)}` : '暂无数据',
    note: '来自开放平台钱包接口'
  },
  {
    label: '第三方绑定',
    value: String(oauthAccounts.value.length),
    note: faceStatus.value?.enrolled ? '已录入人脸' : '人脸未录入或未返回'
  }
])

onMounted(() => {
  void fetchOverview()
  window.addEventListener('pv:user-profile-updated', handleExternalProfileUpdate)
})

onBeforeUnmount(() => {
  window.removeEventListener('pv:user-profile-updated', handleExternalProfileUpdate)
  stopFaceCamera()
  clearFaceSelection()
})

function handleExternalProfileUpdate() {
  void fetchOverview()
}

async function fetchOverview() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await loadProfileOverview()
    overview.value = result.data
    dataSource.value = result.source
    fillProfileForm(result.data.profile)
    if (result.source !== 'remote') {
      ElMessage.info('部分资料使用模拟数据兜底')
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '个人资料加载失败'
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!profileForm.nickname.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }

  saving.value = true
  try {
    const payload: UpdateProfilePayload = {
      nickname: profileForm.nickname.trim(),
      email: profileForm.email.trim(),
      phone: profileForm.phone.trim(),
      gender: profileForm.gender
    }
    const result = await saveUserProfile(payload)
    if (overview.value) {
      overview.value.profile = result.data
    }
    fillProfileForm(result.data)
    userStore.setUserInfo({
      userId: result.data.userId,
      username: result.data.username,
      nickname: result.data.nickname,
      email: result.data.email,
      roles: result.data.roles,
      status: result.data.status
    })
    ElMessage.success(result.source === 'mock' ? '当前为模拟保存' : '资料已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料保存失败')
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请输入旧密码和新密码')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  passwordSaving.value = true
  try {
    const payload: ChangePasswordPayload = {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    }
    const result = await updateUserPassword(payload)
    resetPasswordForm()
    ElMessage.success(result.source === 'mock' ? '当前为模拟修改密码' : '密码已修改')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码修改失败')
  } finally {
    passwordSaving.value = false
  }
}

async function unbindOAuth(oauthId: number, provider: string) {
  try {
    await ElMessageBox.confirm(`确认解绑 ${provider} 账号吗？`, '解绑第三方账号', {
      type: 'warning',
      confirmButtonText: '解绑',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  actionLoadingId.value = oauthId
  try {
    const result = await unbindOAuthProvider(oauthId)
    if (overview.value) {
      overview.value.oauthAccounts = overview.value.oauthAccounts.filter((item) => item.oauthId !== oauthId)
    }
    ElMessage.success(result.source === 'mock' ? '当前为模拟解绑' : '第三方账号已解绑')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解绑失败')
  } finally {
    actionLoadingId.value = null
  }
}

function clearFaceSelection() {
  if (facePreview.value) URL.revokeObjectURL(facePreview.value)
  facePreview.value = ''
  faceFile.value = undefined
}

function selectFaceFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不超过 5MB')
    input.value = ''
    return
  }
  clearFaceSelection()
  faceFile.value = file
  facePreview.value = URL.createObjectURL(file)
}

async function openFaceCamera() {
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

function stopFaceCamera() {
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

  clearFaceSelection()
  faceFile.value = new File([blob], 'face-' + Date.now() + '.jpg', { type: 'image/jpeg' })
  facePreview.value = URL.createObjectURL(faceFile.value)
  stopFaceCamera()
  ElMessage.success('已完成拍照')
}

async function refreshFaceStatus() {
  if (!overview.value) return
  overview.value.faceStatus = await getFaceStatus()
}

async function enrollSelectedFace() {
  if (!faceFile.value) {
    ElMessage.warning('请先选择人脸图片')
    return
  }

  faceSaving.value = true
  try {
    await enrollFace(faceFile.value)
    await refreshFaceStatus()
    clearFaceSelection()
    ElMessage.success('人脸已录入，可用于人脸登录')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '人脸录入失败')
  } finally {
    faceSaving.value = false
  }
}

async function revokeFaceAuth() {
  try {
    await ElMessageBox.confirm('确认撤销当前账号的人脸信息吗？', '撤销人脸认证', {
      type: 'warning',
      confirmButtonText: '撤销',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  faceRevoking.value = true
  try {
    await deleteFace()
    await refreshFaceStatus()
    clearFaceSelection()
    ElMessage.success('人脸信息已撤销')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤销人脸失败')
  } finally {
    faceRevoking.value = false
  }
}

function fillProfileForm(value: UserProfile) {
  profileForm.nickname = value.nickname ?? ''
  profileForm.email = value.email ?? ''
  profileForm.phone = value.phone ?? ''
  profileForm.gender = value.gender ?? 0
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

function sourceLabel(source: DataSource) {
  if (source === 'remote') return '真实接口'
  if (source === 'mixed') return '混合数据'
  return '模拟数据'
}

function sourceType(source: DataSource) {
  if (source === 'remote') return 'success'
  if (source === 'mixed') return 'warning'
  return 'info'
}

function genderLabel(gender?: number) {
  if (gender === 1) return '男'
  if (gender === 2) return '女'
  return '未设置'
}

function roleText(roles?: string[]) {
  return roles?.length ? roles.join(' / ') : 'USER'
}

function apiKeyStatusType(status?: string) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'EXPIRED') return 'warning'
  return 'info'
}

function apiKeyStatusLabel(status?: string) {
  if (status === 'ACTIVE') return '启用'
  if (status === 'EXPIRED') return '过期'
  return '停用'
}

function recordTypeLabel(type: string) {
  const labels: Record<string, string> = {
    RECHARGE: '充值',
    CONSUME: '消费',
    REFUND: '退款'
  }
  return labels[type] ?? type
}
</script>

<template>
  <section class="profile-page">
    <div class="page-heading">
      <div>
        <h1>我的</h1>
      </div>
      <div class="heading-actions">
        <el-tag :type="sourceType(dataSource)" effect="light">{{ sourceLabel(dataSource) }}</el-tag>
        <el-button :loading="loading" @click="fetchOverview">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="dataSource !== 'remote'"
      title="部分信息当前使用 mock 兜底；真实接口恢复后会自动展示后端数据。"
      type="info"
      show-icon
      :closable="false"
    />

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" type="primary" @click="fetchOverview">重试</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="profile-content">
      <template v-if="profile">
        <section class="hero-panel">
          <div class="avatar-block">
            <el-avatar :size="72" :src="profile.avatarUrl">
              {{ (profile.nickname || profile.username || 'U').slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div>
              <h2>{{ profile.nickname || profile.username }}</h2>
              <p>{{ profile.username }} · {{ roleText(profile.roles) }}</p>
            </div>
          </div>
          <div class="hero-meta">
            <div>
              <span>邮箱</span>
              <strong>{{ profile.email || '未绑定' }}</strong>
            </div>
            <div>
              <span>手机号</span>
              <strong>{{ profile.phone || '未绑定' }}</strong>
            </div>
            <div>
              <span>性别</span>
              <strong>{{ genderLabel(profile.gender) }}</strong>
            </div>
            <div>
              <span>注册时间</span>
              <strong>{{ profile.createdAt || '-' }}</strong>
            </div>
          </div>
        </section>

        <div class="overview-grid">
          <div v-for="item in profileStats" :key="item.label" class="overview-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.note }}</small>
          </div>
        </div>

        <div class="profile-layout">
          <section class="panel form-panel">
            <div class="panel-head">
              <div>
                <h2>基本资料</h2>
                <p>来源：GET /api/user/profile，保存：PUT /api/user/profile。</p>
              </div>
            </div>
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="用户名">
                <el-input :model-value="profile.username" disabled />
              </el-form-item>
              <el-form-item label="昵称" required>
                <el-input v-model="profileForm.nickname" maxlength="32" show-word-limit />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="profileForm.email" maxlength="80" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="profileForm.phone" maxlength="20" />
              </el-form-item>
              <el-form-item label="性别">
                <el-radio-group v-model="profileForm.gender">
                  <el-radio-button :label="0">未设置</el-radio-button>
                  <el-radio-button :label="1">男</el-radio-button>
                  <el-radio-button :label="2">女</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <div class="form-actions">
                <el-button @click="fillProfileForm(profile)">重置</el-button>
                <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
              </div>
            </el-form>
          </section>

          <section class="panel security-panel">
            <div class="panel-head">
              <div>
                <h2>安全设置</h2>
                <p>密码修改接入 PUT /api/user/password。</p>
              </div>
            </div>
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="旧密码">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <div class="form-actions">
                <el-button @click="resetPasswordForm">清空</el-button>
                <el-button type="primary" :loading="passwordSaving" @click="changePassword">修改密码</el-button>
              </div>
            </el-form>

            <div class="security-list">
              <div>
                <span>邮箱绑定</span>
                <strong>{{ profile.email ? '已绑定' : '未绑定' }}</strong>
                <small>邮箱修改复用资料保存接口，验证码绑定流程待确认。</small>
              </div>
              <div class="face-auth-card">
                <div class="face-auth-top">
                  <div>
                    <span>人脸认证</span>
                    <strong>{{ faceStatus?.enrolled ? '已录入' : '未录入' }}</strong>
                    <small>{{ faceStatus?.enrolledAt ? '录入时间：' + faceStatus.enrolledAt : '选择清晰正脸图片后可录入或更新' }}</small>
                  </div>
                  <el-tag :type="faceStatus?.enrolled ? 'success' : 'info'" effect="light">
                    {{ faceStatus?.enrolled ? '可用' : '未启用' }}
                  </el-tag>
                </div>

                <div v-if="cameraActive" class="face-camera-panel">
                  <video ref="videoRef" autoplay muted playsinline />
                  <div class="face-camera-actions">
                    <el-button type="primary" @click="captureFacePhoto">拍照</el-button>
                    <el-button @click="stopFaceCamera">关闭摄像头</el-button>
                  </div>
                </div>

                <div v-if="facePreview" class="face-preview">
                  <img :src="facePreview" alt="人脸预览" />
                  <el-button text type="danger" @click="clearFaceSelection">移除</el-button>
                </div>

                <div class="face-actions">
                  <label class="face-file-button">
                    <input type="file" accept="image/*" @change="selectFaceFile" />
                    选择图片
                  </label>
                  <el-button plain @click="openFaceCamera">打开摄像头</el-button>
                  <el-button type="primary" :loading="faceSaving" :disabled="!faceFile" @click="enrollSelectedFace">
                    {{ faceStatus?.enrolled ? '更新人脸' : '录入人脸' }}
                  </el-button>
                  <el-button
                    type="danger"
                    plain
                    :loading="faceRevoking"
                    :disabled="!faceStatus?.enrolled"
                    @click="revokeFaceAuth"
                  >
                    撤销
                  </el-button>
                </div>
              </div>
            </div>
          </section>
        </div>

        <div class="profile-layout">
          <section class="panel">
            <div class="panel-head">
              <div>
                <h2>API 权益</h2>
                <p>API Key、已购买模型和权益额度来自开放平台接口。</p>
              </div>
            </div>
            <el-empty v-if="entitlements.length === 0" description="暂无 API 权益" />
            <div v-else class="entitlement-list">
              <div v-for="item in entitlements" :key="item.entitlementId" class="entitlement-card">
                <div>
                  <h3>{{ item.modelName }}</h3>
                  <p>{{ item.apiKeyName }}</p>
                </div>
                <el-progress
                  :percentage="Math.min(100, Math.round((item.quotaUsed / item.quotaTotal) * 100))"
                  :stroke-width="8"
                />
                <div class="entitlement-meta">
                  <span>{{ item.quotaUsed }} / {{ item.quotaTotal }} 次</span>
                  <span>到期：{{ item.expireTime }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head">
              <div>
                <h2>API Key</h2>
                <p>来源：GET /api/open/keys。</p>
              </div>
            </div>
            <el-empty v-if="apiKeys.length === 0" description="暂无 API Key" />
            <div v-else class="key-list">
              <div v-for="item in apiKeys" :key="item.apiKeyId" class="key-item">
                <div>
                  <strong>{{ item.keyName }}</strong>
                  <small>{{ item.apiKeyPrefix || item.apiKey || '未返回前缀' }}</small>
                </div>
                <el-tag :type="apiKeyStatusType(item.status)" effect="light">{{ apiKeyStatusLabel(item.status) }}</el-tag>
              </div>
            </div>
          </section>
        </div>

        <div class="profile-layout">
          <section class="panel">
            <div class="panel-head">
              <div>
                <h2>钱包</h2>
                <p>钱包余额和消费流水来自开放平台钱包接口。</p>
              </div>
              <el-tag type="success" effect="light">已接入</el-tag>
            </div>
            <div v-if="wallet" class="wallet-grid">
              <div>
                <span>可用余额</span>
                <strong>￥{{ wallet.balance.toFixed(2) }}</strong>
              </div>
              <div>
                <span>冻结余额</span>
                <strong>￥{{ wallet.frozenBalance.toFixed(2) }}</strong>
              </div>
              <div>
                <span>本月消费</span>
                <strong>￥{{ wallet.monthlyCost.toFixed(2) }}</strong>
              </div>
            </div>
            <div class="record-list">
              <div v-for="item in walletRecords" :key="item.recordId" class="record-item">
                <div>
                  <strong>{{ item.title }}</strong>
                  <small>{{ recordTypeLabel(item.type) }} · {{ item.createdAt }}</small>
                </div>
                <span :class="{ income: item.amount > 0 }">{{ item.amount > 0 ? '+' : '' }}￥{{ item.amount }}</span>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head">
              <div>
                <h2>第三方绑定</h2>
                <p>来源：GET /api/user/oauth-accounts，解绑使用 DELETE /api/user/oauth-accounts/{oauthId}。</p>
              </div>
            </div>
            <el-empty v-if="oauthAccounts.length === 0" description="暂无第三方绑定" />
            <div v-else class="oauth-list">
              <div v-for="item in oauthAccounts" :key="item.oauthId" class="oauth-item">
                <div>
                  <strong>{{ item.provider }}</strong>
                  <small>{{ item.nickname || item.providerUserId }} · {{ item.bindTime || '-' }}</small>
                </div>
                <el-button
                  size="small"
                  text
                  type="danger"
                  :loading="actionLoadingId === item.oauthId"
                  @click="unbindOAuth(item.oauthId, item.provider)"
                >
                  解绑
                </el-button>
              </div>
            </div>
            <el-alert
              class="placeholder-alert"
              title="第三方发起绑定需要 OAuth 授权跳转，本页只展示绑定状态和解绑操作。"
              type="info"
              show-icon
              :closable="false"
            />
          </section>
        </div>
      </template>

      <el-empty v-else-if="!loading && !loadError" description="暂无个人资料" />
    </div>
  </section>
</template>

<style scoped>
.profile-page,
.profile-content,
.entitlement-list,
.key-list,
.record-list,
.oauth-list,
.security-list {
  display: grid;
  gap: 18px;
}

.page-heading,
.heading-actions,
.hero-panel,
.hero-meta,
.panel-head,
.form-actions,
.key-item,
.record-item,
.oauth-item,
.entitlement-meta {
  display: flex;
  align-items: center;
  gap: 14px;
}

.page-heading,
.hero-panel,
.panel-head,
.key-item,
.record-item,
.oauth-item,
.entitlement-meta {
  justify-content: space-between;
}

.page-heading h1,
.hero-panel h2,
.panel-head h2,
.entitlement-card h3 {
  margin: 0;
  color: #10274c;
}

.page-heading h1 {
  font-size: 28px;
}

.hero-panel h2 {
  font-size: 24px;
}

.panel-head h2 {
  font-size: 18px;
}

.page-heading p,
.hero-panel p,
.panel-head p,
.entitlement-card p,
.security-list small,
.key-item small,
.record-item small,
.oauth-item small,
.entitlement-meta {
  margin: 6px 0 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.hero-panel,
.overview-card,
.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.hero-panel {
  padding: 20px;
}

.avatar-block {
  display: flex;
  align-items: center;
  gap: 16px;
}

.hero-meta {
  flex-wrap: wrap;
}

.hero-meta div,
.wallet-grid div,
.security-list div {
  min-width: 150px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fbff;
}

.hero-meta span,
.overview-card span,
.wallet-grid span,
.security-list span {
  color: var(--color-muted);
  font-size: 13px;
}

.hero-meta strong,
.wallet-grid strong,
.security-list strong {
  display: block;
  margin-top: 6px;
  color: #10274c;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.overview-card {
  min-height: 112px;
  padding: 18px;
}

.overview-card strong {
  display: block;
  margin: 10px 0 6px;
  color: #10274c;
  font-size: 28px;
  line-height: 1;
}

.overview-card small {
  color: var(--color-muted);
}

.profile-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}

.panel {
  min-width: 0;
  padding: 18px;
}

.form-actions {
  justify-content: flex-end;
}

.security-list {
  margin-top: 18px;
}

.face-auth-card {
  display: grid;
  gap: 12px;
}

.face-auth-top,
.face-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.face-camera-panel {
  display: grid;
  gap: 10px;
}

.face-camera-panel video {
  width: 100%;
  height: 220px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f7fbff;
  object-fit: cover;
}

.face-camera-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.face-preview {
  display: grid;
  grid-template-columns: 96px auto;
  align-items: center;
  gap: 12px;
}

.face-preview img {
  width: 96px;
  height: 96px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  object-fit: cover;
}

.face-file-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 15px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  color: #10274c;
  background: #ffffff;
  font-size: 14px;
  cursor: pointer;
}

.face-file-button input {
  display: none;
}

.entitlement-card,
.key-item,
.record-item,
.oauth-item {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f8fbff;
}

.entitlement-card {
  display: grid;
  gap: 12px;
}

.key-item strong,
.record-item strong,
.oauth-item strong {
  display: block;
  color: #10274c;
}

.wallet-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.record-item span {
  color: var(--color-danger);
  font-weight: 700;
}

.record-item span.income {
  color: var(--color-success);
}

.placeholder-alert {
  margin-top: 14px;
}

@media (max-width: 1180px) {
  .overview-grid,
  .profile-layout,
  .wallet-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-panel {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .page-heading,
  .heading-actions,
  .panel-head,
  .overview-grid,
  .profile-layout,
  .wallet-grid {
    align-items: flex-start;
    grid-template-columns: 1fr;
  }

  .page-heading,
  .heading-actions,
  .panel-head,
  .key-item,
  .record-item,
  .oauth-item,
  .face-auth-top,
  .face-actions {
    flex-direction: column;
  }
}
</style>
