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
      const type = item.category || item.newsType
      const typeLabel = ({ WEATHER_ALERT: '气象预警', DISASTER: '灾害动态', POLICY: '政策标准', INDUSTRY: '行业动态', ENTERPRISE: '企业资讯', PLATFORM: '运维指南', NEWS: '公开资讯', NOTICE: '平台公告' })[type] || type || '资讯'
      const richContent = this.richContent(item.content)
      this.setData({ news: { ...item, richContent, typeLabel, sourceText: item.sourceName || '光伏智云平台', sourceUrl: this.safeUrl(item.sourceUrl), attachmentUrl: this.safeUrl(item.attachmentUrl), timeText: dateTime(item.sourcePublishedAt || item.publishedAt || item.createdAt), effectiveText: item.effectiveAt ? dateTime(item.effectiveAt) : '', expiresText: item.expiresAt ? dateTime(item.expiresAt) : '' }, loading: false })
      wx.setNavigationBarTitle({ title: item.title || '资讯详情' })
    } catch (error) { this.setData({ loading: false, error: errorMessage(error, '资讯详情加载失败') }) }
  },
  copyOriginal() {
    if (!this.data.news || !this.data.news.sourceUrl) return
    wx.setClipboardData({ data: this.data.news.sourceUrl })
  },
  copyAttachment() {
    if (!this.data.news || !this.data.news.attachmentUrl) return
    wx.setClipboardData({ data: this.data.news.attachmentUrl })
  },
  safeUrl(value) {
    const text = String(value || '').trim()
    return /^https?:\/\//i.test(text) ? text : ''
  },
  richContent(value) {
    const content = String(value || '').trim()
    if (!content) return ''
    if (/<(?:p|br|h[1-4]|ul|ol|li|blockquote|table|strong|em|a)\b/i.test(content)) return content
    return content.split(/\n{2,}/).map((part) => `<p>${this.escapeHtml(part).replace(/\n/g, '<br>')}</p>`).join('')
  },
  escapeHtml(value) {
    return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;')
  }
})
