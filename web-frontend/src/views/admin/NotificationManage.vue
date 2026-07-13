<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type Notification
} from '../../api/notification'

type ReadFilter = '' | 0 | 1

const filters = reactive({ readStatus: '' as ReadFilter })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const rows = ref<Notification[]>([])
const unreadCount = ref(0)
const loading = ref(false)
const markingAll = ref(false)
const readingIds = ref<number[]>([])
const detailVisible = ref(false)
const activeNotification = ref<Notification>()

const hasUnread = computed(() => unreadCount.value > 0)

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

async function fetchUnreadCount() {
  try {
    const result = await getUnreadCount()
    unreadCount.value = Number(result.count ?? result.unreadCount ?? 0)
  } catch (error) {
    ElMessage.error(errorMessage(error, '未读数量加载失败'))
  }
}

async function fetchList() {
  loading.value = true
  try {
    const result = await getNotifications({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      ...(filters.readStatus === '' ? {} : { readStatus: filters.readStatus })
    })
    rows.value = result.records ?? []
    page.total = Number(result.total ?? 0)
  } catch (error) {
    rows.value = []
    page.total = 0
    ElMessage.error(errorMessage(error, '通知列表加载失败'))
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await Promise.all([fetchList(), fetchUnreadCount()])
}

async function changeFilter(status: ReadFilter) {
  filters.readStatus = status
  page.pageNum = 1
  await fetchList()
}

async function changePage() {
  await fetchList()
}

async function markAsRead(row: Notification, showSuccess = true) {
  if (row.readStatus === 1 || readingIds.value.includes(row.notificationId)) return

  readingIds.value = [...readingIds.value, row.notificationId]
  try {
    await markNotificationRead(row.notificationId)
    row.readStatus = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    if (activeNotification.value?.notificationId === row.notificationId) {
      activeNotification.value.readStatus = 1
    }
    if (showSuccess) ElMessage.success('已标为已读')
    if (filters.readStatus === 0) await fetchList()
  } catch (error) {
    ElMessage.error(errorMessage(error, '标记已读失败'))
  } finally {
    readingIds.value = readingIds.value.filter((id) => id !== row.notificationId)
  }
}

async function openDetail(row: Notification) {
  activeNotification.value = row
  detailVisible.value = true
  await markAsRead(row, false)
}

async function markAllAsRead() {
  if (!hasUnread.value) return
  try {
    await ElMessageBox.confirm(`确定将全部 ${unreadCount.value} 条未读通知标为已读吗？`, '全部标为已读', {
      confirmButtonText: '全部标为已读',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }

  markingAll.value = true
  try {
    await markAllNotificationsRead()
    ElMessage.success('全部通知已标为已读')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error, '全部标记已读失败'))
  } finally {
    markingAll.value = false
  }
}

function formatTime(value?: string) {
  if (!value) return '-'
  const parsed = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(parsed.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(parsed)
}

function notificationRowClassName({ row }: { row: Notification }) {
  return row.readStatus === 0 ? 'unread-row' : ''
}

onMounted(refresh)
</script>

<template>
  <div class="page-shell notification-page">
    <div class="page-title">
      <div>
        <h2>站内通知</h2>
        <p>查看系统发送给当前管理员的通知，并管理阅读状态。</p>
      </div>
      <div class="unread-summary" :class="{ empty: !hasUnread }" aria-live="polite">
        <span class="unread-dot" aria-hidden="true" />
        <span>未读</span>
        <strong>{{ unreadCount }}</strong>
      </div>
    </div>

    <div class="toolbar">
      <div class="read-tabs" aria-label="阅读状态筛选">
        <button
          v-for="option in [
            { label: '全部通知', value: '' },
            { label: '未读', value: 0 },
            { label: '已读', value: 1 }
          ]"
          :key="String(option.value)"
          type="button"
          class="read-tab"
          :class="{ active: filters.readStatus === option.value }"
          @click="changeFilter(option.value as ReadFilter)"
        >
          {{ option.label }}
        </button>
      </div>
      <div class="toolbar-right">
        <el-button :loading="loading" @click="refresh">刷新</el-button>
        <el-button type="primary" plain :disabled="!hasUnread" :loading="markingAll" @click="markAllAsRead">
          全部标为已读
        </el-button>
      </div>
    </div>

    <div class="page-section notification-table-wrap" style="padding:0">
      <el-table
        v-loading="loading"
        :data="rows"
        empty-text="当前筛选下暂无通知"
        max-height="calc(100vh - 276px)"
        row-key="notificationId"
        style="width:100%"
        :row-class-name="notificationRowClassName"
      >
        <el-table-column label="状态" width="88" align="center">
          <template #default="{ row }">
            <span v-if="row.readStatus === 0" class="status-unread"><i />未读</span>
            <span v-else class="status-read">已读</span>
          </template>
        </el-table-column>
        <el-table-column label="通知内容" min-width="360">
          <template #default="{ row }">
            <button type="button" class="notification-copy" @click="openDetail(row)">
              <span class="notification-title">{{ row.title }}</span>
              <span class="notification-preview">{{ row.content }}</span>
            </button>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看</el-button>
            <el-button
              v-if="row.readStatus === 0"
              type="primary"
              link
              :loading="readingIds.includes(row.notificationId)"
              @click="markAsRead(row)"
            >
              标为已读
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="page.total"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="changePage"
        @size-change="page.pageNum = 1; changePage()"
      />
    </div>

    <el-dialog
      v-model="detailVisible"
      title="通知详情"
      width="min(560px, calc(100vw - 32px))"
      destroy-on-close
    >
      <article v-if="activeNotification" class="notification-detail">
        <div class="detail-meta">
          <el-tag :type="activeNotification.readStatus === 0 ? 'danger' : 'info'" effect="plain">
            {{ activeNotification.readStatus === 0 ? '未读' : '已读' }}
          </el-tag>
          <time>{{ formatTime(activeNotification.createdAt) }}</time>
        </div>
        <h3>{{ activeNotification.title }}</h3>
        <p>{{ activeNotification.content }}</p>
      </article>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">我知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notification-page {
  --notification-accent: #1677ff;
}

