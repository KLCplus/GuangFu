const { modelApi } = require('../../utils/api')
const { errorMessage } = require('../../utils/format')

Page({
  data: { loading: true, error: '', model: null },
  onLoad(options) { this.modelId = Number(options.id); this.loadDetail() },
  async loadDetail() {
    if (!this.modelId) { this.setData({ loading: false, error: '模型 ID 不合法' }); return }
    this.setData({ loading: true, error: '' })
    try {
      const item = await modelApi.detail(this.modelId)
      const lists = [
        { title: '核心能力', values: item.capabilities || [] },
        { title: '适用场景', values: item.applicableScenarios || [] },
        { title: '优势', values: item.advantages || [] },
        { title: '局限', values: item.limitations || [] }
      ].filter((group) => group.values.length)
      const configs = [
        ['输入窗口', item.inputWindowMinutes, '分钟'], ['输入间隔', item.inputFrameIntervalSeconds, '秒'],
        ['输出步数', item.outputSteps, '步'], ['输出步长', item.outputStepMinutes, '分钟']
      ].filter((row) => row[1] !== undefined && row[1] !== null).map((row) => ({ label: row[0], value: `${row[1]} ${row[2]}` }))
      this.setData({
        model: { ...item, descriptionText: item.description || item.shortDescription || '暂无详细说明', tags: item.tags || [], lists, configs, statusLabel: this.statusLabel(item.status), online: String(item.status).toUpperCase() === 'ONLINE' },
        loading: false
      })
      wx.setNavigationBarTitle({ title: item.modelName || '模型详情' })
    } catch (error) { this.setData({ loading: false, error: errorMessage(error, '模型详情加载失败') }) }
  },
  statusLabel(status) { return ({ ONLINE: '已上线', TESTING: '测试中', OFFLINE: '仅展示' })[String(status || '').toUpperCase()] || status || '状态未知' },
  showActionStatus() { wx.showModal({ title: '功能说明', content: '模型购买暂未开放。页面不会模拟购买或试用成功；后续以真实业务接口为准。', showCancel: false }) }
})
