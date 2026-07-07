import request from './request'
import type { DateTimeString, PageQuery, PageResult } from './types'

export type NewsType = 'MODEL_UPDATE' | 'SYSTEM_NOTICE' | 'INDUSTRY_NEWS' | string
export type NewsTargetRole = 'ALL' | 'USER' | 'API_USER' | 'ADMIN' | string
export type NewsStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | string

export interface NewsQuery extends PageQuery {
  type?: NewsType
}

export interface AdminNewsQuery extends PageQuery {
  status?: NewsStatus
  type?: NewsType
}

export interface News {
  newsId: number
  title: string
  summary: string
  content: string
  coverUrl?: string
  newsType: NewsType
  targetRole: NewsTargetRole
  status?: NewsStatus
  publishedAt?: DateTimeString
  createdAt?: DateTimeString
}

export interface NewsPayload {
  title: string
  summary: string
  content: string
  coverUrl?: string
  newsType: NewsType
  targetRole: NewsTargetRole
}

export const getNewsList = (params?: NewsQuery) => request.get<PageResult<News>>('/news', { params })
export const getNews = (newsId: number) => request.get<News>(`/news/${newsId}`)
export const getAdminNewsList = (params?: AdminNewsQuery) => request.get<PageResult<News>>('/admin/news', { params })
export const createNews = (data: NewsPayload) => request.post<News>('/admin/news', data)
export const updateNews = (newsId: number, data: NewsPayload) => request.put<News>(`/admin/news/${newsId}`, data)
export const publishNews = (newsId: number) => request.put<void>(`/admin/news/${newsId}/publish`)
export const offlineNews = (newsId: number) => request.put<void>(`/admin/news/${newsId}/offline`)
export const deleteNews = (newsId: number) => request.delete<void>(`/admin/news/${newsId}`)
