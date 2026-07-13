const { authApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { errorMessage } = require('../../utils/format')

Page({
  data: { loggedIn: false, loading: false, user: null, error: '', apiBaseUrl: '' },
  onShow() {
    const loggedIn = isLoggedIn()
    const cached = wx.getStorageSync('userInfo') || null
    this.setData({ loggedIn, user: this.mapUser(cached), apiBaseUrl: getApp().globalData.apiBaseUrl })
    if (loggedIn) this.loadProfile()
  },
  async loadProfile() {
    this.setData({ loading: true, error: '' })
    try {
      const user = await authApi.profile()
      wx.setStorageSync('userInfo', user)
      this.setData({ user: this.mapUser(user) })
    } catch (error) { this.setData({ error: errorMessage(error, '账号信息加载失败') }) }
    finally { this.setData({ loading: false }) }
  },
  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },
  mapUser(user) {
    if (!user) return null
    const displayName = user.nickname || user.username || '平台用户'
    const roles = Array.isArray(user.roles) ? user.roles : (user.role ? [user.role] : [])
    return { ...user, displayName, avatarText: displayName.slice(0, 1), roleText: roles.length ? roles.join(' · ') : '普通用户' }
  },
  logout() {
    wx.showModal({ title: '断开平台账号', content: '将清除本机保存的登录令牌，不会删除平台账号。', confirmText: '断开', confirmColor: '#df4b5f', success: (result) => {
      if (!result.confirm) return
      wx.removeStorageSync('token'); wx.removeStorageSync('refreshToken'); wx.removeStorageSync('userInfo')
      this.setData({ loggedIn: false, user: null, error: '' })
    } })
  },
  showRoadmap() { wx.showModal({ title: '功能完善中', content: 'PC 端个人中心方案尚未定稿，小程序暂不扩展个人资料编辑、密码修改等功能。', showCancel: false }) }
})
