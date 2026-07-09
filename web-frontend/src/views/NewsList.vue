<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  loadNewsPage,
  loadNotifications,
  loadUnreadNotificationCount,
  markEveryNotificationRead,
  markNotificationAsRead
} from '../api/userPages'
import type { DataSource } from '../api/userPages'
import type { News, NewsType } from '../api/news'
import type { Notification } from '../api/notification'

type NewsTypeFilter = 'ALL' | NewsType
type NoticeFilter = 'ALL' | 'UNREAD'

interface NewsTypeOption {
  label: string
  value: NewsTypeFilter
}

const router = useRouter()

const loading = ref(false)
const notificationLoading = ref(false)
const loadError = ref('')
const newsList = ref<News[]>([])
const notifications = ref<Notification[]>([])
const unreadCount = ref(0)
const total = ref(0)
const newsSource = ref<DataSource>('remote')
const notificationSource = ref<DataSource>('remote')
const actionLoadingId = ref<number | null>(null)

const query = reactive({
  pageNum: 1,
  pageSize: 8,
  type: 'ALL' as NewsTypeFilter,
  keyword: ''
})

const noticeFilter = ref<NoticeFilter>('ALL')

const newsTypeOptions: NewsTypeOption[] = [
  { label: '全部', value: 'ALL' },
  { label: '新闻', value: 'NEWS' },
  { label: '公告', value: 'NOTICE' },
  { label: '模型更新', value: 'MODEL_UPDATE' },
  { label: '异常提醒', value: 'ALERT' },
  { label: '系统通知', value: 'SYSTEM_NOTICE' },
  { label: '行业资讯', value: 'INDUSTRY_NEWS' }
]

const noticeOptions = [
  { label: '全部通知', value: 'ALL' },
  { label: '未读通知', value: 'UNREAD' }
]

const filteredNews = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  if (!keyword) return newsList.value
  return newsList.value.filter((item) => {
    return [item.title, item.summary, item.content, item.newsType]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword))
  })
})

const stats = computed(() => [
  { label: '新闻总数', value: String(total.value), note: '按当前筛选条件统计' },
  { label: '当前页', value: String(filteredNews.value.length), note: '支持关键词本地过滤' },
  { label: '未读通知', value: String(unreadCount.value), note: '来自通知接口或 mock 兜底' }
])

onMounted(() => {
  void fetchPageData()
})

async function fetchPageData() {
  await Promise.all([fetchNews(), fetchNotifications(), fetchUnreadCount()])
}

async function fetchNews() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await loadNewsPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      type: query.type === 'ALL' ? undefined : query.type
    })
    newsList.value = result.data.records
    total.value = result.data.total
    newsSource.value = result.source
    if (result.source === 'mock') {
      ElMessage.info('新闻真实接口暂不可用，当前展示模拟数据')
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '新闻列表加载失败'
  } finally {
    loading.value = false
  }
}

async function fetchNotifications() {
  notificationLoading.value = true
  try {
    const result = await loadNotifications({
      pageNum: 1,
      pageSize: 6,
      readStatus: noticeFilter.value === 'UNREAD' ? 0 : undefined
    })
    notifications.value = result.data.records
    notificationSource.value = result.source
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通知列表加载失败')
  } finally {
    notificationLoading.value = false
  }
}

async function fetchUnreadCount() {
  try {
    const result = await loadUnreadNotificationCount()
    unreadCount.value = result.data
    if (result.source === 'mock') {
      notificationSource.value = 'mock'
    }
  } catch {
    unreadCount.value = notifications.value.filter((item) => item.readStatus === 0).length
  }
}

function handleTypeChange() {
  query.pageNum = 1
  void fetchNews()
}

function handlePageChange(pageNum: number) {
  query.pageNum = pageNum
  void fetchNews()
}

function handleSizeChange(pageSize: number) {
  query.pageSize = pageSize
  query.pageNum = 1
  void fetchNews()
}

function handleNoticeFilterChange() {
  void fetchNotifications()
}

async function readNotification(item: Notification) {
  if (item.readStatus === 1) return
  actionLoadingId.value = item.notificationId
  try {
    const result = await markNotificationAsRead(item.notificationId)
    item.readStatus = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    ElMessage.success(result.source === 'mock' ? '当前为模拟已读操作' : '通知已标记为已读')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通知已读操作失败')
  } finally {
    actionLoadingId.value = null
  }
}

