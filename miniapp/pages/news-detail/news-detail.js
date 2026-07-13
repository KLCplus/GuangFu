const { newsApi } = require('../../utils/api')
const { dateTime, errorMessage } = require('../../utils/format')
Page({
  data: { loading: true, error: '', news: null },
  onLoad(options) { this.newsId = Number(options.id); this.loadDetail() },
  async loadDetail() {
    if (!this.newsId) { this.setData({ loading: false, error: '资讯 ID 不合法' }); return }
    this.setData({ loading: true, error: '' })
    try {
      const item = await newsApi.detail(this.newsId)
      const paragraphs = String(item.content || '').split(/\n+/).map((value) => value.trim()).filter(Boolean)
      const typeLabel = ({ NEWS: '新闻', NOTICE: '公告', MODEL_UPDATE: '模型更新', ALERT: '异常提醒', SYSTEM_NOTICE: '系统通知', INDUSTRY_NEWS: '行业资讯' })[item.newsType] || item.newsType || '资讯'
      this.setData({ news: { ...item, paragraphs, typeLabel, timeText: dateTime(item.publishedAt || item.createdAt) }, loading: false })
      wx.setNavigationBarTitle({ title: item.title || '资讯详情' })
    } catch (error) { this.setData({ loading: false, error: errorMessage(error, '资讯详情加载失败') }) }
  }
})
