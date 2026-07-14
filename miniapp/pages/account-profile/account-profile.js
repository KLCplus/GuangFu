const { userApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { errorMessage, resourceUrl } = require('../../utils/format')

const GENDERS = ['未设置', '男', '女']

Page({
  data: {
    loading: true,
    saving: false,
    avatarSaving: false,
    error: '',
    profile: null,
    avatarFailed: false,
    genderOptions: GENDERS,
    form: { nickname: '', email: '', phone: '', gender: 0 }
  },

  onLoad() {
    if (!isLoggedIn()) {
      wx.switchTab({ url: '/pages/profile/profile' })
      return
    }
    this.loadProfile()
  },

  onPullDownRefresh() { this.loadProfile().finally(() => wx.stopPullDownRefresh()) },

  async loadProfile() {
    this.setData({ loading: true, error: '' })
    try {
      const profile = await userApi.profile()
      this.applyProfile(profile)
    } catch (error) {
      this.setData({ error: errorMessage(error, '个人资料加载失败') })
    } finally {
      this.setData({ loading: false })
    }
  },

  applyProfile(profile) {
    const displayName = profile.nickname || profile.username || '平台用户'
    const mapped = { ...profile, displayName, avatarText: displayName.slice(0, 1).toUpperCase(), avatarUrl: resourceUrl(profile.avatarUrl) }
    wx.setStorageSync('userInfo', profile)
    this.setData({
      profile: mapped,
      avatarFailed: false,
      form: {
        nickname: profile.nickname || '',
        email: profile.email || '',
        phone: profile.phone || '',
        gender: Number(profile.gender || 0)
      }
    })
  },

  onNickname(event) { this.setData({ 'form.nickname': event.detail.value }) },
  onEmail(event) { this.setData({ 'form.email': event.detail.value }) },
  onPhone(event) { this.setData({ 'form.phone': event.detail.value }) },
  onGender(event) { this.setData({ 'form.gender': Number(event.detail.value) }) },
  onAvatarError() { this.setData({ avatarFailed: true }) },

  async save() {
    const nickname = this.data.form.nickname.trim()
    const email = this.data.form.email.trim()
    const phone = this.data.form.phone.trim()
    if (!nickname) return wx.showToast({ title: '请输入昵称', icon: 'none' })
    if (nickname.length > 32) return wx.showToast({ title: '昵称不能超过 32 个字符', icon: 'none' })
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return wx.showToast({ title: '邮箱格式不正确', icon: 'none' })
    if (phone.length > 20) return wx.showToast({ title: '手机号不能超过 20 个字符', icon: 'none' })

    this.setData({ saving: true })
    try {
      const profile = await userApi.updateProfile({ nickname, email, phone, gender: this.data.form.gender })
      this.applyProfile(profile)
      wx.setStorageSync('accountProfileDirty', true)
      wx.showToast({ title: '资料已保存', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '保存失败'), icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  },

  chooseAvatar() {
    if (this.data.avatarSaving) return
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: ({ tempFiles }) => {
        const file = tempFiles && tempFiles[0]
        if (!file) return
        if (Number(file.size || 0) > 2 * 1024 * 1024) {
          wx.showToast({ title: '图片不能超过 2MB', icon: 'none' })
          return
        }
        this.uploadAvatar(file.tempFilePath)
      }
    })
  },

  async uploadAvatar(filePath) {
    this.setData({ avatarSaving: true })
    try {
      await userApi.uploadAvatar(filePath)
      await this.loadProfile()
      wx.setStorageSync('accountProfileDirty', true)
      wx.showToast({ title: '头像已更新', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '头像上传失败'), icon: 'none' })
    } finally {
      this.setData({ avatarSaving: false })
    }
  },

  deleteAvatar() {
    if (!this.data.profile || !this.data.profile.avatarUrl || this.data.avatarSaving) return
    wx.showModal({
      title: '删除头像',
      content: '删除后将使用昵称首字母作为头像。',
      confirmText: '删除',
      confirmColor: '#df4b5f',
      success: async ({ confirm }) => {
        if (!confirm) return
        this.setData({ avatarSaving: true })
        try {
          await userApi.deleteAvatar()
          await this.loadProfile()
          wx.setStorageSync('accountProfileDirty', true)
          wx.showToast({ title: '头像已删除', icon: 'success' })
        } catch (error) {
          wx.showToast({ title: errorMessage(error, '删除失败'), icon: 'none' })
        } finally {
          this.setData({ avatarSaving: false })
        }
      }
    })
  }
})
