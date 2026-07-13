import request from './request'
import type { DateTimeString, PageQuery, PageResult } from './types'

export interface NotificationQuery extends PageQuery {
  readStatus?: 0 | 1
  type?: string
}

export interface Notification {
  notificationId: number
  title: string
  content: string
  notificationType?: string
  relatedType?: string
  relatedId?: number
  readStatus: 0 | 1
  createdAt: DateTimeString
}

export interface UnreadCount {
  count?: number
  unreadCount?: number
}

export const getNotifications = (params?: NotificationQuery) =>
  request.get<PageResult<Notification>>('/notifications', { params })
export const getUnreadCount = () => request.get<UnreadCount>('/notifications/unread-count')
export const markNotificationRead = (notificationId: number) =>
  request.put<void>(`/notifications/${notificationId}/read`)
export const markAllNotificationsRead = () => request.put<void>('/notifications/read-all')
