const { openApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { money, dateTime, errorMessage } = require('../../utils/format')

Page({
  data: {
    loading: true,
    recharging: false,
    error: '',
    wallet: null,
    records: [],
    presetAmounts: [50, 100, 200, 500],
    selectedAmount: 100,
    customAmount: ''
  },

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
    return ({ RECHARGE: '测试充值', CONSUME: 'API 消费', REFUND: '退款' })[type] || type || '资金变动'
  },

  chooseAmount(event) {
    this.setData({ selectedAmount: Number(event.currentTarget.dataset.amount), customAmount: '' })
  },

  onCustomAmount(event) {
    this.setData({ customAmount: event.detail.value, selectedAmount: 0 })
  },

  confirmRecharge() {
    if (this.data.recharging) return
    const amount = Number(this.data.customAmount || this.data.selectedAmount)
    if (!Number.isFinite(amount) || amount < 1 || amount > 100000) {
      wx.showToast({ title: '金额应为 1–100000 元', icon: 'none' })
      return
    }
    wx.showModal({
      title: '确认测试充值',
      content: `将测试入账 ¥${amount.toFixed(2)}，不会产生真实扣款。`,
      confirmText: '确认入账',
      success: ({ confirm }) => { if (confirm) this.submitRecharge(amount) }
    })
  },

  async submitRecharge(amount) {
    this.setData({ recharging: true })
    try {
      await openApi.recharge({ amount, channel: 'MOCK' })
      this.setData({ customAmount: '', selectedAmount: amount })
      await this.loadWallet()
      wx.showToast({ title: '测试充值成功', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '测试充值失败'), icon: 'none' })
    } finally {
      this.setData({ recharging: false })
    }
  }
})
