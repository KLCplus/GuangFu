<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loadNewsDetail, loadNewsPage } from '../api/userPages'
import type { News } from '../api/news'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const relatedLoading = ref(false)
const loadError = ref('')
const news = ref<News | null>(null)
const relatedNews = ref<News[]>([])

const newsId = computed(() => Number(route.params.newsId))

const safeContent = computed(() => news.value?.content ?? '')

onMounted(() => {
  void fetchDetail()
  void fetchRelatedNews()
})

watch(
  () => route.params.newsId,
  () => {
    void fetchDetail()
    void fetchRelatedNews()
  }
)

async function fetchDetail() {
  if (!Number.isFinite(newsId.value) || newsId.value <= 0) {
    loadError.value = '新闻 ID 不合法'
    return
  }

  loading.value = true
  loadError.value = ''
  try {
    const result = await loadNewsDetail(newsId.value)
    news.value = result.data
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '新闻详情加载失败'
  } finally {
    loading.value = false
  }
}

async function fetchRelatedNews() {
  relatedLoading.value = true
  try {
    const result = await loadNewsPage({ pageNum: 1, pageSize: 4 })
    relatedNews.value = result.data.records.filter((item) => item.newsId !== newsId.value).slice(0, 3)
  } catch {
    relatedNews.value = []
  } finally {
    relatedLoading.value = false
  }
}

function openRelated(item: News) {
  void router.push(`/news/${item.newsId}`)
}

function goBack() {
  void router.push('/news')
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

function publishTime(item?: News | null) {
  return item?.publishedAt || item?.createdAt || '-'
}

</script>

<template>
  <section class="news-detail-page">
    <div class="page-heading">
      <el-button @click="goBack">返回新闻列表</el-button>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" type="primary" @click="fetchDetail">重试</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="detail-layout">
      <article v-if="news" class="panel article-panel">
        <header class="article-head">
          <el-tag :type="newsTypeTag(news.newsType)" effect="light">{{ newsTypeLabel(news.newsType) }}</el-tag>
          <h1>{{ news.title }}</h1>
          <p>{{ news.summary }}</p>
          <div class="article-meta">
            <span>发布时间：{{ publishTime(news) }}</span>
            <span>{{ news.targetRole === 'ALL' ? '全部用户可见' : `${news.targetRole} 可见` }}</span>
          </div>
        </header>

        <img v-if="news.coverUrl" class="cover-image" :src="news.coverUrl" alt="新闻封面" />

        <div class="article-content" v-html="safeContent" />
      </article>

      <el-empty v-else-if="!loading && !loadError" description="新闻不存在" />

      <aside class="side-stack">
        <section class="panel related-panel">
          <div class="panel-head">
            <div>
              <h2>更多新闻</h2>
              <p>来自 GET /api/news。</p>
            </div>
          </div>
          <div v-loading="relatedLoading" class="related-list">
            <el-empty v-if="!relatedLoading && relatedNews.length === 0" description="暂无更多新闻" />
            <button v-for="item in relatedNews" :key="item.newsId" class="related-item" @click="openRelated(item)">
              <span>{{ newsTypeLabel(item.newsType) }}</span>
              <strong>{{ item.title }}</strong>
              <small>{{ publishTime(item) }}</small>
            </button>
          </div>
        </section>

        <section class="panel placeholder-panel">
          <h2>通知联动</h2>
          <p>
            站内通知已在列表页接入未读数和已读操作。新闻详情页当前只展示正文，不额外伪造外部新闻源或阅读回执接口。
          </p>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.news-detail-page {
  display: grid;
  gap: 18px;
}

.page-heading,
.detail-layout,
.article-meta,
.panel-head {
  display: flex;
  gap: 14px;
}

.page-heading,
.article-meta,
.panel-head {
  align-items: center;
  justify-content: space-between;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  align-items: start;
}

.panel {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.article-head {
  display: grid;
  gap: 12px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-border);
}

.article-head h1 {
  margin: 0;
  color: #10274c;
  font-size: 30px;
  line-height: 1.35;
}

.article-head p,
.article-meta,
.panel-head p,
.placeholder-panel p,
.related-item small {
  color: var(--color-muted);
  line-height: 1.7;
}

.article-head p,
.panel-head p,
.placeholder-panel p {
  margin: 0;
}

.article-meta {
  justify-content: flex-start;
  flex-wrap: wrap;
  font-size: 13px;
}

.cover-image {
  width: 100%;
  max-height: 360px;
  margin-top: 18px;
  border-radius: 8px;
  object-fit: cover;
}

.article-content {
  padding-top: 20px;
}

.article-content p {
  margin: 0 0 16px;
  color: #172033;
  font-size: 16px;
  line-height: 1.9;
}

.side-stack {
  display: grid;
  gap: 16px;
}

.panel-head h2,
.placeholder-panel h2 {
  margin: 0;
  color: #10274c;
  font-size: 18px;
}

.related-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.related-item {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f8fbff;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.related-item:hover {
  border-color: var(--color-primary);
}

.related-item span {
  color: var(--color-primary);
  font-size: 13px;
}

.related-item strong {
  color: #10274c;
  line-height: 1.5;
}

@media (max-width: 1180px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .page-heading,
  .panel-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .article-head h1 {
    font-size: 24px;
  }
}
</style>