.unread-summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 6px 12px;
  border: 1px solid #cfe1ff;
  border-radius: 8px;
  background: #f4f8ff;
  color: #53647b;
  font-size: 13px;
}

.unread-summary strong {
  color: var(--notification-accent);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 18px;
}

.unread-summary.empty {
  border-color: var(--admin-line);
  background: #f8fafc;
}

.unread-summary.empty strong {
  color: var(--admin-muted);
}

.unread-dot,
.status-unread i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--notification-accent);
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.read-tabs {
  display: inline-flex;
  padding: 3px;
  border: 1px solid var(--admin-line);
  border-radius: 8px;
  background: #eaf0f7;
}

.read-tab {
  min-width: 76px;
  height: 32px;
  padding: 0 13px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--admin-muted);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
}

.read-tab:hover {
  color: var(--admin-ink);
}

.read-tab.active {
  background: #ffffff;
  color: var(--notification-accent);
  box-shadow: 0 1px 4px rgba(16, 39, 76, 0.12);
}

.read-tab:focus-visible,
.notification-copy:focus-visible {
  outline: 2px solid var(--notification-accent);
  outline-offset: 2px;
}

.notification-copy {
  display: grid;
  width: 100%;
  gap: 4px;
  padding: 2px 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.notification-title {
  overflow: hidden;
  color: var(--admin-ink);
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-preview {
  overflow: hidden;
  color: var(--admin-muted);
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-unread,
.status-read {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 700;
}

.status-unread {
  color: var(--notification-accent);
}

.status-read {
  color: #8a98aa;
}

:deep(.unread-row td.el-table__cell) {
  background: #f7faff;
}

:deep(.unread-row:hover td.el-table__cell) {
  background: #eef5ff !important;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 2px;
}

.notification-detail {
  padding: 2px 4px 8px;
}

.detail-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--admin-muted);
  font-size: 13px;
}

.notification-detail h3 {
  margin: 20px 0 10px;
  color: var(--admin-ink);
  font-size: 20px;
  line-height: 1.45;
}

.notification-detail p {
  margin: 0;
  color: #42536a;
  font-size: 14px;
  line-height: 1.85;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 720px) {
  .page-title {
    align-items: stretch;
    flex-direction: column;
  }

  .unread-summary {
    align-self: flex-start;
  }

  .toolbar,
  .toolbar-right {
    align-items: stretch;
    flex-direction: column;
  }

  .read-tabs {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    width: 100%;
  }

  .read-tab {
    min-width: 0;
  }

  .pagination-row {
    overflow-x: auto;
    justify-content: flex-start;
    padding-bottom: 4px;
  }
}

@media (prefers-reduced-motion: no-preference) {
  .read-tab,
  .notification-copy {
    transition: color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
  }
}
</style>
