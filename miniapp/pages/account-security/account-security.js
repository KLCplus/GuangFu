const { userApi } = require('../../utils/api')
const { isLoggedIn, clearAuth } = require('../../utils/request')
const { errorMessage } = require('../../utils/format')

Page({
  data: {
    loading: true,
    saving: false,
    emailSending: false,
    emailConfirming: false,
    emailCodeSent: false,
    emailCountdown: 0,
    error: '',
    profile: null,
    form: { oldPassword: '', newPassword: '', confirmPassword: '' },
    emailForm: { newEmail: '', code: '', currentPassword: '' }
  },

  onUnload() { this.stopEmailCountdown() },

  onLoad() {
    if (!isLoggedIn()) {
      wx.switchTab({ url: '/pages/profile/profile' })
      return
    }
    this.loadProfile()
  },

  async loadProfile() {
    this.setData({ loading: true, error: '' })
    try {
      const profile = await userApi.profile()
      this.setData({ profile })
    } catch (error) {
      this.setData({ error: errorMessage(error, '安全信息加载失败') })
    } finally {
      this.setData({ loading: false })
    }
  },

  onOldPassword(event) { this.setData({ 'form.oldPassword': event.detail.value }) },
  onNewPassword(event) { this.setData({ 'form.newPassword': event.detail.value }) },
  onConfirmPassword(event) { this.setData({ 'form.confirmPassword': event.detail.value }) },
  onNewEmail(event) { this.setData({ 'emailForm.newEmail': event.detail.value }) },
  onEmailCode(event) { this.setData({ 'emailForm.code': event.detail.value }) },
  onEmailPassword(event) { this.setData({ 'emailForm.currentPassword': event.detail.value }) },

  stopEmailCountdown() {
    if (this.emailTimer) clearInterval(this.emailTimer)
    this.emailTimer = null
    this.setData({ emailCountdown: 0 })
  },

  startEmailCountdown() {
    this.stopEmailCountdown()
    this.setData({ emailCountdown: 60 })
    this.emailTimer = setInterval(() => {
      const next = this.data.emailCountdown - 1
      this.setData({ emailCountdown: Math.max(0, next) })
      if (next <= 0) this.stopEmailCountdown()
    }, 1000)
  },

  async sendEmailCode() {
    const newEmail = this.data.emailForm.newEmail.trim().toLowerCase()
    const currentPassword = this.data.emailForm.currentPassword
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(newEmail)) return wx.showToast({ title: '新邮箱格式不正确', icon: 'none' })
    if (!currentPassword) return wx.showToast({ title: '请输入当前密码', icon: 'none' })
    this.setData({ emailSending: true })
    try {
      await userApi.sendEmailChangeCode({ newEmail, currentPassword })
      this.setData({ emailCodeSent: true, 'emailForm.newEmail': newEmail })
      this.startEmailCountdown()
      wx.showToast({ title: '验证码已发送', icon: 'success' })
    } catch (error) {
      this.setData({ emailCodeSent: false })
      wx.showToast({ title: errorMessage(error, '验证码发送失败'), icon: 'none' })
    } finally {
      this.setData({ emailSending: false })
    }
  },

  async confirmEmailChange() {
    const { newEmail, code, currentPassword } = this.data.emailForm
    if (!newEmail || !code.trim() || !currentPassword) return wx.showToast({ title: '请完整填写换绑信息', icon: 'none' })
    this.setData({ emailConfirming: true })
    try {
      await userApi.confirmEmailChange({ newEmail, code: code.trim(), currentPassword })
      await this.loadProfile()
      this.stopEmailCountdown()
      this.setData({ emailCodeSent: false, emailForm: { newEmail: '', code: '', currentPassword: '' } })
      wx.showToast({ title: '邮箱已验证并更新', icon: 'success' })
    } catch (error) {
      await this.loadProfile()
      wx.showToast({ title: errorMessage(error, '邮箱修改失败'), icon: 'none' })
    } finally {
      this.setData({ emailConfirming: false })
    }
  },

  async changePassword() {
    const { oldPassword, newPassword, confirmPassword } = this.data.form
    if (!oldPassword || !newPassword || !confirmPassword) return wx.showToast({ title: '请完整填写密码', icon: 'none' })
    if (newPassword.length < 8 || newPassword.length > 64) return wx.showToast({ title: '新密码长度应为 8–64 位', icon: 'none' })
    if (newPassword !== confirmPassword) return wx.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    if (oldPassword === newPassword) return wx.showToast({ title: '新旧密码不能相同', icon: 'none' })

    this.setData({ saving: true })
    try {
      await userApi.changePassword({ oldPassword, newPassword })
      clearAuth()
      await wx.showModal({ title: '密码已修改', content: '为保护账户安全，请使用新密码重新登录。', showCancel: false, confirmText: '重新登录' })
      wx.switchTab({ url: '/pages/profile/profile' })
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '密码修改失败'), icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  }
})
