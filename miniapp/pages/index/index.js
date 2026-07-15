const { openApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { number, money, dateTime, errorMessage } = require('../../utils/format')

const EMPTY_SUMMARY = { totalCalls: 0, successCalls: 0, failedCalls: 0, successRate: 0, avgCostTimeMs: 0, totalTokens: 0 }

Page({
  data: {
    loggedIn: false,
    refreshing: false,
    loading: true,
    wallet: null,
    summary: EMPTY_SUMMARY,
    keys: [],
    logs: [],
    trend: [],
    sectionErrors: {},
    hasAnyData: false
  },

  onShow() {
    const loggedIn = isLoggedIn()
    this.setData({ loggedIn })
    if (loggedIn) this.loadOverview()
    else this.setData({ loading: false, wallet: null, keys: [], logs: [], trend: [], sectionErrors: {} })
  },

  onPullDownRefresh() {
    if (!this.data.loggedIn) {
      wx.stopPullDownRefresh()
      return
    }
    this.loadOverview().finally(() => wx.stopPullDownRefresh())
  },

  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },

  async loadOverview() {
    this.setData({ loading: true, refreshing: true, sectionErrors: {} })
    const range = this.last30Days()
    const results = await Promise.allSettled([
      openApi.wallet(),
      openApi.keys(),
      openApi.usageSummary(range),
      openApi.usageTrend({ ...range, granularity: 'DAY' }),
      openApi.callLogs({ ...range, pageNum: 1, pageSize: 6 })
    ])
    const errors = {}
    let wallet = null
    let keys = []
    let summary = EMPTY_SUMMARY
    let trend = []
    let logs = []

    if (results[0].status === 'fulfilled') wallet = this.mapWallet(results[0].value)
    else errors.wallet = errorMessage(results[0].reason, '钱包信息加载失败')
    if (results[1].status === 'fulfilled') keys = (results[1].value || []).map((item) => this.mapKey(item))
    else errors.keys = errorMessage(results[1].reason, 'API Key 加载失败')
    if (results[2].status === 'fulfilled') summary = this.mapSummary(results[2].value || EMPTY_SUMMARY)
    else errors.summary = errorMessage(results[2].reason, '统计汇总加载失败')
    if (results[3].status === 'fulfilled') trend = this.mapTrend(results[3].value || [])
    else errors.trend = errorMessage(results[3].reason, '调用趋势加载失败')
    if (results[4].status === 'fulfilled') logs = ((results[4].value && results[4].value.records) || []).map((item) => this.mapLog(item))
    else errors.logs = errorMessage(results[4].reason, '调用记录加载失败')

    this.setData({
      wallet, keys, summary, trend, logs,
      sectionErrors: errors,
      hasAnyData: Boolean(wallet || keys.length || summary.totalCalls || trend.length || logs.length),
      loading: false,
      refreshing: false
    })
  },

  last30Days() {
    const date = new Date()
    date.setDate(date.getDate() - 29)
    date.setHours(0, 0, 0, 0)
    const pad = (value) => String(value).padStart(2, '0')
    return { startTime: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} 00:00:00` }
  },

  mapWallet(wallet) {
    return {
      ...wallet,
      balanceText: money(wallet.balance, wallet.currency),
      monthlyCostText: money(wallet.monthlyCost, wallet.currency),
      frozenText: money(wallet.frozenBalance, wallet.currency),
      records: (wallet.records || []).slice(0, 3).map((item) => ({
        ...item,
        amountText: `${Number(item.amount) >= 0 ? '+' : ''}${money(item.amount, wallet.currency)}`,
        timeText: dateTime(item.createdAt)
      }))
    }
  },

  mapSummary(summary) {
    return {
      ...summary,
      totalCallsText: number(summary.totalCalls),
      successCallsText: number(summary.successCalls),
      failedCallsText: number(summary.failedCalls),
      successRateText: `${Number(summary.successRate || 0).toFixed(1)}%`,
      avgCostText: `${number(summary.avgCostTimeMs)} ms`,
      tokenText: Number(summary.totalTokens) > 0 ? number(summary.totalTokens) : '暂未接通'
    }
  },

  mapKey(item) {
    const prefix = item.apiKeyPrefix || ''
    return {
      ...item,
      active: String(item.status).toUpperCase() === 'ACTIVE',
      maskedKey: prefix ? `${prefix}••••••••••••` : '已安全隐藏',
      createdText: dateTime(item.createdAt),
      lastUsedText: item.lastUsedAt ? dateTime(item.lastUsedAt) : '尚未调用'
    }
  },

  mapTrend(items) {
    const max = Math.max(1, ...items.map((item) => Number(item.totalCalls || 0)))
    return items.slice(-10).map((item) => ({
      ...item,
      height: Math.max(8, Math.round(Number(item.totalCalls || 0) / max * 96)),
      label: String(item.timeBucket || '').slice(5)
    }))
  },

  mapLog(item) {
    const success = String(item.status || '').toUpperCase() === 'SUCCESS' || Number(item.statusCode) < 400
    return {
      ...item,
      success,
      timeText: dateTime(item.requestTime || item.createdAt),
      modelText: item.modelName || (item.modelId ? `模型 #${item.modelId}` : '未记录模型'),
      pathText: item.path || item.requestPath || '未记录接口',
      costText: `${number(item.costTimeMs != null ? item.costTimeMs : item.costTime, '0')} ms`
    }
  },

  async createKey() {
    const result = await wx.showModal({ title: '创建 API Key', placeholderText: '例如：小程序联调', editable: true, confirmText: '创建' })
    const keyName = (result.content || '').trim()
    if (!result.confirm || !keyName) return
    try {
      const created = await openApi.createKey({ keyName, expireDays: 90 })
      await this.showOneTimeKey(created)
      await this.loadOverview()
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '创建失败'), icon: 'none' })
    }
  },

  keyAction(event) {
    const id = Number(event.currentTarget.dataset.id)
    const key = this.data.keys.find((item) => item.apiKeyId === id)
    if (!key) return
    const actions = [key.active ? '停用' : '启用', '重命名', '重新生成', '删除']
    wx.showActionSheet({
      itemList: actions,
      success: ({ tapIndex }) => this.handleKeyAction(key, tapIndex)
    })
  },

  async handleKeyAction(key, index) {
    try {
      if (index === 0) {
        await openApi.setKeyStatus(key.apiKeyId, key.active ? 'DISABLED' : 'ACTIVE')
      } else if (index === 1) {
        const result = await wx.showModal({ title: '修改名称', content: key.keyName, editable: true, confirmText: '保存' })
        const name = (result.content || '').trim()
        if (!result.confirm || !name) return
        await openApi.renameKey(key.apiKeyId, name)
      } else if (index === 2) {
        const confirmed = await wx.showModal({ title: '重新生成 Key', content: '旧 Key 将立即失效。新 Key 只展示一次。', confirmText: '重新生成', confirmColor: '#df4b5f' })
        if (!confirmed.confirm) return
        const reset = await openApi.resetKey(key.apiKeyId)
        await this.showOneTimeKey(reset)
      } else if (index === 3) {
        const confirmed = await wx.showModal({ title: '删除 API Key', content: `确认永久删除“${key.keyName}”吗？`, confirmText: '删除', confirmColor: '#df4b5f' })
        if (!confirmed.confirm) return
        await openApi.deleteKey(key.apiKeyId)
      }
      await this.loadOverview()
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '操作失败'), icon: 'none' })
    }
  },

  async showOneTimeKey(result) {
    if (!result || !result.apiKey) {
      wx.showToast({ title: '操作成功，后端未返回完整 Key', icon: 'none' })
      return
    }
    await wx.showModal({ title: '请立即保存', content: `${result.apiKey}\n\n完整 Key 关闭后不再展示。`, showCancel: false, confirmText: '我已保存' })
  },

  showRechargeStatus() {
    wx.navigateTo({ url: '/pages/account-wallet/account-wallet' })
  }
})
