<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Bell, Check, Cpu, Document, Refresh, Search, Warning } from '@element-plus/icons-vue'
import { getNewsList } from '../api/news'
import type { News, NewsType } from '../api/news'
import { getNotifications, getUnreadCount, markAllNotificationsRead, markNotificationRead } from '../api/notification'
import type { Notification } from '../api/notification'

type ActiveTab = 'news' | 'notifications'
type NewsTypeFilter = 'ALL' | NewsType
type NotificationFilter = 'ALL' | 'UNREAD'
type NotificationTypeFilter = 'ALL' | 'NOTICE' | 'MODEL_UPDATE' | 'ALERT' | 'SYSTEM'

const props = withDefaults(defineProps<{
  basePath?: string
}>(), {
  basePath: '/news'
})

const route = useRoute()
const router = useRouter()
const activeTab = ref<ActiveTab>('news')
const newsType = ref<NewsTypeFilter>('ALL')
const notificationFilter = ref<NotificationFilter>('ALL')
const notificationType = ref<NotificationTypeFilter>('ALL')
const keyword = ref('')
const newsPage = ref(1)
const notificationPage = ref(1)
const pageSize = ref(10)
const newsItems = ref<News[]>([])
const notificationItems = ref<Notification[]>([])
const newsTotal = ref<number | null>(null)
const notificationTotal = ref<number | null>(null)
const unreadCount = ref<number | null>(null)
const newsLoading = ref(false)
const notificationLoading = ref(false)
const newsError = ref('')
const notificationError = ref('')
const unreadError = ref('')
const actionLoadingId = ref<number | null>(null)
const allReadLoading = ref(false)
const isLoggedIn = computed(() => Boolean(localStorage.getItem('token')))
let searchTimer: number | undefined

const categories: Array<{ label: string; value: NewsTypeFilter; icon: typeof Document }> = [
  { label: '全部', value: 'ALL', icon: Document },
  { label: '气象预警', value: 'WEATHER_ALERT', icon: Warning },
  { label: '灾害动态', value: 'DISASTER', icon: Warning },
  { label: '政策标准', value: 'POLICY', icon: Document },
  { label: '行业动态', value: 'INDUSTRY', icon: Document },
  { label: '企业资讯', value: 'ENTERPRISE', icon: Document },
  { label: '平台资讯', value: 'PLATFORM', icon: Bell }
]
const notificationTypes: Array<{ label: string; value: NotificationTypeFilter; icon: typeof Document }> = [
  { label: '全部类型', value: 'ALL', icon: Bell },
  { label: '公告通知', value: 'NOTICE', icon: Bell },
  { label: '模型更新', value: 'MODEL_UPDATE', icon: Cpu },
  { label: '异常提醒', value: 'ALERT', icon: Warning },
  { label: '系统通知', value: 'SYSTEM', icon: Bell }
]

const newsGroups = computed(() => groupByDate(newsItems.value, (item) => item.publishedAt || item.createdAt))
const notificationGroups = computed(() => groupByDate(notificationItems.value, (item) => item.createdAt))
const currentTotal = computed(() => activeTab.value === 'news' ? (newsTotal.value ?? 0) : (notificationTotal.value ?? 0))
const canMarkAllRead = computed(() => isLoggedIn.value && activeTab.value === 'notifications' && (unreadCount.value ?? 0) > 0 && !allReadLoading.value)

watch(
  () => route.fullPath,
  () => {
    const tab = route.query.tab === 'notifications' ? 'notifications' : 'news'
    activeTab.value = tab
    newsType.value = categories.some((item) => item.value === route.query.type) ? route.query.type as NewsTypeFilter : 'ALL'
    notificationFilter.value = route.query.unread === '1' ? 'UNREAD' : 'ALL'
    notificationType.value = notificationTypes.some((item) => item.value === route.query.notificationType) ? route.query.notificationType as NotificationTypeFilter : 'ALL'
    keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
    newsPage.value = queryPage('newsPage')
    notificationPage.value = queryPage('notificationPage')
    pageSize.value = [10, 20, 30].includes(Number(route.query.size)) ? Number(route.query.size) : 10
    void refreshActive()
    if (isLoggedIn.value) void fetchUnreadCount()
    else unreadCount.value = null
  },
  { immediate: true }
)

