const { userApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { errorMessage, resourceUrl } = require('../../utils/format')

const GENDERS = ['未设置', '男', '女']
const CALLING_CODES = ['+86', '+1', '+44', '+81', '+82']
const CALLING_CODE_LABELS = ['中国大陆 +86', '美国/加拿大 +1', '英国 +44', '日本 +81', '韩国 +82']

Page({
  data: {
    loading: true,
    saving: false,
    avatarSaving: false,
    error: '',
    profile: null,
    avatarFailed: false,
    genderOptions: GENDERS,
    callingCodeLabels: CALLING_CODE_LABELS,
    callingCodeIndex: 0,
    form: { nickname: '', phone: '', gender: 0 }
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
    const matchedIndex = CALLING_CODES.findIndex(code => (profile.phone || '').startsWith(code))
    const callingCodeIndex = matchedIndex >= 0 ? matchedIndex : 0
    this.setData({
      profile: mapped,
      avatarFailed: false,
      form: {
        nickname: profile.nickname || '',
        phone: matchedIndex >= 0 ? profile.phone.slice(CALLING_CODES[matchedIndex].length) : (profile.phone || ''),
        gender: Number(profile.gender || 0)
      },
      callingCodeIndex
    })
  },

  onNickname(event) { this.setData({ 'form.nickname': event.detail.value }) },
  onPhone(event) { this.setData({ 'form.phone': event.detail.value }) },
  onCallingCode(event) { this.setData({ callingCodeIndex: Number(event.detail.value) }) },
  onGender(event) { this.setData({ 'form.gender': Number(event.detail.value) }) },
  onAvatarError() { this.setData({ avatarFailed: true }) },

  async save() {
    const nickname = this.data.form.nickname.trim()
    const phone = this.data.form.phone.trim().replace(/[\s()-]/g, '')
    const callingCode = CALLING_CODES[this.data.callingCodeIndex]
    if (!nickname) return wx.showToast({ title: '请输入昵称', icon: 'none' })
    if (nickname.length > 32) return wx.showToast({ title: '昵称不能超过 32 个字符', icon: 'none' })
    if (phone && callingCode === '+86' && !/^1[3-9]\d{9}$/.test(phone)) return wx.showToast({ title: '大陆手机号格式不正确', icon: 'none' })
    if (phone && callingCode !== '+86' && !/^\d+$/.test(phone)) return wx.showToast({ title: '国际号码只能包含数字', icon: 'none' })
    const internationalLength = `${callingCode}${phone}`.replace('+', '').length
    if (phone && callingCode !== '+86' && (internationalLength < 8 || internationalLength > 15)) return wx.showToast({ title: '国际号码格式不正确', icon: 'none' })

    this.setData({ saving: true })
    try {
      await userApi.updateProfile({ nickname, phone: phone ? `${callingCode}${phone}` : '', gender: this.data.form.gender })
      await this.loadProfile()
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
  }
})
