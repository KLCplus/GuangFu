const { get, post, put, remove } = require('./request')

const authApi = {
  login: (data) => post('/api/auth/login', data),
  profile: () => get('/api/user/profile')
}

const openApi = {
  keys: () => get('/api/open/keys'),
  createKey: (data) => post('/api/open/apply-key', data),
  setKeyStatus: (id, status) => put(`/api/open/keys/${id}/status`, { status }),
  renameKey: (id, keyName) => put(`/api/open/keys/${id}/name`, { keyName }),
  resetKey: (id) => post(`/api/open/keys/${id}/reset`),
  deleteKey: (id) => remove(`/api/open/keys/${id}`),
  wallet: () => get('/api/open/wallet'),
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

module.exports = { authApi, openApi, modelApi, miniappModelApi, newsApi }
