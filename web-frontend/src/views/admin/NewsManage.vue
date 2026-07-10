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
const remoteReady = ref(false)
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
  newsType: 'INDUSTRY_NEWS',
  targetRole: 'ALL',
  status: 'DRAFT',
  publishedAt: '',
  createdAt: ''
})

const form = reactive<NewsRow>(emptyForm())

// ---- Mock 兜底数据 ----
const mockRows: NewsRow[] = [
  {
    newsId: 1,
    title: '平台 v2.3 模型更新通知',
    summary: '新增 Transformer 短时预测、SimVP 云图预测模型，优化时序预测精度',
    content: '本次更新新增了 Transformer 短时预测模型和 SimVP 云图预测模型。时序预测在 15min 粒度下 MAPE 降低约 12%，推理耗时优化 30%。新模型已在广场上架，欢迎试用。',
    coverUrl: '',
    newsType: 'MODEL_UPDATE',
    targetRole: 'ALL',
    status: 'PUBLISHED',
    publishedAt: '2026-07-09 14:20:00',
    createdAt: '2026-07-09 10:00:00'
  },
  {
    newsId: 2,
    title: '系统维护公告',
    summary: '7 月 15 日凌晨 2:00-4:00 例行维护',
    content: '平台将于 2026 年 7 月 15 日凌晨 2:00 至 4:00 进行例行维护。维护期间预测 API 可能短暂不可用，建议错开该时段调用。如有问题请联系管理员。',
    coverUrl: '',
    newsType: 'SYSTEM_NOTICE',
    targetRole: 'ALL',
    status: 'PUBLISHED',
    publishedAt: '2026-07-08 09:00:00',
    createdAt: '2026-07-08 08:30:00'
  },
  {
    newsId: 3,
    title: '光伏行业政策解读：分布式光伏补贴新规',
    summary: '国家能源局发布最新分布式光伏补贴调整方案',
    content: '国家能源局于近日发布了分布式光伏发电项目补贴调整方案。新规对户用和工商业分布式项目分别制定了差异化补贴标准，自 2026 年 8 月 1 日起执行。平台已同步更新电站收益测算模型。',
    coverUrl: '',
    newsType: 'INDUSTRY_NEWS',
    targetRole: 'ALL',
    status: 'PUBLISHED',
    publishedAt: '2026-07-06 16:00:00',
    createdAt: '2026-07-06 10:30:00'
  },
  {
    newsId: 4,
    title: 'API 额度调整预告',
    summary: '下月起 API 免费额度将调整，请关注账户余额',
    content: '为保障服务质量，自 2026 年 8 月 1 日起，免费 API 调用额度将从每月 1000 次调整为 500 次。付费用户不受影响。请提前规划 API 使用，如有需要可在广场购买更高额度套餐。',
    coverUrl: '',
    newsType: 'SYSTEM_NOTICE',
    targetRole: 'USER',
    status: 'DRAFT',
    publishedAt: '',
    createdAt: '2026-07-07 15:00:00'
  },
  {
    newsId: 5,
    title: '已废弃：旧版 API 迁移说明',
    summary: 'v1 预测接口已停用，请迁移至 v2',
    content: 'v1 版本预测接口 /openapi/v1/predict 已于 2026 年 6 月 30 日停止服务。所有用户请迁移至 v2 接口，文档详见 API 页面。',
    coverUrl: '',
    newsType: 'SYSTEM_NOTICE',
    targetRole: 'API_USER',
    status: 'OFFLINE',
    publishedAt: '2026-06-15 10:00:00',
    createdAt: '2026-06-10 14:00:00'
  }
]

const rows = ref<NewsRow[]>([...mockRows])

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
    targetRole: f.targetRole
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: AdminNewsQuery = { pageNum: 1, pageSize: 200 }
    if (filters.status) params.status = filters.status
    if (filters.type) params.type = filters.type
    const res = await getAdminNewsList(params)
    rows.value = res.data.records as NewsRow[]
    page.total = res.data.total
    remoteReady.value = true
  } catch {
    if (!remoteReady.value) {
      ElMessage.info('新闻接口暂不可用，当前展示模拟数据')
    }
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
      const res = await createNews(toPayload(form))
      rows.value.unshift({ ...res.data, status: 'DRAFT', publishedAt: '', createdAt: new Date().toISOString() } as NewsRow)
      ElMessage.success('新闻创建成功')
    } else {
      await updateNews(editingId.value!, toPayload(form))
      const idx = rows.value.findIndex((r) => r.newsId === editingId.value)
      if (idx >= 0) Object.assign(rows.value[idx], form)
      ElMessage.success('新闻更新成功')
    }
    dialogVisible.value = false
    remoteReady.value = true
  } catch {
    ElMessage.error(mode.value === 'create' ? '创建失败' : '更新失败')
  } finally {
    saving.value = false
  }
}

async function handlePublish(row: NewsRow) {
  try {
    await publishNews(row.newsId)
    row.status = 'PUBLISHED'
    row.publishedAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
    ElMessage.success('已发布')
    remoteReady.value = true
  } catch {
    // 模拟操作
    row.status = 'PUBLISHED'
    row.publishedAt = new Date().toISOString().replace('T', ' ').slice(0, 19)
    ElMessage.success('已发布（模拟）')
  }
}

async function handleOffline(row: NewsRow) {
  try {
    await offlineNews(row.newsId)
    row.status = 'OFFLINE'
    ElMessage.success('已下线')
    remoteReady.value = true
  } catch {
    row.status = 'OFFLINE'
    ElMessage.success('已下线（模拟）')
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
    remoteReady.value = true
  } catch {
    rows.value = rows.value.filter((r) => r.newsId !== row.newsId)
    ElMessage.success('已删除（模拟）')
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
  const map: Record<string, string> = { MODEL_UPDATE: '模型更新', SYSTEM_NOTICE: '系统通知', INDUSTRY_NEWS: '行业资讯' }
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
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="新闻类型">
              <el-select v-model="form.newsType" style="width:100%">
                <el-option label="模型更新" value="MODEL_UPDATE" />
                <el-option label="系统通知" value="SYSTEM_NOTICE" />
                <el-option label="行业资讯" value="INDUSTRY_NEWS" />
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
        <el-form-item label="封面图片 URL">
          <el-input v-model="form.coverUrl" placeholder="可选，封面图片链接" />
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
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}

.stat-label {
  color: var(--color-muted);
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
