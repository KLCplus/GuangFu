const { modelApi, miniappModelApi } = require('../../utils/api')
const { errorMessage } = require('../../utils/format')
const { getModelIcon } = require('../../utils/model-icons')

Page({
  data: {
    loggedIn: false,
    loading: true,
    error: '',
    models: [],
    visibleModels: [],
    keyword: '',
    types: [{ label: '全部', value: 'ALL' }],
    activeType: 'ALL',
    status: 'ALL',
    filterOpen: false
  },

  onShow() {
    const loggedIn = Boolean(wx.getStorageSync('token'))
    const accountChanged = loggedIn !== this.data.loggedIn
    this.setData(accountChanged
      ? { loggedIn, activeType: 'ALL', status: 'ALL', filterOpen: false }
      : { loggedIn })
    this.loadModels()
  },
  onPullDownRefresh() { this.loadModels().finally(() => wx.stopPullDownRefresh()) },

  async loadModels() {
    this.setData({ loading: true, error: '' })
    try {
      const api = this.data.loggedIn ? modelApi : miniappModelApi
      const models = (await api.list() || []).map((item) => this.mapModel(item))
      const seen = new Set()
      const types = [{ label: '全部', value: 'ALL' }]
      models.forEach((item) => {
        if (item.modelType && !seen.has(item.modelType)) {
          seen.add(item.modelType)
          types.push({ label: item.typeLabel, value: item.modelType })
        }
      })
      this.setData({ models, types, loading: false })
      this.applyFilters()
    } catch (error) {
      this.setData({ models: [], visibleModels: [], loading: false, error: errorMessage(error, '模型列表加载失败') })
    }
  },

  mapModel(item) {
    const tags = Array.isArray(item.tags) ? item.tags.filter(Boolean) : []
    return {
      ...item,
      tags: tags.slice(0, 4),
      typeLabel: this.typeLabel(item.modelType),
      statusLabel: this.statusLabel(item.status),
      online: String(item.status).toUpperCase() === 'ONLINE',
      descriptionText: item.shortDescription || item.description || '该模型暂未补充简介',
      searchText: [item.modelName, item.modelCode, item.description, item.shortDescription, item.modelFamily, item.provider, ...tags].filter(Boolean).join(' ').toLowerCase(),
      iconUrl: getModelIcon(item.modelCode)
    }
  },

  typeLabel(type) {
    const labels = { NUMERIC: '数值预测', IMAGE: '图像模型', FUSION: '融合模型', MULTIMODAL: '多模态', IMAGE_TO_NUMERIC: '图像转数值' }
    return labels[type] || type || '未分类'
  },

  statusLabel(status) {
    const labels = { ONLINE: '已上线', TESTING: '测试中', OFFLINE: '仅展示' }
    return labels[String(status || '').toUpperCase()] || status || '状态未知'
  },

  onSearch(event) { this.setData({ keyword: event.detail.value }); this.applyFilters() },
  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },
  chooseType(event) { this.setData({ activeType: event.currentTarget.dataset.value }); this.applyFilters() },
  toggleFilter() { this.setData({ filterOpen: !this.data.filterOpen }) },
  chooseStatus(event) { this.setData({ status: event.currentTarget.dataset.value, filterOpen: false }); this.applyFilters() },
  clearFilters() { this.setData({ keyword: '', activeType: 'ALL', status: 'ALL', filterOpen: false }); this.applyFilters() },

  applyFilters() {
    const keyword = this.data.keyword.trim().toLowerCase()
    const visibleModels = this.data.models.filter((item) => {
      const matchesKeyword = !keyword || item.searchText.includes(keyword)
      const matchesType = this.data.activeType === 'ALL' || item.modelType === this.data.activeType
      const matchesStatus = this.data.status === 'ALL' || String(item.status).toUpperCase() === this.data.status
      return matchesKeyword && matchesType && matchesStatus
    })
    this.setData({ visibleModels })
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/model-detail/model-detail?id=${event.currentTarget.dataset.id}` })
  }
})
