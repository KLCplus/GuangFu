const { openApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { money, dateTime, errorMessage } = require('../../utils/format')

Page({
  data: { loading: true, error: '', wallet: null, records: [] },

  onLoad() {
    if (!isLoggedIn()) {
      wx.switchTab({ url: '/pages/profile/profile' })
      return
    }
    this.loadWallet()
  },

  onPullDownRefresh() { this.loadWallet().finally(() => wx.stopPullDownRefresh()) },

  async loadWallet() {
    this.setData({ loading: true, error: '' })
    try {
      const wallet = await openApi.wallet()
      const currency = wallet.currency || 'CNY'
      const records = (wallet.records || []).map((item) => {
        const amount = Number(item.amount)
        const income = Number.isFinite(amount) && amount > 0
        return {
          ...item,
          titleText: item.title || this.typeLabel(item.type),
          typeText: this.typeLabel(item.type),
          timeText: dateTime(item.createdAt),
          amountText: `${income ? '+' : ''}${money(item.amount, currency)}`,
          balanceText: item.balanceAfter == null ? '' : money(item.balanceAfter, currency),
          income
        }
      })
      this.setData({
        wallet: {
          ...wallet,
          balanceText: money(wallet.balance, currency),
          frozenText: money(wallet.frozenBalance, currency),
          monthlyCostText: money(wallet.monthlyCost, currency)
        },
        records
      })
    } catch (error) {
      this.setData({ wallet: null, records: [], error: errorMessage(error, '钱包加载失败') })
    } finally {
      this.setData({ loading: false })
    }
  },

  typeLabel(type) {
    return ({ RECHARGE: '账户充值', CONSUME: 'API 消费', REFUND: '退款' })[type] || type || '资金变动'
  },

  showRechargeStatus() {
    wx.showModal({ title: '充值暂未开放', content: '当前后端仅提供本地 MOCK 模拟入账，不是微信支付流程，因此小程序不发起充值。', showCancel: false })
  }
})
