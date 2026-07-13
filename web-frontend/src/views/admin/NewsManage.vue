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
  uploadNewsCover,
  uploadNewsImage,
  type AdminNewsQuery,
  type News,
  type NewsPayload,
  type NewsStatus,
  type NewsType,
  type NewsTargetRole
} from '../../api/news'

// ---- 类型 ----
interface NewsRow {
  newsId: number
  title: string
  summary: string
  content: string
  coverUrl: string
  newsType: NewsType
  category?: string
  targetRole: NewsTargetRole
  status: NewsStatus
  publishedAt: string
  createdAt: string
}

interface StatsItem {
  label: string
  value: string | number
  color: string
}

type Mode = 'create' | 'edit'

// ---- 筛选 ----
const filters = reactive({
  keyword: '',
  status: '' as '' | NewsStatus,
  type: '' as '' | NewsType
})

// ---- 分页 ----
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

// ---- 状态 ----
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const mode = ref<Mode>('create')
const editingId = ref<number>()

// ---- 表单 ----
const emptyForm = (): NewsRow => ({
  newsId: 0,
  title: '',
  summary: '',
  content: '',
  coverUrl: '',
  newsType: 'NEWS',
  category: 'PLATFORM',
  targetRole: 'ALL',
  status: 'DRAFT',
  publishedAt: '',
  createdAt: ''
})

const form = reactive<NewsRow>(emptyForm())

const rows = ref<NewsRow[]>([])

// ---- 计算属性 ----
const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchKeyword =
      !keyword ||
      [row.title, row.summary, row.content, row.newsType].some((v) => v.toLowerCase().includes(keyword))
    const matchStatus = !filters.status || row.status === filters.status
    const matchType = !filters.type || row.newsType === filters.type
    return matchKeyword && matchStatus && matchType
  })
})

const pagedRows = computed(() => {
  const start = (page.pageNum - 1) * page.pageSize
  return filteredRows.value.slice(start, start + page.pageSize)
})

const totalFiltered = computed(() => filteredRows.value.length)

const stats = computed<StatsItem[]>(() => {
  const all = rows.value
  return [
    { label: '全部', value: all.length, color: '#1d6fdc' },
    { label: '已发布', value: all.filter((r) => r.status === 'PUBLISHED').length, color: '#52c41a' },
    { label: '草稿', value: all.filter((r) => r.status === 'DRAFT').length, color: '#faad14' },
    { label: '已下线', value: all.filter((r) => r.status === 'OFFLINE').length, color: '#999' }
  ]
})

// ---- API 操作 ----
function toPayload(f: NewsRow): NewsPayload {
  return {
    title: f.title,
    summary: f.summary,
    content: f.content,
    coverUrl: f.coverUrl || undefined,
    newsType: f.newsType,
    category: f.category || 'PLATFORM',
    targetRole: f.targetRole
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: AdminNewsQuery = { pageNum: 1, pageSize: 200 }
    if (filters.status) params.status = filters.status
    if (filters.type) params.type = filters.type
    const pageResult = await getAdminNewsList(params)
    rows.value = pageResult.records as NewsRow[]
    page.total = pageResult.total
  } catch (error) {
    rows.value = []
    ElMessage.error(error instanceof Error ? error.message : '新闻列表加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  mode.value = 'create'
  editingId.value = undefined
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: NewsRow) {
  mode.value = 'edit'
  editingId.value = row.newsId
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入新闻标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请输入新闻正文')
    return
  }

    saving.value = true
    try {
      if (mode.value === 'create') {
      await createNews(toPayload(form))
      await fetchList()
      ElMessage.success('新闻创建成功')
    } else {
      await updateNews(editingId.value!, toPayload(form))
      const idx = rows.value.findIndex((r) => r.newsId === editingId.value)
      if (idx >= 0) Object.assign(rows.value[idx], form)
      ElMessage.success('新闻更新成功')
    }
    dialogVisible.value = false
  } catch {
    ElMessage.error(mode.value === 'create' ? '创建失败' : '更新失败')
  } finally {
    saving.value = false
  }
}

async function uploadCoverFile(upload: { file: File }) {
  if (!editingId.value) return ElMessage.warning('请先创建新闻草稿，再上传封面')
  try { const result = await uploadNewsCover(editingId.value, upload.file); form.coverUrl = result.url; ElMessage.success('封面已上传') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '封面上传失败') }
}
async function uploadContentFile(upload: { file: File }) {
  if (!editingId.value) return ElMessage.warning('请先创建新闻草稿，再上传正文图片')
  try { const result = await uploadNewsImage(editingId.value, upload.file); form.content += `${form.content ? '\n' : ''}<img src="${result.url}" alt="新闻图片">`; ElMessage.success('正文图片已插入') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '正文图片上传失败') }
}

async function handlePublish(row: NewsRow) {
  try {
    await publishNews(row.newsId)
    row.status = 'PUBLISHED'
    row.publishedAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
    ElMessage.success('已发布')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '发布失败')
  }
}

