<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createNews,
  deleteNews,
  getAdminNewsList,
  offlineNews,
  publishNews,
  updateNews,
  type News,
  type NewsPayload,
  type NewsStatus
} from '../../api/news'

type Mode = 'create' | 'edit'

interface AnnouncementRow extends News {
  status: NewsStatus
  summary: string
  content: string
}

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const mode = ref<Mode>('create')
const editingId = ref<number>()
const rows = ref<AnnouncementRow[]>([])
const filters = reactive({ keyword: '', status: '' as '' | NewsStatus })
const page = reactive({ pageNum: 1, pageSize: 10 })

const messageTypes = ['NOTICE', 'MODEL_UPDATE', 'ALERT', 'SYSTEM_NOTICE'] as const
const form = reactive({ title: '', summary: '', content: '', newsType: 'NOTICE' as News['newsType'] })

const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesStatus = !filters.status || row.status === filters.status
    const matchesKeyword = !keyword || [row.title, row.summary, row.content]
      .some((value) => (value || '').toLowerCase().includes(keyword))
    return matchesStatus && matchesKeyword
  })
})

const pagedRows = computed(() => {
  const start = (page.pageNum - 1) * page.pageSize
  return filteredRows.value.slice(start, start + page.pageSize)
})

const publishedCount = computed(() => rows.value.filter((row) => row.status === 'PUBLISHED').length)
const draftCount = computed(() => rows.value.filter((row) => row.status === 'DRAFT').length)
const offlineCount = computed(() => rows.value.filter((row) => row.status === 'OFFLINE').length)

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function payload(): NewsPayload {
  return {
    title: form.title.trim(),
    summary: form.summary.trim(),
    content: form.content.trim(),
    newsType: form.newsType,
    targetRole: 'ALL'
  }
}

