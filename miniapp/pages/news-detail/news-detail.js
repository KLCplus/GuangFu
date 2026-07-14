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
      const plainContent = String(item.content || '').replace(/<br\s*\/?\s*>/gi, '\n').replace(/<\/p>/gi, '\n').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
      const paragraphs = plainContent.split(/\n+/).map((value) => value.trim()).filter(Boolean)
      const type = item.category || item.newsType
      const typeLabel = ({ WEATHER_ALERT: '气象预警', DISASTER: '灾害动态', POLICY: '政策标准', INDUSTRY: '行业动态', ENTERPRISE: '企业资讯', PLATFORM: '平台资讯', NEWS: '平台新闻', NOTICE: '平台公告' })[type] || type || '资讯'
      this.setData({ news: { ...item, paragraphs, typeLabel, sourceText: item.sourceName || '光伏智云平台', timeText: dateTime(item.sourcePublishedAt || item.publishedAt || item.createdAt), effectiveText: item.effectiveAt ? dateTime(item.effectiveAt) : '', expiresText: item.expiresAt ? dateTime(item.expiresAt) : '' }, loading: false })
      wx.setNavigationBarTitle({ title: item.title || '资讯详情' })
    } catch (error) { this.setData({ loading: false, error: errorMessage(error, '资讯详情加载失败') }) }
  },
  copyOriginal() {
    if (!this.data.news || !this.data.news.sourceUrl) return
    wx.setClipboardData({ data: this.data.news.sourceUrl })
  }
})
