const { authApi } = require('../../utils/api')
const { errorMessage } = require('../../utils/format')

Page({
  data: {
    username: '',
    password: '',
    loading: false,
    error: ''
  },

  onUsernameInput(event) { this.setData({ username: event.detail.value, error: '' }) },
  onPasswordInput(event) { this.setData({ password: event.detail.value, error: '' }) },

  async submit() {
    const username = this.data.username.trim()
    const password = this.data.password
    if (!username || !password) {
      this.setData({ error: '请输入用户名和密码' })
      return
    }
    this.setData({ loading: true, error: '' })
    try {
      const result = await authApi.login({ username, password })
      wx.setStorageSync('token', result.token)
      if (result.refreshToken) wx.setStorageSync('refreshToken', result.refreshToken)
      if (result.userInfo) wx.setStorageSync('userInfo', result.userInfo)
      wx.showToast({ title: '已连接', icon: 'success' })
      setTimeout(() => wx.navigateBack({ delta: 1 }), 350)
    } catch (error) {
      this.setData({ error: errorMessage(error, '登录失败，请重试') })
    } finally {
      this.setData({ loading: false })
    }
  }
})
