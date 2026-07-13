const { newsApi } = require('../../utils/api')
const { isLoggedIn } = require('../../utils/request')
const { relativeDate, errorMessage } = require('../../utils/format')

const TYPES = [
  { label: '全部', value: 'ALL' }, { label: '新闻', value: 'NEWS' }, { label: '公告', value: 'NOTICE' },
  { label: '模型更新', value: 'MODEL_UPDATE' }, { label: '异常提醒', value: 'ALERT' },
  { label: '系统通知', value: 'SYSTEM_NOTICE' }, { label: '行业资讯', value: 'INDUSTRY_NEWS' }
]

Page({
  data: {
    activeTab: 'news',
    types: TYPES,
    activeType: 'ALL',
    keyword: '',
    news: [],
    visibleNews: [],
    notifications: [],
    unreadCount: 0,
    unreadOnly: false,
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
    if (!loggedIn) {
      this.setData({ loading: false, news: [], visibleNews: [], notifications: [], error: '', notificationError: '' })
      return
    }
    if (!wasLoggedIn || (this.data.activeTab === 'news' && !this.data.news.length)) this.loadNews(true)
    else if (this.data.activeTab === 'notice') this.loadNotifications()
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
  onSearch(event) { this.setData({ keyword: event.detail.value }); this.filterNews() },
  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },

  async loadNews(reset) {
    const pageNum = reset ? 1 : this.data.pageNum + 1
    this.setData(reset ? { loading: true, error: '' } : { loadingMore: true })
    try {
      const result = await newsApi.list({ pageNum, pageSize: this.data.pageSize, type: this.data.activeType === 'ALL' ? undefined : this.data.activeType })
      const records = (result.records || []).map((item) => this.mapNews(item))
      const news = reset ? records : this.data.news.concat(records)
      this.setData({ news, pageNum, total: Number(result.total || 0), hasMore: news.length < Number(result.total || 0), loading: false, loadingMore: false })
      this.filterNews()
    } catch (error) {
      this.setData({ loading: false, loadingMore: false, error: errorMessage(error, '资讯列表加载失败'), news: reset ? [] : this.data.news, visibleNews: reset ? [] : this.data.visibleNews })
    }
  },

  mapNews(item) {
    return { ...item, typeLabel: this.typeLabel(item.newsType), timeText: relativeDate(item.publishedAt || item.createdAt), summaryText: item.summary || String(item.content || '').slice(0, 80) || '暂无摘要', alert: item.newsType === 'ALERT' }
  },
  typeLabel(type) { return ({ NEWS: '新闻', NOTICE: '公告', MODEL_UPDATE: '模型更新', ALERT: '异常提醒', SYSTEM_NOTICE: '系统通知', INDUSTRY_NEWS: '行业资讯', OPERATION: '运营消息' })[type] || type || '资讯' },
  filterNews() {
    const keyword = this.data.keyword.trim().toLowerCase()
    const visibleNews = !keyword ? this.data.news : this.data.news.filter((item) => [item.title, item.summary, item.content, item.newsType].filter(Boolean).some((value) => String(value).toLowerCase().includes(keyword)))
    this.setData({ visibleNews })
  },
  openNews(event) { wx.navigateTo({ url: `/pages/news-detail/news-detail?id=${event.currentTarget.dataset.id}` }) },

  async loadNotifications() {
    if (!isLoggedIn()) { this.setData({ loggedIn: false, notifications: [], notificationError: '' }); return }
    this.setData({ loading: true, notificationError: '' })
    const [listResult, countResult] = await Promise.allSettled([
      newsApi.notifications({ pageNum: 1, pageSize: 20, readStatus: this.data.unreadOnly ? 0 : undefined }),
      newsApi.unreadCount()
    ])
    const update = { loading: false }
    if (listResult.status === 'fulfilled') update.notifications = (listResult.value.records || []).map((item) => ({ ...item, unread: Number(item.readStatus) === 0, timeText: relativeDate(item.createdAt) }))
    else { update.notifications = []; update.notificationError = errorMessage(listResult.reason, '站内通知加载失败') }
    if (countResult.status === 'fulfilled') update.unreadCount = Number(countResult.value.count != null ? countResult.value.count : countResult.value.unreadCount || 0)
    this.setData(update)
  },

  toggleUnread() { this.setData({ unreadOnly: !this.data.unreadOnly }); this.loadNotifications() },
  async readNotification(event) {
    const id = Number(event.currentTarget.dataset.id)
    const target = this.data.notifications.find((item) => item.notificationId === id)
    if (!target || !target.unread) return
    try {
      await newsApi.markRead(id)
      const notifications = this.data.notifications.map((item) => item.notificationId === id ? { ...item, unread: false, readStatus: 1 } : item)
      this.setData({ notifications, unreadCount: Math.max(0, this.data.unreadCount - 1) })
    } catch (error) { wx.showToast({ title: errorMessage(error, '标记失败'), icon: 'none' }) }
  },
  async readAll() {
    try {
      await newsApi.markAllRead()
      this.setData({ notifications: this.data.notifications.map((item) => ({ ...item, unread: false, readStatus: 1 })), unreadCount: 0 })
      wx.showToast({ title: '已全部读过', icon: 'success' })
    } catch (error) { wx.showToast({ title: errorMessage(error, '操作失败'), icon: 'none' }) }
  }
})
