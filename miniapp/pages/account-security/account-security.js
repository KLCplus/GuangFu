const { userApi } = require('../../utils/api')
const { isLoggedIn, clearAuth } = require('../../utils/request')
const { errorMessage } = require('../../utils/format')

Page({
  data: {
    loading: true,
    saving: false,
    error: '',
    profile: null,
    form: { oldPassword: '', newPassword: '', confirmPassword: '' }
  },

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