onBeforeUnmount(() => window.clearTimeout(searchTimer))

function queryPage(key: 'newsPage' | 'notificationPage') {
  const value = Number(route.query[key])
  return Number.isInteger(value) && value > 0 ? value : 1
}

function updateRoute(replace = false) {
  const query: Record<string, string> = {
    tab: activeTab.value,
    size: String(pageSize.value)
  }
  if (activeTab.value === 'news') {
    if (newsType.value !== 'ALL') query.type = newsType.value
    if (keyword.value.trim()) query.keyword = keyword.value.trim()
    if (newsPage.value > 1) query.newsPage = String(newsPage.value)
  } else {
    if (notificationFilter.value === 'UNREAD') query.unread = '1'
    if (notificationType.value !== 'ALL') query.notificationType = notificationType.value
    if (notificationPage.value > 1) query.notificationPage = String(notificationPage.value)
  }
  void router[replace ? 'replace' : 'push']({ path: props.basePath, query })
}

function selectTab(tab: ActiveTab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  updateRoute()
}

function selectNewsType(type: NewsTypeFilter) {
  newsType.value = type
  newsPage.value = 1
  updateRoute()
}

function chooseNewsType(type: NewsTypeFilter) {
  activeTab.value = 'news'
  newsType.value = type
  newsPage.value = 1
  updateRoute()
}

function selectNotificationFilter(filter: NotificationFilter) {
  notificationFilter.value = filter
  notificationPage.value = 1
  updateRoute()
}

function chooseNotificationFilter(filter: NotificationFilter) {
  activeTab.value = 'notifications'
  notificationFilter.value = filter
  notificationPage.value = 1
  updateRoute()
}

function chooseNotificationType(type: NotificationTypeFilter) {
  activeTab.value = 'notifications'
  notificationType.value = type
  notificationPage.value = 1
  updateRoute()
}

function scheduleSearch() {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    newsPage.value = 1
    updateRoute(true)
  }, 350)
}

async function refreshActive() {
  if (activeTab.value === 'news') await fetchNews()
  else await fetchNotifications()
}

async function fetchNews() {
  newsLoading.value = true
  newsError.value = ''
  try {
    const result = await getNewsList({
      pageNum: newsPage.value,
      pageSize: pageSize.value,
      type: newsType.value === 'ALL' ? undefined : newsType.value,
      keyword: keyword.value.trim() || undefined
    })
    newsItems.value = result.records
    newsTotal.value = result.total
  } catch (error) {
    console.error('新闻数据加载失败', error)
    newsItems.value = []
    newsTotal.value = null
    newsError.value = '新闻数据加载失败，请检查服务状态后重试。'
  } finally {
    newsLoading.value = false
  }
}

async function fetchNotifications() {
  if (!isLoggedIn.value) {
    notificationItems.value = []
    notificationTotal.value = null
    notificationError.value = ''
    return
  }
  notificationLoading.value = true
  notificationError.value = ''
  try {
    const result = await getNotifications({
      pageNum: notificationPage.value,
      pageSize: pageSize.value,
      readStatus: notificationFilter.value === 'UNREAD' ? 0 : undefined,
      type: notificationType.value === 'ALL' ? undefined : notificationType.value
    })
    notificationItems.value = result.records
    notificationTotal.value = result.total
  } catch (error) {
    console.error('通知数据加载失败', error)
    notificationItems.value = []
    notificationTotal.value = null
    notificationError.value = '通知数据加载失败，请检查服务状态后重试。'
  } finally {
    notificationLoading.value = false
  }
}

async function fetchUnreadCount() {
  if (!isLoggedIn.value) { unreadCount.value = null; return }
  unreadError.value = ''
  try {
    const result = await getUnreadCount()
    unreadCount.value = result.count ?? result.unreadCount ?? 0
  } catch (error) {
    console.error('未读通知数加载失败', error)
    unreadCount.value = null
    unreadError.value = '未读通知数加载失败'
  }
}