async function handleOffline(row: NewsRow) {
  try {
    await offlineNews(row.newsId)
    row.status = 'OFFLINE'
    ElMessage.success('已下线')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '下线失败')
  }
}

async function handleDelete(row: NewsRow) {
  try {
    await ElMessageBox.confirm(`确定要删除新闻「${row.title}」吗？此操作不可恢复。`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteNews(row.newsId)
    rows.value = rows.value.filter((r) => r.newsId !== row.newsId)
    ElMessage.success('已删除')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ---- 辅助 ----
function statusLabel(status: NewsStatus) {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }
  return map[status] || status
}

function statusTag(status: NewsStatus) {
  const map: Record<string, string> = { DRAFT: 'warning', PUBLISHED: 'success', OFFLINE: 'info' }
  return map[status] || 'info'
}

function typeLabel(type: NewsType) {
  const map: Record<string, string> = { NEWS: '平台新闻', NOTICE: '公开公告', MODEL_UPDATE: '模型更新通知', ALERT: '异常提醒', SYSTEM_NOTICE: '系统通知', INDUSTRY_NEWS: '旧行业资讯' }
  return map[type] || type
}

function roleLabel(role: NewsTargetRole) {
  const map: Record<string, string> = { ALL: '全部用户', USER: '普通用户', API_USER: 'API 用户', ADMIN: '管理员' }
  return map[role] || role
}

function timeText(row: NewsRow) {
  return row.publishedAt || row.createdAt || '-'
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.type = ''
  page.pageNum = 1
}

// ---- 生命周期 ----
onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-shell">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div v-for="s in stats" :key="s.label" class="stat-card">
        <span class="stat-label">{{ s.label }}</span>
        <span class="stat-value" :style="{ color: s.color }">{{ s.value }}</span>
      </div>
    </div>

    <!-- 筛选工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索标题、摘要或正文"
          clearable
          style="width: 240px"
          @clear="page.pageNum = 1"
          @keyup.enter="page.pageNum = 1"
        />
        <el-select v-model="filters.status" placeholder="状态筛选" clearable style="width: 130px" @change="page.pageNum = 1">
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
        <el-select v-model="filters.type" placeholder="类型筛选" clearable style="width: 130px" @change="page.pageNum = 1">
          <el-option label="模型更新" value="MODEL_UPDATE" />
          <el-option label="系统通知" value="SYSTEM_NOTICE" />
          <el-option label="行业资讯" value="INDUSTRY_NEWS" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button @click="resetFilters">重置</el-button>
        <el-button :loading="loading" @click="fetchList">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建新闻</el-button>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="page-section" style="padding:0">
      <el-table v-loading="loading" :data="pagedRows" stripe size="default" style="width:100%">
        <el-table-column prop="newsId" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ typeLabel(row.newsType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标角色" width="110">
          <template #default="{ row }">{{ roleLabel(row.targetRole) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">{{ timeText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              size="small"
              type="success"
              link
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              size="small"
              type="warning"
              link
              @click="handleOffline(row)"
            >
              下架
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-row">
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="totalFiltered"
        layout="total, sizes, prev, pager, next"
        background
      />
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="mode === 'create' ? '新建新闻' : '编辑新闻'"
      width="680px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="新闻标题" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="2"
            placeholder="简要摘要，将展示在列表卡片中"
            maxlength="256"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="新闻正文内容（支持纯文本）"
            maxlength="4096"
            show-word-limit
          />
          <el-upload class="inline-upload" :show-file-list="false" accept="image/jpeg,image/png,image/webp" :http-request="uploadContentFile">
            <el-button size="small">上传并插入正文图片</el-button>
          </el-upload>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="新闻类型">
              <el-select v-model="form.newsType" style="width:100%">
                <el-option label="平台新闻（公开）" value="NEWS" />
                <el-option label="平台公告（公开并通知）" value="NOTICE" />
                <el-option label="模型更新（仅通知）" value="MODEL_UPDATE" />
                <el-option label="异常提醒（仅通知）" value="ALERT" />
                <el-option label="系统通知（仅通知）" value="SYSTEM_NOTICE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标角色">
              <el-select v-model="form.targetRole" style="width:100%">
                <el-option label="全部用户" value="ALL" />
                <el-option label="普通用户" value="USER" />
                <el-option label="API 用户" value="API_USER" />
                <el-option label="管理员" value="ADMIN" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="封面图片">
          <el-upload :show-file-list="false" accept="image/jpeg,image/png,image/webp" :http-request="uploadCoverFile">
            <el-button :disabled="!editingId">上传 OSS 封面</el-button>
          </el-upload>
          <el-image v-if="form.coverUrl" :src="form.coverUrl" fit="cover" style="width:120px;height:64px;margin-top:8px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ mode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px;
  border: 1px solid var(--admin-line);
  border-radius: 8px;
  background: var(--admin-surface);
  box-shadow: var(--admin-shadow);
}

.stat-label {
  color: var(--admin-muted);
  font-size: 13px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 760px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar {
    flex-direction: column;
  }
}
</style>
