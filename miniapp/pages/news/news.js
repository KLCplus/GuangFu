const { newsApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { relativeDate, errorMessage } = require('../../utils/format')

const TYPES = [
  { label: '全部', value: 'ALL' },
  { label: '灾害动态', value: 'DISASTER' }, { label: '政策标准', value: 'POLICY' },
  { label: '行业动态', value: 'INDUSTRY' }, { label: '企业资讯', value: 'ENTERPRISE' },
  { label: '运维指南', value: 'PLATFORM' }
]
const NOTIFICATION_TYPES = [
  { label: '全部类型', value: 'ALL' }, { label: '公告提醒', value: 'NOTICE' },
  { label: '模型更新', value: 'MODEL_UPDATE' }, { label: '异常提醒', value: 'ALERT' },
  { label: '系统通知', value: 'SYSTEM' }
]

Page({
  data: {
    activeTab: 'news',
    types: TYPES,
    notificationTypes: NOTIFICATION_TYPES,
    activeType: 'ALL',
    keyword: '',
    news: [],
    visibleNews: [],
    notifications: [],
    unreadCount: 0,
    unreadOnly: false,
    activeNotificationType: 'ALL',
    loading: true,
    loadingMore: false,
    error: '',
    notificationError: '',
    pageNum: 1,
    pageSize: 8,
    total: 0,
    hasMore: false,
    loggedIn: false
  },

  onShow() {
    const loggedIn = isLoggedIn()
    const wasLoggedIn = this.data.loggedIn
    this.setData({ loggedIn })
    if (this.data.activeTab === 'news' && (!this.data.news.length || !wasLoggedIn)) this.loadNews(true)
    else if (this.data.activeTab === 'notice' && loggedIn) this.loadNotifications()
    else if (!loggedIn) this.setData({ loading: false, notifications: [], unreadCount: 0, notificationError: '' })
  },
  onPullDownRefresh() {
    const task = this.data.activeTab === 'news' ? this.loadNews(true) : this.loadNotifications()
    task.finally(() => wx.stopPullDownRefresh())
  },
  onReachBottom() { if (this.data.activeTab === 'news' && this.data.hasMore && !this.data.loadingMore) this.loadNews(false) },

  switchTab(event) {
    const activeTab = event.currentTarget.dataset.tab
    this.setData({ activeTab })
    if (activeTab === 'notice' && this.data.loggedIn) this.loadNotifications()
  },
  chooseType(event) { this.setData({ activeType: event.currentTarget.dataset.value, pageNum: 1 }); this.loadNews(true) },
  onSearch(event) {
    this.setData({ keyword: event.detail.value })
    clearTimeout(this.searchTimer)
    this.searchTimer = setTimeout(() => this.loadNews(true), 350)
  },
  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },

  async loadNews(reset) {
    const pageNum = reset ? 1 : this.data.pageNum + 1
    this.setData(reset ? { loading: true, error: '' } : { loadingMore: true })
    try {
      const result = await newsApi.list({ pageNum, pageSize: this.data.pageSize, type: this.data.activeType === 'ALL' ? undefined : this.data.activeType, keyword: this.data.keyword.trim() || undefined })
      const records = (result.records || []).map((item) => this.mapNews(item))
      const news = reset ? records : this.data.news.concat(records)
      this.setData({ news, pageNum, total: Number(result.total || 0), hasMore: news.length < Number(result.total || 0), loading: false, loadingMore: false })
      this.setData({ visibleNews: news })
    } catch (error) {
      this.setData({ loading: false, loadingMore: false, error: errorMessage(error, '资讯列表加载失败'), news: reset ? [] : this.data.news, visibleNews: reset ? [] : this.data.visibleNews })
    }
  },

  mapNews(item) {
    const type = item.category || item.newsType
    return { ...item, typeLabel: this.typeLabel(type), timeText: relativeDate(item.sourcePublishedAt || item.publishedAt || item.createdAt), summaryText: item.summary || String(item.content || '').slice(0, 80) || '暂无摘要', alert: type === 'WEATHER_ALERT', sourceText: item.sourceName || '光伏智云平台' }
  },
  typeLabel(type) { return ({ WEATHER_ALERT: '气象预警', DISASTER: '灾害动态', POLICY: '政策标准', INDUSTRY: '行业动态', ENTERPRISE: '企业资讯', PLATFORM: '运维指南', NEWS: '公开资讯', NOTICE: '平台公告' })[type] || type || '资讯' },
  openNews(event) { wx.navigateTo({ url: `/pages/news-detail/news-detail?id=${event.currentTarget.dataset.id}` }) },

  async loadNotifications() {
    if (!isLoggedIn()) { this.setData({ loggedIn: false, notifications: [], notificationError: '' }); return }
    this.setData({ loading: true, notificationError: '' })
    const [listResult, countResult] = await Promise.allSettled([
      newsApi.notifications({ pageNum: 1, pageSize: 20, readStatus: this.data.unreadOnly ? 0 : undefined, type: this.data.activeNotificationType === 'ALL' ? undefined : this.data.activeNotificationType }),
      newsApi.unreadCount()
    ])
    const update = { loading: false }
    if (listResult.status === 'fulfilled') update.notifications = (listResult.value.records || []).map((item) => ({ ...item, unread: Number(item.readStatus) === 0, timeText: relativeDate(item.createdAt), typeLabel: this.notificationTypeLabel(item.notificationType) }))
    else { update.notifications = []; update.notificationError = errorMessage(listResult.reason, '站内消息加载失败') }
    if (countResult.status === 'fulfilled') update.unreadCount = Number(countResult.value.count != null ? countResult.value.count : countResult.value.unreadCount || 0)
    this.setData(update)
  },

  toggleUnread() { this.setData({ unreadOnly: !this.data.unreadOnly }); this.loadNotifications() },
  chooseNotificationType(event) { this.setData({ activeNotificationType: event.currentTarget.dataset.value }); this.loadNotifications() },
  notificationTypeLabel(type) { return ({ NOTICE: '公告提醒', NEWS: '公告提醒', MODEL_UPDATE: '模型更新', ALERT: '异常提醒', SYSTEM: '系统通知', SYSTEM_NOTICE: '系统通知' })[type] || type || '站内消息' },
  async readNotification(event) {
    const id = Number(event.currentTarget.dataset.id)
    const target = this.data.notifications.find((item) => item.notificationId === id)
    if (!target) return
    if (!target.unread) {
      if (target.relatedType === 'NEWS' && target.relatedId) wx.navigateTo({ url: `/pages/news-detail/news-detail?id=${target.relatedId}` })
      return
    }
    try {
      await newsApi.markRead(id)
      const notifications = this.data.notifications.map((item) => item.notificationId === id ? { ...item, unread: false, readStatus: 1 } : item)
      this.setData({ notifications, unreadCount: Math.max(0, this.data.unreadCount - 1) })
      if (target.relatedType === 'NEWS' && target.relatedId) wx.navigateTo({ url: `/pages/news-detail/news-detail?id=${target.relatedId}` })
    } catch (error) { wx.showToast({ title: errorMessage(error, '标记失败'), icon: 'none' }) }
  },
  async readAll() {
    try {
      await newsApi.markAllRead()
      this.setData({ notifications: this.data.notifications.map((item) => ({ ...item, unread: false, readStatus: 1 })), unreadCount: 0 })
      wx.showToast({ title: '消息已全部读过', icon: 'success' })
    } catch (error) { wx.showToast({ title: errorMessage(error, '操作失败'), icon: 'none' }) }
  }
})
