const { get, post, put, remove, upload } = require('./request')

const authApi = {
  login: (data) => post('/api/auth/login', data),
  profile: () => get('/api/user/profile')
}

const userApi = {
  profile: () => get('/api/user/profile'),
  updateProfile: (data) => put('/api/user/profile', data),
  sendEmailChangeCode: (data) => post('/api/user/security/email/code', data),
  confirmEmailChange: (data) => put('/api/user/security/email', data),
  changePassword: (data) => put('/api/user/password', data),
  uploadAvatar: (filePath) => upload('/api/user/avatar', filePath),
  deleteAvatar: () => remove('/api/user/avatar')
}

const openApi = {
  keys: () => get('/api/open/keys'),
  createKey: (data) => post('/api/open/apply-key', data),
  setKeyStatus: (id, status) => put(`/api/open/keys/${id}/status`, { status }),
  renameKey: (id, keyName) => put(`/api/open/keys/${id}/name`, { keyName }),
  resetKey: (id) => post(`/api/open/keys/${id}/reset`),
  deleteKey: (id) => remove(`/api/open/keys/${id}`),
  entitlements: () => get('/api/open/entitlements'),
  wallet: () => get('/api/open/wallet'),
  recharge: (data) => post('/api/open/wallet/recharge', data),
  usageSummary: (params) => get('/api/open/usage/summary', params),
  usageTrend: (params) => get('/api/open/usage/trend', params),
  callLogs: (params) => get('/api/open/call-logs', params)
}

const modelApi = {
  list: (params) => get('/api/models', params),
  detail: (id) => get(`/api/models/${id}`)
}

const miniappModelApi = {
  list: (params) => get('/api/miniapp/models', params),
  detail: (id) => get(`/api/miniapp/models/${id}`)
}

const newsApi = {
  list: (params) => get('/api/news', params),
  detail: (id) => get(`/api/news/${id}`),
  notifications: (params) => get('/api/notifications', params),
  unreadCount: () => get('/api/notifications/unread-count'),
  markRead: (id) => put(`/api/notifications/${id}/read`),
  markAllRead: () => put('/api/notifications/read-all')
}

module.exports = { authApi, userApi, openApi, modelApi, miniappModelApi, newsApi }