async function readAllNotifications() {
  notificationLoading.value = true
  try {
    const result = await markEveryNotificationRead()
    notifications.value = notifications.value.map((item) => ({ ...item, readStatus: 1 }))
    unreadCount.value = 0
    ElMessage.success(result.source === 'mock' ? '当前为模拟全部已读' : '全部通知已读')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '全部已读操作失败')
  } finally {
    notificationLoading.value = false
  }
}

function openNews(item: News) {
  void router.push(`/news/${item.newsId}`)
}

function newsTypeLabel(type?: string) {
  const labels: Record<string, string> = {
    NEWS: '新闻',
    NOTICE: '公告',
    MODEL_UPDATE: '模型更新',
    ALERT: '异常提醒',
    SYSTEM_NOTICE: '系统通知',
    INDUSTRY_NEWS: '行业资讯',
    OPERATION: '运营消息'
  }
  return labels[type ?? ''] ?? type ?? '新闻'
}

function newsTypeTag(type?: string) {
  if (type === 'ALERT') return 'danger'
  if (type === 'MODEL_UPDATE') return 'success'
  if (type === 'NOTICE' || type === 'SYSTEM_NOTICE') return 'warning'
  return 'info'
}

function sourceLabel(source: DataSource) {
  if (source === 'remote') return '真实接口'
  if (source === 'mixed') return '混合数据'
  return '模拟数据'
}

function sourceType(source: DataSource) {
  if (source === 'remote') return 'success'
  if (source === 'mixed') return 'warning'
  return 'info'
}

function publishTime(item: News) {
  return item.publishedAt || item.createdAt || '-'
}
</script>

<template>
  <section class="news-page">
    <div class="page-heading">
      <div>
        <h1>新闻通知</h1>
        <p>查看平台公告、模型更新和站内通知</p>
      </div>
      <div class="heading-actions">
        <el-tag :type="sourceType(newsSource)" effect="light">新闻：{{ sourceLabel(newsSource) }}</el-tag>
        <el-tag :type="sourceType(notificationSource)" effect="light">
          通知：{{ sourceLabel(notificationSource) }}
        </el-tag>
      </div>
    </div>

    <el-alert
      v-if="newsSource !== 'remote' || notificationSource !== 'remote'"
      title="部分数据当前使用 mock 兜底；真实接口恢复后会自动展示后端数据。"
      type="info"
      show-icon
      :closable="false"
    />

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" type="primary" @click="fetchNews">重试</el-button>
      </template>
    </el-alert>

    <div class="overview-grid">
      <div v-for="item in stats" :key="item.label" class="overview-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </div>
    </div>

    <div class="news-layout">
      <section class="panel news-list-panel">
        <div class="panel-head">
          <div>
            <h2>新闻列表</h2>
            <p>来源：GET /api/news，支持类型筛选和分页。</p>
          </div>
          <el-button :loading="loading" @click="fetchNews">刷新</el-button>
        </div>

        <div class="filters">
          <el-segmented v-model="query.type" :options="newsTypeOptions" @change="handleTypeChange" />
          <el-input
            v-model="query.keyword"
            class="keyword-input"
            clearable
            placeholder="搜索标题、摘要、正文或类型"
          />
        </div>

        <div v-loading="loading" class="news-list">
          <el-empty v-if="!loading && filteredNews.length === 0" description="暂无新闻" />
          <article v-for="item in filteredNews" :key="item.newsId" class="news-card" @click="openNews(item)">
            <div class="news-card-main">
              <div class="news-card-title">
                <el-tag :type="newsTypeTag(item.newsType)" effect="light">{{ newsTypeLabel(item.newsType) }}</el-tag>
                <h3>{{ item.title }}</h3>
              </div>
              <p>{{ item.summary || item.content }}</p>
              <div class="news-meta">
                <span>{{ publishTime(item) }}</span>
                <span>{{ item.targetRole === 'ALL' ? '全部用户可见' : `${item.targetRole} 可见` }}</span>
              </div>
            </div>
            <el-button type="primary" plain>查看详情</el-button>
          </article>
        </div>

        <div class="pagination-row">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="query.pageNum"
            :page-size="query.pageSize"
            :page-sizes="[8, 12, 20]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </section>

      <aside class="panel notice-panel">
        <div class="panel-head">
          <div>
            <h2>站内通知</h2>
            <p>来源：GET /api/notifications。</p>
          </div>
          <el-badge :value="unreadCount" :hidden="unreadCount === 0">
            <el-button size="small" @click="readAllNotifications">全部已读</el-button>
          </el-badge>
        </div>

        <el-segmented
          v-model="noticeFilter"
          class="notice-filter"
          :options="noticeOptions"
          @change="handleNoticeFilterChange"
        />

        <div v-loading="notificationLoading" class="notice-list">
          <el-empty v-if="!notificationLoading && notifications.length === 0" description="暂无通知" />
          <div v-for="item in notifications" :key="item.notificationId" class="notice-item">
            <div class="notice-title">
              <span :class="{ unread: item.readStatus === 0 }"></span>
              <strong>{{ item.title }}</strong>
            </div>
            <p>{{ item.content }}</p>
            <div class="notice-meta">
              <small>{{ item.createdAt }}</small>
              <el-button
                v-if="item.readStatus === 0"
                size="small"
                text
                type="primary"
                :loading="actionLoadingId === item.notificationId"
                @click="readNotification(item)"
              >
                标为已读
              </el-button>
              <el-tag v-else size="small" effect="light">已读</el-tag>
            </div>
          </div>
        </div>

        <el-alert
          class="external-placeholder"
          title="外部天气新闻聚合暂未接入"
          description="docs 仅说明可接入第三方新闻源，当前页面先展示平台新闻和站内通知。"
          type="info"
          show-icon
          :closable="false"
        />
      </aside>
    </div>
  </section>