function refresh() {
  void refreshActive()
  if (isLoggedIn.value) void fetchUnreadCount()
}

function changePage(page: number) {
  if (activeTab.value === 'news') newsPage.value = page
  else notificationPage.value = page
  updateRoute()
}

function changePageSize(size: number) {
  pageSize.value = size
  if (activeTab.value === 'news') newsPage.value = 1
  else notificationPage.value = 1
  updateRoute()
}

async function openNews(item: News) {
  await router.push(`${props.basePath}/${item.newsId}`)
}

async function readNotification(item: Notification) {
  if (item.readStatus === 1 || actionLoadingId.value !== null) return
  actionLoadingId.value = item.notificationId
  try {
    await markNotificationRead(item.notificationId)
    item.readStatus = 1
    unreadCount.value = Math.max(0, (unreadCount.value ?? 0) - 1)
  } catch (error) {
    console.error('通知标记已读失败', error)
    ElMessage.error('通知标记已读失败，请重试。')
  } finally {
    actionLoadingId.value = null
  }
}

async function readAllNotifications() {
  if (!canMarkAllRead.value) return
  allReadLoading.value = true
  try {
    await markAllNotificationsRead()
    unreadCount.value = 0
    notificationItems.value = notificationItems.value.map((item) => ({ ...item, readStatus: 1 }))
    if (notificationFilter.value === 'UNREAD') {
      notificationPage.value = 1
      updateRoute(true)
    }
    ElMessage.success('全部通知已标记为已读')
  } catch (error) {
    console.error('全部通知标记已读失败', error)
    ElMessage.error('全部通知标记已读失败，请重试。')
  } finally {
    allReadLoading.value = false
  }
}

function clearNewsFilters() {
  newsType.value = 'ALL'
  keyword.value = ''
  newsPage.value = 1
  updateRoute()
}

function newsTypeLabel(type?: string) {
  const labels: Record<string, string> = { WEATHER_ALERT: '气象预警', DISASTER: '灾害动态', POLICY: '政策标准', INDUSTRY: '行业动态', ENTERPRISE: '企业资讯', PLATFORM: '平台资讯', NEWS: '平台新闻', NOTICE: '平台公告' }
  return labels[type ?? ''] ?? type ?? '新闻'
}

function notificationTypeLabel(type?: string) {
  const labels: Record<string, string> = { NOTICE: '公告通知', NEWS: '公告通知', MODEL_UPDATE: '模型更新', ALERT: '异常提醒', SYSTEM: '系统通知', SYSTEM_NOTICE: '系统通知' }
  return labels[type ?? ''] ?? type ?? '站内通知'
}

function newsTypeTag(type?: string) {
  if (type === 'ALERT') return 'danger'
  if (type === 'MODEL_UPDATE') return 'success'
  if (type === 'NOTICE') return 'warning'
  return 'info'
}

function rowIcon(type?: string) {
  if (type === 'MODEL_UPDATE') return Cpu
  if (type === 'ALERT') return Warning
  if (type === 'NOTICE' || type === 'SYSTEM') return Bell
  return Document
}

function typeClass(type?: string) {
  return `type-${(type || 'default').toLowerCase()}`
}

function formatTime(value?: string) {
  if (!value) return '-'
  const date = toDate(value)
  if (!date) return value
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(date)
}

