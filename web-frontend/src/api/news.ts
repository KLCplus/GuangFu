import request from './request'
import type { DateTimeString, PageQuery, PageResult } from './types'

export type NewsType = 'MODEL_UPDATE' | 'SYSTEM_NOTICE' | 'INDUSTRY_NEWS' | string
export type NewsCategory = 'WEATHER_ALERT' | 'DISASTER' | 'POLICY' | 'INDUSTRY' | 'ENTERPRISE' | 'PLATFORM' | string
export type NewsTargetRole = 'ALL' | 'USER' | 'API_USER' | 'ADMIN' | string
export type NewsStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | string

export interface NewsQuery extends PageQuery {
  type?: NewsType
  /** 按标题、摘要或正文关键词检索（由服务端分页） */
  keyword?: string
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
  category?: NewsCategory
  contentType?: string
  sourceType?: string
  sourceName?: string
  sourceUrl?: string
  sourcePublishedAt?: DateTimeString
  fetchedAt?: DateTimeString
  externalContent?: boolean
  warningLevel?: string
  warningRegion?: string
  warningAgency?: string
  effectiveAt?: DateTimeString
  expiresAt?: DateTimeString
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
  category?: NewsCategory
  targetRole: NewsTargetRole
}

export interface CreateNewsResult {
  newsId: number
  title: string
}

export const getNewsList = (params?: NewsQuery) => request.get<PageResult<News>>('/news', { params })
export const getNews = (newsId: number) => request.get<News>(`/news/${newsId}`)
export const getAdminNewsList = (params?: AdminNewsQuery) => request.get<PageResult<News>>('/admin/news', { params })
export const createNews = (data: NewsPayload) => request.post<CreateNewsResult>('/admin/news', data)
export const updateNews = (newsId: number, data: NewsPayload) => request.put<void>(`/admin/news/${newsId}`, data)
export const publishNews = (newsId: number) => request.put<void>(`/admin/news/${newsId}/publish`)
export const offlineNews = (newsId: number) => request.put<void>(`/admin/news/${newsId}/offline`)
export const deleteNews = (newsId: number) => request.delete<void>(`/admin/news/${newsId}`)
export const uploadNewsCover = (newsId: number, file: File) => { const form = new FormData(); form.append('file', file); return request.post<{ fileId: number; url: string }>(`/admin/news/${newsId}/cover`, form) }
export const uploadNewsImage = (newsId: number, file: File) => { const form = new FormData(); form.append('file', file); return request.post<{ fileId: number; url: string }>(`/admin/news/${newsId}/images`, form) }
