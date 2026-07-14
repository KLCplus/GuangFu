const AUTH_EXPIRED_EVENT = 'auth-expired'

function clearAuth() {
  wx.removeStorageSync('token')
  wx.removeStorageSync('refreshToken')
  wx.removeStorageSync('userInfo')
  wx.removeStorageSync('accountOverviewCache')
}

function buildQuery(params) {
  if (!params) return ''
  const pairs = Object.keys(params)
    .filter((key) => params[key] !== undefined && params[key] !== null && params[key] !== '')
    .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}

function normalizeError(response, fallback = '请求失败') {
  const body = response && response.data
  const statusCode = response && response.statusCode
  const message = (body && body.message) || fallback
  const error = new Error(message)
  error.statusCode = statusCode
  error.code = body && body.code
  error.response = response
  return error
}

function request(options) {
  const app = getApp()
  const token = wx.getStorageSync('token')
  const query = buildQuery(options.params)
  return new Promise((resolve, reject) => {
    wx.request({
      ...options,
      url: `${app.globalData.apiBaseUrl}${options.url}${query}`,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.header || {})
      },
      success: (response) => {
        const body = response.data
        if (response.statusCode === 401) {
          clearAuth()
          wx.emit && wx.emit(AUTH_EXPIRED_EVENT)
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(normalizeError(response))
          return
        }
        if (body && typeof body.code === 'number') {
          if (body.code !== 200) {
            reject(normalizeError(response, '业务请求失败'))
            return
          }
          resolve(body.data)
          return
        }
        resolve(body)
      },
      fail: (cause) => {
        const error = new Error(cause.errMsg || '无法连接服务器')
        error.cause = cause
        reject(error)
      }
    })
  })
}

function upload(url, filePath, name = 'file') {
  const app = getApp()
  const token = wx.getStorageSync('token')
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${app.globalData.apiBaseUrl}${url}`,
      filePath,
      name,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (response) => {
        let body
        try { body = typeof response.data === 'string' ? JSON.parse(response.data) : response.data }
        catch { body = null }
        const normalized = { ...response, data: body }
        if (response.statusCode === 401) {
          clearAuth()
          wx.emit && wx.emit(AUTH_EXPIRED_EVENT)
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(normalizeError(normalized, '上传失败'))
          return
        }
        if (body && typeof body.code === 'number') {
          if (body.code !== 200) {
            reject(normalizeError(normalized, '上传失败'))
            return
          }
          resolve(body.data)
          return
        }
        resolve(body)
      },
      fail: (cause) => {
        const error = new Error(cause.errMsg || '无法连接服务器')
        error.cause = cause
        reject(error)
      }
    })
  })
}

function get(url, params) { return request({ url, params, method: 'GET' }) }
function post(url, data) { return request({ url, data, method: 'POST' }) }
function put(url, data) { return request({ url, data, method: 'PUT' }) }
function remove(url) { return request({ url, method: 'DELETE' }) }
function isLoggedIn() { return Boolean(wx.getStorageSync('token')) }

module.exports = { request, upload, get, post, put, remove, isLoggedIn, clearAuth, AUTH_EXPIRED_EVENT }
