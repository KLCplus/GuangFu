const { userApi, openApi } = require('../../utils/api')
const { isLoggedIn, clearAuth } = require('../../utils/request')
const { money, errorMessage, resourceUrl } = require('../../utils/format')

const REFRESH_INTERVAL = 20000

Page({
  data: {
    loggedIn: false,
    loading: false,
    user: null,
    avatarFailed: false,
    metrics: { balance: '--', keyCount: '--', entitlementCount: '--' },
    metricErrors: {},
    profileError: ''
  },

  onShow() {
    const loggedIn = isLoggedIn()
    if (!loggedIn) {
      this.lastLoadedAt = 0
      this.setData({ loggedIn: false, loading: false, user: null, profileError: '', metricErrors: {} })
      return
    }
    const cached = wx.getStorageSync('userInfo') || null
    this.setData({ loggedIn: true, user: this.mapUser(cached), avatarFailed: false })
    const dirty = wx.getStorageSync('accountProfileDirty')
    if (dirty || !this.lastLoadedAt || Date.now() - this.lastLoadedAt > REFRESH_INTERVAL) {
      wx.removeStorageSync('accountProfileDirty')
      this.loadAccount()
    }
  },

  onPullDownRefresh() {
    if (!isLoggedIn()) {
      wx.stopPullDownRefresh()
      return
    }
    this.loadAccount().finally(() => wx.stopPullDownRefresh())
  },

  async loadAccount() {
    this.setData({ loading: true, profileError: '', metricErrors: {} })
    const results = await Promise.allSettled([
      userApi.profile(),
      openApi.wallet(),
      openApi.keys(),
      openApi.entitlements()
    ])
    if (!isLoggedIn()) {
      this.setData({ loggedIn: false, loading: false, user: null })
      return
    }

    const metricErrors = {}
    const metrics = { balance: '--', keyCount: '--', entitlementCount: '--' }
    let profileError = ''

    if (results[0].status === 'fulfilled') {
      wx.setStorageSync('userInfo', results[0].value)
      this.setData({ user: this.mapUser(results[0].value), avatarFailed: false })
    } else {
      profileError = errorMessage(results[0].reason, '用户资料加载失败')
    }
    if (results[1].status === 'fulfilled') metrics.balance = money(results[1].value && results[1].value.balance, results[1].value && results[1].value.currency)
    else metricErrors.balance = errorMessage(results[1].reason, '余额加载失败')
    if (results[2].status === 'fulfilled') metrics.keyCount = String((results[2].value || []).length)
    else metricErrors.keys = errorMessage(results[2].reason, 'API Key 加载失败')
    if (results[3].status === 'fulfilled') metrics.entitlementCount = String((results[3].value || []).length)
    else metricErrors.entitlements = errorMessage(results[3].reason, 'API 权益加载失败')

    this.lastLoadedAt = Date.now()
    this.setData({ loading: false, metrics, metricErrors, profileError })
  },

  mapUser(user) {
    if (!user) return null
    const displayName = user.nickname || user.username || '平台用户'
    return {
      ...user,
      displayName,
      avatarText: displayName.slice(0, 1).toUpperCase(),
      avatarUrl: resourceUrl(user.avatarUrl),
      secondaryText: user.email || `@${user.username}`,
      statusText: Number(user.status) === 1 ? '账户正常' : '账户受限',
      statusNormal: Number(user.status) === 1,
      emailStatus: user.email ? '邮箱已绑定' : '邮箱未绑定'
    }
  },

  onAvatarError() { this.setData({ avatarFailed: true }) },
  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },
  goProfile() { wx.navigateTo({ url: '/pages/account-profile/account-profile' }) },
  goSecurity() { wx.navigateTo({ url: '/pages/account-security/account-security' }) },
  goWallet() { wx.navigateTo({ url: '/pages/account-wallet/account-wallet' }) },
  goEntitlements() { wx.navigateTo({ url: '/pages/account-entitlements/account-entitlements' }) },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '将清除本机保存的登录状态，不会删除平台账号。',
      confirmText: '退出',
      confirmColor: '#df4b5f',
      success: (result) => {
        if (!result.confirm) return
        clearAuth()
        this.lastLoadedAt = 0
        this.setData({ loggedIn: false, user: null, profileError: '', metricErrors: {}, metrics: { balance: '--', keyCount: '--', entitlementCount: '--' } })
      }
    })
  }
})