async function fetchList() {
  loading.value = true
  try {
    const results = await Promise.all(messageTypes.map((type) =>
      getAdminNewsList({ pageNum: 1, pageSize: 100, type })
    ))
    rows.value = results.flatMap((result) => result.records).map((item) => ({
      ...item,
      summary: item.summary || '',
      content: item.content || '',
      status: item.status || 'DRAFT'
    }))
  } catch (error) {
    rows.value = []
    ElMessage.error(errorMessage(error, '消息列表加载失败'))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.title = ''
  form.summary = ''
  form.content = ''
  form.newsType = 'NOTICE'
}

function openCreate() {
  mode.value = 'create'
  editingId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AnnouncementRow) {
  mode.value = 'edit'
  editingId.value = row.newsId
  form.title = row.title
  form.summary = row.summary
  form.content = row.content
  form.newsType = row.newsType
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.title.trim()) return ElMessage.warning('请输入消息标题')
  if (!form.content.trim()) return ElMessage.warning('请输入消息正文')

  saving.value = true
  try {
    if (mode.value === 'create') {
      await createNews(payload())
      ElMessage.success('消息草稿已创建')
    } else if (editingId.value) {
      await updateNews(editingId.value, payload())
      ElMessage.success('消息已保存')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (error) {
    ElMessage.error(errorMessage(error, mode.value === 'create' ? '消息创建失败' : '消息保存失败'))
  } finally {
    saving.value = false
  }
}

async function handlePublish(row: AnnouncementRow) {
  try {
    await ElMessageBox.confirm(
      `发布后将向全平台用户展示“${row.title}”，确定发布吗？`,
      '发布全平台消息',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await publishNews(row.newsId)
    ElMessage.success('消息已发布，并发送给目标用户')
    await fetchList()
  } catch (error) {
    ElMessage.error(errorMessage(error, '消息发布失败'))
  }
}

async function handleOffline(row: AnnouncementRow) {
  try {
    await offlineNews(row.newsId)
    ElMessage.success('消息已下线')
    await fetchList()
  } catch (error) {
    ElMessage.error(errorMessage(error, '消息下线失败'))
  }
}

async function handleDelete(row: AnnouncementRow) {
  try {
    await ElMessageBox.confirm(`确定删除消息“${row.title}”吗？此操作不可恢复。`, '删除消息', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteNews(row.newsId)
    ElMessage.success('消息已删除')
    await fetchList()
  } catch (error) {
    ElMessage.error(errorMessage(error, '消息删除失败'))
  }
}

function statusLabel(status: NewsStatus) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }[status] || status
}

function statusTag(status: NewsStatus) {
  return ({ DRAFT: 'warning', PUBLISHED: 'success', OFFLINE: 'info' } as const)[status] || 'info'
}

function messageTypeLabel(type?: string) {
  return ({ NOTICE: '公告提醒', MODEL_UPDATE: '模型更新', ALERT: '异常提醒', SYSTEM_NOTICE: '系统通知' } as Record<string, string>)[type || ''] || type || '平台消息'
}

function formatTime(value?: string) {
  if (!value) return '-'
  const parsed = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(parsed.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(parsed)
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  page.pageNum = 1
}

onMounted(fetchList)
</script>

<template>
  <div class="page-shell announcement-page">
    <div class="page-title">
      <div>
        <h2>消息管理</h2>
        <p>维护平台公告、模型更新、异常提醒和系统通知。发布后会生成对应的用户消息。</p>
      </div>
      <el-tag type="primary" effect="plain">全部用户可见</el-tag>
    </div>

    <div class="stat-row" aria-label="消息概况">
      <div class="stat-card"><span class="stat-label">全部</span><strong class="stat-value primary">{{ rows.length }}</strong></div>
      <div class="stat-card"><span class="stat-label">已发布</span><strong class="stat-value success">{{ publishedCount }}</strong></div>
      <div class="stat-card"><span class="stat-label">草稿</span><strong class="stat-value warning">{{ draftCount }}</strong></div>
      <div class="stat-card"><span class="stat-label">已下线</span><strong class="stat-value muted">{{ offlineCount }}</strong></div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索消息标题或正文"
          style="width: 260px"
          @input="page.pageNum = 1"
        />
        <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 130px" @change="page.pageNum = 1">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button @click="resetFilters">重置</el-button>
        <el-button :loading="loading" @click="fetchList">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建消息</el-button>
      </div>
    </div>

    <div class="page-section table-wrap" style="padding: 0">
      <el-table v-loading="loading" :data="pagedRows" empty-text="暂无平台消息，点击右上角新建第一条消息" row-key="newsId">
        <el-table-column label="消息内容" min-width="340">
          <template #default="{ row }">
            <div class="announcement-copy">
              <strong>{{ row.title }}</strong>
              <span>{{ row.summary || row.content }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ messageTypeLabel(row.newsType) }}</template>
        </el-table-column>
        <el-table-column label="范围" width="120">
          <template #default><span class="scope-chip">全部用户</span></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">{{ formatTime(row.publishedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'PUBLISHED'" type="success" link @click="handlePublish(row)">发布</el-button>
            <el-button v-else type="warning" link @click="handleOffline(row)">下线</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="filteredRows.length"
        layout="total, sizes, prev, pager, next"
        background
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="mode === 'create' ? '新建平台消息' : '编辑平台消息'"
      width="min(680px, calc(100vw - 32px))"
      :close-on-click-modal="false"
    >
      <div class="audience-note">发布范围：全部平台用户</div>
      <el-form :model="form" label-position="top">
        <el-form-item label="消息类型" required>
          <el-select v-model="form.newsType" style="width: 100%">
            <el-option label="公告提醒" value="NOTICE" />
            <el-option label="模型更新" value="MODEL_UPDATE" />
            <el-option label="异常提醒" value="ALERT" />
            <el-option label="系统通知" value="SYSTEM_NOTICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="消息标题" required>
          <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="例如：平台将于周日凌晨进行维护" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="256" show-word-limit placeholder="用一句话说明消息要点" />
        </el-form-item>
        <el-form-item label="消息正文" required>
          <el-input v-model="form.content" type="textarea" :rows="9" maxlength="4096" show-word-limit placeholder="说明时间、影响范围，以及用户需要采取的操作" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.stat-card { display: flex; flex-direction: column; gap: 8px; padding: 18px; border: 1px solid var(--admin-line); border-radius: 8px; background: var(--admin-surface); box-shadow: var(--admin-shadow); }
.stat-label { color: var(--admin-muted); font-size: 13px; }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1; }
.stat-value.primary { color: #1d6fdc; }
.stat-value.success { color: #52c41a; }
.stat-value.warning { color: #faad14; }
.stat-value.muted { color: #999; }
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 10px; }
.announcement-copy { display: grid; gap: 4px; min-width: 0; padding: 5px 0; }
.announcement-copy strong { overflow: hidden; color: var(--admin-ink); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.announcement-copy span { overflow: hidden; color: var(--admin-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.scope-chip { color: var(--color-primary); font-size: 12px; font-weight: 700; }
.pagination-row { display: flex; justify-content: flex-end; }
.audience-note { margin: -4px 0 18px; padding: 10px 12px; border: 1px solid #d8e3ef; border-radius: 7px; background: #f6f9fc; color: var(--admin-muted); font-size: 13px; }
@media (max-width: 680px) {
  .stat-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .toolbar { align-items: stretch; flex-direction: column; }
  .toolbar-left, .toolbar-right { align-items: stretch; flex-direction: column; }
  .toolbar-left :deep(.el-input), .toolbar-left :deep(.el-select) { width: 100% !important; }
  .pagination-row { justify-content: flex-start; overflow-x: auto; padding-bottom: 4px; }
}
</style>
