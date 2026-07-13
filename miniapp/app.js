App({
  globalData: {
    // 本地联调使用本机后端；真机/体验版请在开发者工具中通过环境配置覆盖。
    apiBaseUrl: 'http://127.0.0.1:8080'
  },

  onLaunch() {
    const configuredBaseUrl = wx.getStorageSync('apiBaseUrl')
    if (configuredBaseUrl) this.globalData.apiBaseUrl = configuredBaseUrl
  }
})
