const { openApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { number, dateTime, errorMessage, maskApiKey, percent } = require('../../utils/format')

Page({
  data: {
    loading: true,
    errors: {},
    keys: [],
    entitlements: [],
    stats: { keyCount: '--', enabledCount: '--', entitlementCount: '--' }
  },

  onLoad() {
    if (!isLoggedIn()) {
      wx.switchTab({ url: '/pages/profile/profile' })
      return
    }
    this.loadData()
  },

  onPullDownRefresh() { this.loadData().finally(() => wx.stopPullDownRefresh()) },

  async loadData() {
    this.setData({ loading: true, errors: {} })
    const [keyResult, entitlementResult] = await Promise.allSettled([openApi.keys(), openApi.entitlements()])
    const errors = {}
    const update = { loading: false }
    const stats = { keyCount: '--', enabledCount: '--', entitlementCount: '--' }

    if (keyResult.status === 'fulfilled') {
      const keys = (keyResult.value || []).map((item) => ({
        ...item,
        maskedKey: maskApiKey(item),
        active: String(item.status).toUpperCase() === 'ACTIVE',
        statusText: String(item.status).toUpperCase() === 'ACTIVE' ? '已启用' : String(item.status).toUpperCase() === 'EXPIRED' ? '已过期' : '已停用',
        expireText: dateTime(item.expireTime || item.expireAt, '长期有效')
      }))
      update.keys = keys
      stats.keyCount = String(keys.length)
      stats.enabledCount = String(keys.filter((item) => item.active).length)
    } else {
      update.keys = []
      errors.keys = errorMessage(keyResult.reason, 'API Key 加载失败')
    }

    if (entitlementResult.status === 'fulfilled') {
      const entitlements = (entitlementResult.value || []).map((item) => {
        const quotaTotal = Number(item.quotaTotal)
        const quotaUsed = Number(item.quotaUsed)
        return {
          ...item,
          progress: percent(quotaUsed, quotaTotal),
          quotaText: `${number(quotaUsed, '0')} / ${Number.isFinite(quotaTotal) && quotaTotal > 0 ? number(quotaTotal) : '未设上限'}`,
          expireText: dateTime(item.expireTime, '长期有效'),
          statusText: String(item.status).toUpperCase() === 'ACTIVE' ? '生效中' : String(item.status).toUpperCase() === 'EXPIRED' ? '已过期' : '不可用',
          active: String(item.status).toUpperCase() === 'ACTIVE'
        }
      })
      update.entitlements = entitlements
      stats.entitlementCount = String(entitlements.length)
    } else {
      update.entitlements = []
      errors.entitlements = errorMessage(entitlementResult.reason, 'API 权益加载失败')
    }

    this.setData({ ...update, stats, errors })
  }
})
