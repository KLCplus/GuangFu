<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loadNewsDetail, loadNewsPage } from '../api/userPages'
import type { News } from '../api/news'
import { renderNewsContent, visibleNewsSummary } from '../utils/newsContent'

const props = withDefaults(defineProps<{
  basePath?: string
}>(), {
  basePath: '/news'
})

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const relatedLoading = ref(false)
const loadError = ref('')
const news = ref<News | null>(null)
const relatedNews = ref<News[]>([])

const newsId = computed(() => Number(route.params.newsId))

const safeContent = computed(() => renderNewsContent(news.value?.content))
const effectiveSummary = computed(() => visibleNewsSummary(news.value?.summary, safeContent.value))
const sourceUrl = computed(() => safeHttpUrl(news.value?.sourceUrl))
const attachmentUrl = computed(() => safeHttpUrl(news.value?.attachmentUrl))

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
  void router.push(`${props.basePath}/${item.newsId}`)
}

function goBack() {
  void router.push(props.basePath)
}

function safeHttpUrl(value?: string) {
  if (!value) return ''
  try {
    const url = new URL(value, window.location.origin)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}

function newsTypeLabel(type?: string) {
  const labels: Record<string, string> = {
    WEATHER_ALERT: '气象预警',
    DISASTER: '灾害动态',
    POLICY: '政策标准',
    INDUSTRY: '行业动态',
    ENTERPRISE: '企业资讯',
    PLATFORM: '运维指南',
    NEWS: '资讯',
    NOTICE: '公告',
    MODEL_UPDATE: '模型更新',
    ALERT: '异常提醒'
  }
  return labels[type ?? ''] ?? type ?? '资讯'
}

function newsTypeTag(type?: string) {
  if (type === 'ALERT') return 'danger'
  if (type === 'MODEL_UPDATE') return 'success'
  if (type === 'NOTICE') return 'warning'
  return 'info'
}

function publishTime(item?: News | null) {
  return item?.publishedAt || item?.createdAt || '-'
}

</script>

<template>
  <section class="news-detail-page">
    <div class="page-heading">
      <el-button @click="goBack">返回资讯中心</el-button>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" type="primary" @click="fetchDetail">重试</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="detail-layout">
      <article v-if="news" class="panel article-panel">
        <header class="article-head">
          <el-tag :type="newsTypeTag(news.newsType)" effect="light">{{ newsTypeLabel(news.category || news.newsType) }}</el-tag>
          <h1>{{ news.title }}</h1>
          <p v-if="effectiveSummary">{{ effectiveSummary }}</p>
          <div class="article-meta">
            <span>发布时间：{{ publishTime(news) }}</span>
            <span v-if="news.sourceName">来源：{{ news.sourceName }}</span>
            <span v-if="news.warningRegion">地区：{{ news.warningRegion }}</span>
            <span v-if="news.warningAgency">发布机构：{{ news.warningAgency }}</span>
          </div>
          <el-button v-if="sourceUrl" tag="a" :href="sourceUrl" target="_blank" rel="noopener noreferrer" type="primary" plain>查看原文</el-button>
        </header>

        <img v-if="news.coverUrl" class="cover-image" :src="news.coverUrl" alt="新闻封面" />

        <div v-if="safeContent" class="article-content" v-html="safeContent" />
        <div v-else class="content-state">正文暂不可用，请通过原文入口查看。</div>

        <section v-if="attachmentUrl" class="attachment-panel">
          <div>
            <span class="attachment-label">文章附件</span>
            <strong>{{ news.attachmentName || '查看附件' }}</strong>
            <small>{{ news.attachmentType || '文件' }}</small>
          </div>
          <el-button tag="a" :href="attachmentUrl" target="_blank" rel="noopener noreferrer" type="primary" plain>查看附件</el-button>
        </section>
      </article>

      <el-empty v-else-if="!loading && !loadError" description="新闻不存在" />

      <aside class="side-stack">
        <section class="panel related-panel">
          <div class="panel-head">
            <div>
              <h2>更多资讯</h2>
              <p>来自 GET /api/news。</p>
            </div>
          </div>
          <div v-loading="relatedLoading" class="related-list">
            <el-empty v-if="!relatedLoading && relatedNews.length === 0" description="暂无更多资讯" />
            <button v-for="item in relatedNews" :key="item.newsId" class="related-item" @click="openRelated(item)">
              <span>{{ newsTypeLabel(item.category || item.newsType) }}</span>
              <strong>{{ item.title }}</strong>
              <small>{{ publishTime(item) }}</small>
            </button>
          </div>
        </section>

        <section class="panel placeholder-panel">
          <h2>内容说明</h2>
          <p>
            外部资讯仅保留安全清洗后的正文，并标明来源。站内消息与公开内容分别存储和读取。
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
  overflow-wrap: anywhere;
  color: #172033;
  font-size: 16px;
  line-height: 1.9;
}

.article-content :deep(p) {
  margin: 0 0 16px;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4) { margin: 28px 0 12px; color: #10274c; line-height: 1.45; }
.article-content :deep(h1) { font-size: 24px; }
.article-content :deep(h2) { font-size: 21px; }
.article-content :deep(h3) { font-size: 18px; }
.article-content :deep(h4) { font-size: 16px; }
.article-content :deep(ul), .article-content :deep(ol) { margin: 0 0 18px; padding-left: 28px; }
.article-content :deep(li + li) { margin-top: 7px; }
.article-content :deep(blockquote) { margin: 20px 0; padding: 12px 16px; border-left: 4px solid #7bb2df; background: #f3f8fc; color: #53657a; }
.article-content :deep(table) { width: max-content; min-width: 100%; border-collapse: collapse; }
.article-content :deep(thead) { background: #edf5fb; }
.article-content :deep(th), .article-content :deep(td) { min-width: 100px; padding: 10px 12px; border: 1px solid #d9e3ec; text-align: left; vertical-align: top; }
.article-content :deep(a) { color: #1769aa; overflow-wrap: anywhere; }
.article-content :deep(table) { display: block; max-width: 100%; overflow-x: auto; }

.content-state { margin-top: 20px; padding: 18px; border-radius: 8px; background: #f8fafc; color: #64748b; text-align: center; }
.attachment-panel { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 24px; padding: 16px; border-radius: 8px; background: #f3f8fc; }
.attachment-panel > div { display: grid; min-width: 0; gap: 4px; }
.attachment-panel strong { overflow-wrap: anywhere; color: #173a60; }
.attachment-panel small, .attachment-label { color: #718096; font-size: 12px; }

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