</template>

<style scoped>
.news-page {
  display: grid;
  gap: 18px;
}

.page-heading,
.heading-actions,
.panel-head,
.news-card,
.news-card-title,
.news-meta,
.notice-meta,
.notice-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-heading,
.panel-head,
.news-card,
.news-meta,
.notice-meta {
  justify-content: space-between;
}

.page-heading h1,
.panel-head h2,
.news-card h3 {
  margin: 0;
  color: #10274c;
}

.page-heading h1 {
  font-size: 28px;
}

.panel-head h2 {
  font-size: 18px;
}

.page-heading p,
.panel-head p,
.news-card p,
.notice-item p,
.news-meta,
.notice-meta {
  color: var(--color-muted);
  line-height: 1.6;
}

.page-heading p,
.panel-head p,
.news-card p,
.notice-item p {
  margin: 6px 0 0;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.overview-card,
.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.overview-card {
  min-height: 112px;
  padding: 18px;
}

.overview-card span {
  color: var(--color-muted);
  font-size: 13px;
}

.overview-card strong {
  display: block;
  margin: 10px 0 6px;
  color: #10274c;
  font-size: 28px;
  line-height: 1;
}

.overview-card small {
  color: var(--color-muted);
}

.news-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.panel {
  min-width: 0;
  padding: 18px;
}

.news-list-panel,
.notice-panel,
.news-list,
.notice-list {
  display: grid;
  gap: 14px;
}

.filters {
  display: grid;
  gap: 12px;
}

.keyword-input {
  max-width: 420px;
}

.news-card {
  min-height: 128px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fbfdff;
  cursor: pointer;
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.news-card:hover {
  border-color: var(--color-primary);
  transform: translateY(-1px);
}

.news-card-main {
  min-width: 0;
}

.news-card-title {
  align-items: flex-start;
}

.news-card-title h3 {
  font-size: 18px;
  line-height: 1.4;
}

.news-card p {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.news-meta {
  justify-content: flex-start;
  margin-top: 12px;
  font-size: 13px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
}

.notice-filter {
  align-self: start;
}

.notice-item {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f8fbff;
}

.notice-title {
  align-items: flex-start;
}

.notice-title span {
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 50%;
  background: #cbd5e1;
}

.notice-title span.unread {
  background: var(--color-primary);
}

.notice-title strong {
  min-width: 0;
  color: #10274c;
  line-height: 1.5;
}

.notice-item p {
  font-size: 14px;
}

.notice-meta {
  margin-top: 10px;
}

.external-placeholder {
  margin-top: 4px;
}

@media (max-width: 1180px) {
  .news-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .page-heading,
  .heading-actions,
  .panel-head,
  .news-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .keyword-input {
    max-width: none;
  }
}
</style>