function toDate(value?: string) {
  if (!value) return null
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

function groupByDate<T>(items: T[], getTime: (item: T) => string | undefined) {
  const groups: Array<{ label: string; items: T[] }> = []
  const indexed = new Map<string, { label: string; items: T[] }>()
  items.forEach((item) => {
    const time = getTime(item)
    const key = dayKey(time)
    let group = indexed.get(key)
    if (!group) {
      group = { label: dayLabel(time), items: [] }
      indexed.set(key, group)
      groups.push(group)
    }
    group.items.push(item)
  })
  return groups
}

function dayKey(value?: string) {
  const date = toDate(value)
  return date ? `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}` : 'unknown'
}

function dayLabel(value?: string) {
  const date = toDate(value)
  if (!date) return '时间未知'
  const today = new Date()
  const startToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  const startDate = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const diff = Math.round((startToday - startDate) / 86400000)
  if (diff === 0) return '今天'
  if (diff === 1) return '昨天'
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 ${date.getDate()} 日`
}
</script>

<template>
  <section class="notification-center">
    <header class="page-header">
      <div>
        <h1>新闻通知</h1>
      </div>
      <div class="header-actions">
        <span class="compact-stats">新闻 {{ newsTotal ?? '—' }}<template v-if="isLoggedIn"> · 未读通知 {{ unreadCount ?? '—' }}</template></span>
        <el-button :loading="newsLoading || notificationLoading" :icon="Refresh" @click="refresh">刷新</el-button>
        <el-button v-if="activeTab === 'notifications'" type="primary" plain :icon="Check" :disabled="!canMarkAllRead" :loading="allReadLoading" @click="readAllNotifications">全部已读</el-button>
      </div>
    </header>

    <div class="inbox-layout">
      <aside class="filter-sidebar" aria-label="新闻通知筛选">
        <div v-if="activeTab === 'notifications'" class="sidebar-section">
          <p class="sidebar-title">收件箱</p>
          <button class="filter-item" :class="{ active: activeTab === 'notifications' && notificationFilter === 'ALL' }" @click="chooseNotificationFilter('ALL')">
            <el-icon><Bell /></el-icon><span>全部通知</span><b>{{ notificationTotal ?? '—' }}</b>
          </button>
          <button class="filter-item" :class="{ active: activeTab === 'notifications' && notificationFilter === 'UNREAD' }" @click="chooseNotificationFilter('UNREAD')">
            <el-icon><Bell /></el-icon><span>未读通知</span><b>{{ unreadCount ?? '—' }}</b>
          </button>
        </div>
        <div v-if="activeTab === 'notifications'" class="sidebar-section">
          <p class="sidebar-title">通知类型</p>
          <button v-for="item in notificationTypes" :key="item.value" class="filter-item" :class="{ active: notificationType === item.value }" @click="chooseNotificationType(item.value)">
            <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
          </button>
        </div>
        <div v-else class="sidebar-section">
          <p class="sidebar-title">新闻分类</p>
          <button v-for="category in categories" :key="category.value" class="filter-item" :class="{ active: activeTab === 'news' && newsType === category.value }" @click="chooseNewsType(category.value)">
            <el-icon><component :is="category.icon" /></el-icon><span>{{ category.label }}</span>
          </button>
        </div>
      </aside>

      <main class="inbox-main">
        <nav class="content-tabs" aria-label="内容类型">
          <button :class="{ active: activeTab === 'news' }" @click="selectTab('news')">新闻与公告</button>
          <button :class="{ active: activeTab === 'notifications' }" @click="selectTab('notifications')">站内通知</button>
        </nav>

        <div class="toolbar">
          <template v-if="activeTab === 'news'">
            <el-input v-model="keyword" class="search-box" :prefix-icon="Search" clearable placeholder="搜索标题、摘要或正文" @input="scheduleSearch" @clear="scheduleSearch" />
          </template>
          <template v-else>
            <div class="toggle-group">
              <button :class="{ active: notificationFilter === 'ALL' }" @click="selectNotificationFilter('ALL')">全部</button>
              <button :class="{ active: notificationFilter === 'UNREAD' }" @click="selectNotificationFilter('UNREAD')">未读</button>
            </div>
          </template>
          <span class="group-label">按日期分组</span>
          <el-button text :icon="Refresh" @click="refresh">刷新</el-button>
        </div>

        <section v-if="activeTab === 'news'" class="list-shell">
          <div v-if="newsError" class="state-box error-state"><strong>新闻数据加载失败</strong><span>请检查服务状态后重试。</span><el-button type="primary" @click="fetchNews">重新加载</el-button></div>
          <template v-else-if="newsLoading">
            <div v-for="index in 5" :key="index" class="skeleton-row"><el-skeleton animated><template #template><el-skeleton-item variant="circle" /><div><el-skeleton-item variant="h3" style="width: 46%" /><el-skeleton-item variant="text" style="width: 78%; margin-top: 10px" /></div></template></el-skeleton></div>
          </template>
          <div v-else-if="newsItems.length === 0" class="state-box"><strong>暂无新闻内容</strong><span>当前筛选条件下没有可展示的新闻。</span><el-button text type="primary" @click="clearNewsFilters">清除筛选</el-button></div>
          <template v-else>
            <section v-for="group in newsGroups" :key="group.label" class="date-group">
              <h2>{{ group.label }}</h2>
              <article v-for="item in group.items" :key="item.newsId" class="message-row" tabindex="0" @click="openNews(item)" @keydown.enter="openNews(item)">
                <span class="message-marker news-marker"></span>
                <el-icon class="message-icon" :class="typeClass(item.category || item.newsType)"><component :is="rowIcon(item.category || item.newsType)" /></el-icon>
                <div class="message-body"><div class="message-kicker"><span :class="['type-tag', typeClass(item.category || item.newsType)]">{{ newsTypeLabel(item.category || item.newsType) }}</span><span v-if="item.sourceName" class="source-name">{{ item.sourceName }}</span><span v-if="item.warningLevel" class="warning-level">{{ item.warningLevel }}</span></div><h3>{{ item.title }}</h3><p>{{ item.summary || item.content }}</p><p v-if="item.warningRegion" class="warning-meta">{{ item.warningRegion }}<template v-if="item.warningAgency"> · {{ item.warningAgency }}</template></p></div>
                <time>{{ formatTime(item.publishedAt || item.createdAt) }}</time><el-icon class="row-arrow"><ArrowRight /></el-icon>
              </article>
            </section>
          </template>
        </section>

        <section v-else class="list-shell">
          <div v-if="!isLoggedIn" class="state-box login-state"><strong>登录后查看站内通知</strong><span>站内通知只包含与你账号相关的公告提醒、模型更新和异常消息。</span><el-button type="primary" @click="router.push('/login')">前往登录</el-button></div>
          <div v-else-if="notificationError" class="state-box error-state"><strong>通知数据加载失败</strong><span>请检查服务状态后重试。</span><el-button type="primary" @click="fetchNotifications">重新加载</el-button></div>
          <template v-else-if="notificationLoading">
            <div v-for="index in 5" :key="index" class="skeleton-row"><el-skeleton animated><template #template><el-skeleton-item variant="circle" /><div><el-skeleton-item variant="h3" style="width: 46%" /><el-skeleton-item variant="text" style="width: 78%; margin-top: 10px" /></div></template></el-skeleton></div>
          </template>
          <div v-else-if="notificationItems.length === 0" class="state-box"><strong>{{ notificationFilter === 'UNREAD' ? '没有未读通知' : '暂无通知' }}</strong><span>{{ notificationFilter === 'UNREAD' ? '你已经处理完所有通知。' : '新的站内通知将在这里显示。' }}</span><el-button text type="primary" @click="refresh">刷新</el-button></div>
          <template v-else>
            <section v-for="group in notificationGroups" :key="group.label" class="date-group">
              <h2>{{ group.label }}</h2>
              <article v-for="item in group.items" :key="item.notificationId" class="message-row notification-row" :class="{ unread: item.readStatus === 0 }" tabindex="0" @click="readNotification(item)" @keydown.enter="readNotification(item)">
                <span class="message-marker" :class="{ unread: item.readStatus === 0 }"></span>
                <el-icon class="message-icon" :class="typeClass(item.notificationType)"><component :is="rowIcon(item.notificationType)" /></el-icon>
                <div class="message-body"><div class="message-kicker"><span :class="['type-tag', typeClass(item.notificationType)]">{{ notificationTypeLabel(item.notificationType) }}</span><span v-if="item.readStatus === 0" class="unread-text">未读</span></div><h3>{{ item.title }}</h3><p>{{ item.content }}</p></div>
                <time>{{ formatTime(item.createdAt) }}</time><el-icon class="row-arrow"><ArrowRight /></el-icon>
              </article>
            </section>
          </template>
        </section>

        <footer v-if="!newsError && !notificationError && currentTotal > 0" class="pagination-row">
          <el-pagination background layout="total, sizes, prev, pager, next" :total="currentTotal" :current-page="activeTab === 'news' ? newsPage : notificationPage" :page-size="pageSize" :page-sizes="[10, 20, 30]" @current-change="changePage" @size-change="changePageSize" />
        </footer>
        <p v-if="unreadError" class="unread-warning">{{ unreadError }}</p>
      </main>
    </div>
  </section>
</template>

<style scoped>
.notification-center { display: flex; flex-direction: column; min-height: 100vh; margin: -24px -28px -36px; color: #172033; background: #fff; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 24px 32px; border-bottom: 1px solid var(--color-border); }
.page-header h1 { margin: 0; color: #10274c; font-size: 31px; letter-spacing: -.025em; }
.header-actions { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 9px; }
.compact-stats { color: #64748b; font-size: 13px; white-space: nowrap; }
.inbox-layout { display: grid; grid-template-columns: minmax(0, 1fr) 224px; flex: 1; min-height: 620px; background: #fff; }
.filter-sidebar { grid-column: 2; grid-row: 1; padding: 18px 12px; border-left: 1px solid var(--color-border); background: #fbfcfe; }
.sidebar-section + .sidebar-section { margin-top: 22px; padding-top: 20px; border-top: 1px solid #e9edf3; }
.sidebar-title { margin: 0 8px 8px; color: #64748b; font-size: 12px; font-weight: 700; letter-spacing: .04em; }
.filter-item { display: flex; width: 100%; align-items: center; gap: 9px; min-height: 37px; padding: 0 9px; border: 0; border-radius: 6px; color: #526075; background: transparent; font: inherit; font-size: 14px; text-align: left; cursor: pointer; }
.filter-item:hover { background: #f0f4f8; color: #1d4f88; }
.filter-item.active { color: #1d5c9f; background: #eaf3fb; font-weight: 600; }
.filter-item b { margin-left: auto; color: #718096; font-size: 12px; font-weight: 600; }
.inbox-main { grid-column: 1; min-width: 0; }
.content-tabs { display: flex; gap: 4px; padding: 14px 16px 0; border-bottom: 1px solid var(--color-border); }
.content-tabs button, .toggle-group button { padding: 9px 13px; border: 0; border-radius: 6px 6px 0 0; color: #64748b; background: transparent; font: inherit; font-size: 14px; cursor: pointer; }
.content-tabs button.active { color: #185b9d; box-shadow: inset 0 -2px #2477bd; font-weight: 650; }
.toolbar { display: flex; align-items: center; gap: 12px; min-height: 66px; padding: 12px 16px; border-bottom: 1px solid var(--color-border); background: #fff; }
.search-box { max-width: 520px; flex: 1; }
.toggle-group { display: inline-flex; padding: 3px; border: 1px solid #dce3eb; border-radius: 7px; background: #f8fafc; }
.toggle-group button { padding: 5px 11px; border-radius: 5px; }
.toggle-group button.active { color: #1d5c9f; background: #fff; box-shadow: 0 1px 2px rgb(15 23 42 / 8%); font-weight: 600; }
.group-label { margin-left: auto; color: #718096; font-size: 13px; white-space: nowrap; }
.list-shell { min-height: 400px; }
.date-group h2 { margin: 0; padding: 14px 16px 8px; color: #64748b; font-size: 12px; font-weight: 700; }
.message-row { display: grid; grid-template-columns: 8px 28px minmax(0, 1fr) minmax(128px, auto) 18px; align-items: start; gap: 10px; min-height: 80px; padding: 14px 16px; border-top: 1px solid #edf0f4; cursor: pointer; transition: background .15s ease; }
.message-row:hover { background: #f8fafc; }
.message-row:focus-visible { outline: 2px solid #4c95cf; outline-offset: -2px; }
.notification-row.unread { background: #fbfdff; }
.notification-row.unread:hover { background: #f4f9fd; }
.message-marker { width: 8px; height: 8px; margin-top: 7px; border-radius: 50%; background: transparent; }
.message-marker.unread { background: #2582c4; }
.message-icon { margin-top: 2px; padding: 5px; border-radius: 7px; color: #55708c; background: #f0f4f8; font-size: 17px; }
.message-icon.type-alert, .type-tag.type-alert { color: #aa6411; background: #fff5e8; }
.message-icon.type-model_update, .type-tag.type-model_update { color: #28755d; background: #edf8f2; }
.message-icon.type-notice, .type-tag.type-notice { color: #5465a6; background: #f0f1fb; }
.message-body { min-width: 0; }
.message-kicker { display: flex; align-items: center; gap: 8px; min-height: 20px; }
.source-name { overflow: hidden; color: #64748b; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.warning-level { padding: 2px 7px; border-radius: 999px; color: #b45309; background: #fff7ed; font-size: 11px; }
.message-body .warning-meta { display: block; margin-top: 4px; color: #8a5a24; font-size: 12px; -webkit-line-clamp: unset; }
.type-tag { display: inline-flex; align-items: center; height: 21px; padding: 0 7px; border-radius: 4px; color: #55708c; background: #eef3f7; font-size: 12px; }
.unread-text { color: #2477bd; font-size: 12px; }
.message-body h3 { overflow: hidden; margin: 3px 0 2px; color: #283548; font-size: 15px; font-weight: 600; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.notification-row:not(.unread) h3 { color: #4c5c70; font-weight: 500; }
.message-body p { display: -webkit-box; overflow: hidden; margin: 0; color: #718096; font-size: 13px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.message-row time { padding-top: 5px; color: #8a97a8; font-size: 12px; text-align: right; white-space: nowrap; }
.row-arrow { margin-top: 6px; color: #a6b2c1; opacity: 0; transition: opacity .15s ease; }.message-row:hover .row-arrow { opacity: 1; }
.state-box { display: grid; place-items: center; gap: 7px; min-height: 280px; padding: 25px; color: #718096; text-align: center; }.state-box strong { color: #334155; font-size: 15px; }.state-box.error-state strong { color: #a44949; }
.skeleton-row { padding: 20px 16px; border-bottom: 1px solid #edf0f4; }.skeleton-row :deep(.el-skeleton__item) { margin-right: 12px; }.skeleton-row :deep(.el-skeleton__template) { display: flex; align-items: flex-start; }.skeleton-row :deep(.el-skeleton__template > div) { flex: 1; }
.pagination-row { display: flex; justify-content: flex-end; padding: 14px 16px; border-top: 1px solid var(--color-border); }.unread-warning { margin: 0; padding: 0 16px 14px; color: #b16a1b; font-size: 12px; }
@media (max-width: 1024px) { .notification-center { min-height: auto; margin: -24px -28px -36px; }.inbox-layout { grid-template-columns: 1fr; overflow: visible; }.filter-sidebar { grid-column: 1; grid-row: auto; display: grid; grid-template-columns: auto 1fr; gap: 16px; padding: 10px 12px; border-left: 0; border-bottom: 1px solid var(--color-border); }.sidebar-section { display: flex; align-items: center; gap: 4px; overflow-x: auto; }.sidebar-section + .sidebar-section { margin: 0; padding: 0; border: 0; }.sidebar-title { flex: 0 0 auto; margin: 0 4px 0 0; }.filter-item { width: auto; flex: 0 0 auto; white-space: nowrap; }.filter-item b { margin-left: 2px; } }
@media (max-width: 700px) { .notification-center { margin: -16px -16px -24px; }.page-header { display: grid; padding: 20px 16px; }.page-header h1 { font-size: 27px; }.header-actions { justify-content: flex-start; }.filter-sidebar { display: block; }.sidebar-section + .sidebar-section { margin-top: 10px; }.message-row { grid-template-columns: 8px 26px minmax(0, 1fr) 16px; gap: 8px; }.message-row time { grid-column: 3; grid-row: 2; padding: 0; text-align: left; }.row-arrow { grid-column: 4; grid-row: 1; }.toolbar { align-items: flex-start; flex-wrap: wrap; }.search-box { flex-basis: 100%; }.group-label { margin-left: 0; }.pagination-row { overflow-x: auto; justify-content: flex-start; } }
</style>
