function request(options) {
  const app = getApp()
  return new Promise((resolve, reject) => {
    wx.request({
      ...options,
      url: `${app.globalData.apiBaseUrl}${options.url}`,
      header: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${wx.getStorageSync('token') || ''}`,
        ...(options.header || {})
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) resolve(response.data)
        else reject(response)
      },
      fail: reject
    })
  })
}

module.exports = { request }
